package org.ticketstampede.service;

import org.jobrunr.scheduling.JobScheduler;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.stereotype.Service;
import org.ticketstampede.dto.BuyTicketResponse;
import org.ticketstampede.dto.SimulatedPayment;
import org.ticketstampede.entity.*;
import org.ticketstampede.exception.*;
import org.ticketstampede.repository.*;
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
    private final ReservationRepository reservationRepository;
    private final ScheduleTaskRepository scheduleTaskRepository;
    private final ReservationService reservationService;
    private static final int MAX_CONTENTION_RETRIES = 3;
    private final JobScheduler jobScheduler;


    public PurchaseService(TicketRepository ticketRepository,
                           PurchaseRequestRepository purchaseRequestRepository,
                           SaleVersionRepository saleVersionRepository,
                           ScheduleTaskRepository scheduleTaskRepository,
                           PaymentService paymentService,
                           ReservationRepository reservationRepository,
                           ReservationService reservationService,
                           JobScheduler jobScheduler)
    {
        this.ticketRepository = ticketRepository;
        this.purchaseRequestRepository = purchaseRequestRepository;
        this.saleVersionRepository = saleVersionRepository;
        this.reservationRepository = reservationRepository;
        this.scheduleTaskRepository = scheduleTaskRepository;
        this.paymentService = paymentService;
        this.reservationService = reservationService;
        this.jobScheduler = jobScheduler;
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
                Reservation reservation = reservationRepository
                        .findByPurchaseRequestId(purchaseRequest.get().getId())
                        .orElse(null);
                return convertToBuyTicketResponse(purchaseRequest.get(),reservation);
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
            Reservation reservation = reservationRepository
                    .findByPurchaseRequestId(existingPurchaseRequest.getId())
                    .orElse(null);
            return convertToBuyTicketResponse(existingPurchaseRequest,reservation);
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
                return convertToBuyTicketResponse(newPurchaseRequest,null);
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

        //6. Create a reservation
        ticket.markAsReserved();
        newPurchaseRequest.markAsReserved(ticket);
        Reservation reservation = new Reservation(ticket,newPurchaseRequest,userId);
        reservationRepository.save(reservation);
        ScheduledTask scheduledTask = new ScheduledTask(
                ScheduledTaskType.RESERVATION_EXPIRY,
                reservation.getId(),
                reservation.getExpiresAt());
        scheduleTaskRepository.save(scheduledTask);
        jobScheduler.schedule(scheduledTask.getExecuteAt(),
                ()->reservationService.expireReservation(
                        scheduledTask.getId()
                )
        );
        return convertToBuyTicketResponse(newPurchaseRequest,reservation);
    }

    public BuyTicketResponse confirmPurchase(UUID paymentId)
    {
        SimulatedPayment payment = paymentService.verifyPayment(paymentId);
        return reservationService.confirmReservation(payment);
    }

    private BuyTicketResponse convertToBuyTicketResponse(PurchaseRequest purchaseRequest,Reservation reservation)
    {
        Integer ticketNumber = null;
        if(purchaseRequest.getTicket()!=null)
        {
            ticketNumber = purchaseRequest.getTicket().getTicketNumber();
        }
        return new BuyTicketResponse(
                purchaseRequest.getStatus(),
                purchaseRequest.getSaleVersion().getId(),
                purchaseRequest.getRequestId(),
                ticketNumber,
                purchaseRequest.getCompletedAt(),
                reservation != null ? reservation.getId() : null,
                reservation != null ? reservation.getReservationStatus() : null,
                reservation != null ? reservation.getExpiresAt() : null
                );
    }


}
