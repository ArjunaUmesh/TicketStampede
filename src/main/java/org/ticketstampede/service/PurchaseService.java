package org.ticketstampede.service;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.stereotype.Service;
import org.ticketstampede.dto.BuyTicketResponse;
import org.ticketstampede.entity.*;
import org.ticketstampede.exception.*;
import org.ticketstampede.repository.PurchaseRequestRepository;
import org.ticketstampede.repository.SaleVersionRepository;
import org.ticketstampede.repository.TicketRepository;
import org.ticketstampede.service.payment.PaymentService;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PurchaseService {
    private final TicketRepository ticketRepository;
    private final PurchaseRequestRepository purchaseRequestRepository;
    private final SaleVersionRepository saleVersionRepository;
    private final PaymentService paymentService;
    private static final int MAX_CONTENTION_RETRIES = 3;

    public PurchaseService(TicketRepository ticketRepository,
                           PurchaseRequestRepository purchaseRequestRepository,
                           SaleVersionRepository saleVersionRepository,
                           PaymentService paymentService)
    {
        this.ticketRepository = ticketRepository;
        this.purchaseRequestRepository = purchaseRequestRepository;
        this.saleVersionRepository = saleVersionRepository;
        this.paymentService = paymentService;
    }

    //READ_COMMITTED = on each new query, read the latest committed state available at the start of that query.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BuyTicketResponse buyTicket(String userId, UUID requestId)
    {
        //1. Validation
        if(userId==null || userId.isBlank() || requestId==null )
        {
            throw new IllegalArgumentException("Invalid user/request");
        }

        //2. RequestId lookup
        Optional<PurchaseRequest> purchaseRequest = purchaseRequestRepository.findByRequestId(requestId);
        if(purchaseRequest.isPresent())
        {
            if(purchaseRequest.get().getUserId().equals(userId))
            {
                return convertToBuyTicketResponse(purchaseRequest.get());
            }
            throw new RequestIdUserMismatchException();
        }

        //3. RequestId idempotency
        if(purchaseRequestRepository.tryInsertProcessing(UUID.randomUUID(), requestId, userId, Instant.now())==0)
        {
            PurchaseRequest existingPurchaseRequest = purchaseRequestRepository.findByRequestId(requestId).orElseThrow();
            if(!existingPurchaseRequest.getUserId().equals(userId))
            {
                throw new RequestIdUserMismatchException();
            }
            return convertToBuyTicketResponse(existingPurchaseRequest);
        }
        PurchaseRequest newPurchaseRequest = purchaseRequestRepository.findByRequestId(requestId).orElseThrow();

        //4. Bind active Sale Version to the purchase request
        SaleVersion activeSaleVersion = saleVersionRepository.findByActiveTrue().orElseThrow(NoActiveSaleException::new);

        newPurchaseRequest.bindToSale(activeSaleVersion);

        //5. Acquire available ticket that isn't locked
        Ticket ticket = null;
        for(int attempt = 0; attempt <= MAX_CONTENTION_RETRIES; attempt++)
        {
            Optional<Ticket> candidate = ticketRepository.findAvailableTicket(activeSaleVersion.getId());

            if(candidate.isPresent())
            {
                ticket = candidate.get();
                break;
            }
            //If no available ticket found that's not locked, query to find if any there exists any available ticket
            boolean anyAvailableTicket = ticketRepository.existsBySaleVersionIdAndStatus(activeSaleVersion.getId(), TicketStatus.AVAILABLE);
            if(!anyAvailableTicket)
            {
                newPurchaseRequest.markAsSoldOut();
                return convertToBuyTicketResponse(newPurchaseRequest);
            }
            if(attempt == MAX_CONTENTION_RETRIES)
            {
                throw new RetryableTicketError();
            }

            //retry with jitter to reduce probability that too many transactions retry at the same time
            long jitterMs = ThreadLocalRandom.current().nextLong(5, 21);
            try
            {
                Thread.sleep(jitterMs);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                throw new RetryableTicketError();
            }
        }

        //6. Try payment
        if(paymentService.authorize(requestId,userId)== PaymentStatus.RETRYABLE_FAILURE)
        {
            throw new RetryablePaymentException();
        }

        // 7. Mark ticket as sold and purchase request as purchased
        ticket.markAsSold(userId);
        newPurchaseRequest.markAsPurchased(ticket);
        return convertToBuyTicketResponse(newPurchaseRequest);
    }

    private BuyTicketResponse convertToBuyTicketResponse(PurchaseRequest purchaseRequest)
    {
        PurchaseStatus purchaseStatus =
                switch(purchaseRequest.getStatus())
                {
                    case PURCHASED -> PurchaseStatus.PURCHASED;
                    case SOLD_OUT -> PurchaseStatus.SOLD_OUT;
                    case PROCESSING -> throw new RequestStillProcessingException();
                };
        Integer ticketNumber = null;
        if(purchaseRequest.getStatus().equals(PurchaseRequestStatus.PURCHASED))
        {
            ticketNumber = purchaseRequest.getTicket().getTicketNumber();
        }
        return new BuyTicketResponse(
                purchaseStatus,
                purchaseRequest.getSaleVersion().getId(),
                purchaseRequest.getRequestId(),
                ticketNumber,
                purchaseRequest.getCompletedAt());
    }
}
