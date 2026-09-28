package org.ticketstampede.service;

import jakarta.transaction.Transactional;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.stereotype.Service;
import org.ticketstampede.dto.BuyTicketResponse;
import org.ticketstampede.dto.SimulatedPayment;
import org.ticketstampede.entity.*;
import org.ticketstampede.exception.RetryablePaymentException;
import org.ticketstampede.repository.BuyerQueueRepository;
import org.ticketstampede.repository.ReservationRepository;
import org.ticketstampede.repository.ScheduleTaskRepository;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class ReservationService {
    private final ReservationRepository reservationRepository;
    private final ScheduleTaskRepository scheduleTaskRepository;
    private final BuyerQueueRepository buyerQueueRepository;
    private final JobScheduler jobScheduler;

    public ReservationService(ReservationRepository reservationRepository,ScheduleTaskRepository scheduleTaskRepository,BuyerQueueRepository buyerQueueRepository,JobScheduler jobScheduler)
    {
        this.reservationRepository = reservationRepository;
        this.scheduleTaskRepository = scheduleTaskRepository;
        this.buyerQueueRepository = buyerQueueRepository;
        this.jobScheduler = jobScheduler;
    }

    @Transactional
    public void expireReservation(UUID scheduledTaskId)
    {
        ScheduledTask reservationExpiryScheduledTask = scheduleTaskRepository
                .findById(scheduledTaskId)
                .orElseThrow(()->new IllegalStateException("Schedule task not found"));
        if(reservationExpiryScheduledTask.getScheduledTaskStatus() == ScheduledTaskStatus.COMPLETED ||
                reservationExpiryScheduledTask.getScheduledTaskStatus() == ScheduledTaskStatus.CANCELLED)
        {
            //multiple reservation expiry tasks. If schedule task has been already executed and this is a duplicate call
            return;
        }
        if(reservationExpiryScheduledTask.getScheduledTaskType()!= ScheduledTaskType.RESERVATION_EXPIRY ||
                reservationExpiryScheduledTask.getScheduledTaskStatus()!= ScheduledTaskStatus.PENDING)
        {
            throw new IllegalStateException("task is not a reservation expiry task");
        }
        Reservation reservation = reservationRepository
                //lock the reservation row so that confirmation and expiration don't occur at the same time
                .findByIdForUpdate(reservationExpiryScheduledTask.getReferenceId())
                .orElseThrow(()->new IllegalStateException("Reservation not found"));

        //Additional check to ensure that the reservation has actually expired
        if(Instant.now().isBefore(reservation.getExpiresAt()))
        {
            throw new IllegalStateException("Reservation has not expired yet");
        }
        reservationExpiryScheduledTask.markAsProcessing();
        switch(reservation.getReservationStatus())
        {
            case CONFIRMED,CANCELLED,EXPIRED ->
            {
                //Reservation already reached terminal state
                reservationExpiryScheduledTask.markAsCancelled();
            }
            case ACTIVE ->
            {
                Ticket ticket = reservation.getTicket();
                PurchaseRequest purchaseRequest = reservation.getPurchaseRequest();
                if(ticket.getStatus()!=TicketStatus.RESERVED)
                {
                    throw new IllegalStateException("Ticket isn't reserved any more");
                }
                if(purchaseRequest.getStatus()!=PurchaseRequestStatus.RESERVED)
                {
                    throw new IllegalStateException("Purchase request isn't reserved any more");
                }

                //instead of returning an expired reserved ticket back , we try assigning to the next buyer in the queue
                //expire the current purchase request and reservation
                purchaseRequest.markAsReservationExpired();
                reservation.markAsExpired();
                //Obtain the front of the buyer queue that is active and corresponds to the current sale version
                Optional<BuyerQueueEntry> optionalBuyerQueueEntry = buyerQueueRepository.findFirstEligibleQueueEntry(purchaseRequest.getSaleVersion().getId());
                //if there exists no buyer in the queue, then the ticket can be made active
                if(optionalBuyerQueueEntry.isEmpty())
                {
                    ticket.markAsAvailable();

                }else
                {
                    //obtain the buyer queue
                    BuyerQueueEntry buyerQueueEntry = optionalBuyerQueueEntry.get();
                    //Obtain the purchase request for the queued buyer
                    PurchaseRequest queuedPurchaseRequest = buyerQueueEntry.getPurchaseRequest();
                    //reserve the current ticket that has expired for the queued buyer
                    queuedPurchaseRequest.markAsReserved(ticket);
                    reservationRepository.flush();
                    //create a reservation for the queued buyer and the current ticket
                    Reservation queuedReservation = new Reservation(ticket,queuedPurchaseRequest, buyerQueueEntry.getUserId());
                    reservationRepository.save(queuedReservation);
                    //create a scheduled reservation expiry task for the reservation of the queued buyer
                    ScheduledTask queuedScheduledTask = new ScheduledTask(ScheduledTaskType.RESERVATION_EXPIRY,queuedReservation.getId(),queuedReservation.getExpiresAt());
                    scheduleTaskRepository.save(queuedScheduledTask);
                    //schedule a job to expire the reservation for the queued buyer and the current ticket
                    jobScheduler.schedule(
                            queuedScheduledTask.getExecuteAt(),
                            () -> expireReservation(queuedScheduledTask.getId())
                    );
                    //mark the queued buyer as fulfilled , obtained a ticket that has just expired
                    buyerQueueEntry.markAsFulfilled();
                    //find and cancel the queue expiry task for the queued buyer
                    ScheduledTask queueExpiryTask = scheduleTaskRepository
                            .findByScheduledTaskTypeAndReferenceId(
                                    ScheduledTaskType.QUEUE_ENTRY_EXPIRY,
                                    buyerQueueEntry.getId())
                            .orElseThrow(() ->
                                    new IllegalStateException("Queue expiry task not found"));
                    queueExpiryTask.markAsCancelled();
                }
                //mark the initial reservation expiry as complete
                reservationExpiryScheduledTask.markAsCompleted();
            }
        }
    }

    @Transactional
    public BuyTicketResponse confirmReservation(SimulatedPayment payment)
    {
        //lock the reservation so that expiration and confirmation don't clash
        Reservation reservation = reservationRepository
                .findByIdForUpdate(payment.reservationId())
                .orElseThrow(()->new IllegalStateException("Reservation not found"));

        //validate that the payment and reservation match in the fields
        if(!Objects.equals(payment.userId(), reservation.getUserId()))
        {
            throw new IllegalArgumentException("User id mismatch");
        }
        ReservationStatus reservationStatus = reservation.getReservationStatus();
        //In case confirmation acquires lock before expiry after reservation expired
        if (reservationStatus == ReservationStatus.ACTIVE &&
                !Instant.now().isBefore(reservation.getExpiresAt()))
        {
            throw new IllegalStateException("Reservation expired");
        }
        switch(reservationStatus)
        {
            case CONFIRMED -> {return convertToBuyTicketResponse(reservation.getPurchaseRequest(),reservation);}
            case EXPIRED -> throw new IllegalStateException("Reservation expired");
            case CANCELLED -> throw new IllegalStateException("Reservation has been cancelled");
            case ACTIVE -> {
                if(payment.paymentStatus()==PaymentStatus.SUCCESS)
                {
                    Ticket ticket = reservation.getTicket();
                    if(ticket.getStatus()!=TicketStatus.RESERVED)
                    {
                        throw new IllegalStateException("Active reservation doesn't have a reserved ticket");
                    }
                    PurchaseRequest purchaseRequest = reservation.getPurchaseRequest();
                    if(purchaseRequest.getStatus()!=PurchaseRequestStatus.RESERVED)
                    {
                        throw new IllegalStateException("Active reservation does not have a reserved purchase request");
                    }
                    ticket.markAsSold(payment.userId());
                    purchaseRequest.markAsPurchased(ticket);
                    reservation.markAsConfirmed();
                    //mark the corresponding expiry task as cancelled as the ticket is already purchased
                    ScheduledTask expiryTask = scheduleTaskRepository
                            .findByScheduledTaskTypeAndReferenceId(ScheduledTaskType.RESERVATION_EXPIRY, reservation.getId())
                            .orElseThrow(() -> new IllegalStateException("Reservation expiry task not found"));
                    expiryTask.markAsCancelled();
                    return convertToBuyTicketResponse(purchaseRequest,reservation);
                }
                throw new RetryablePaymentException();
            }
        }
        throw new IllegalStateException("Unexpected reservation status");
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
