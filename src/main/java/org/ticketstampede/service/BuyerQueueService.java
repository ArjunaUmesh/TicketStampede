package org.ticketstampede.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketstampede.entity.*;
import org.ticketstampede.repository.BuyerQueueRepository;
import org.ticketstampede.repository.ScheduleTaskRepository;

import java.time.Instant;
import java.util.UUID;

@Service
public class BuyerQueueService {

    private final BuyerQueueRepository buyerQueueRepository;
    private final ScheduleTaskRepository scheduleTaskRepository;

    public BuyerQueueService(BuyerQueueRepository buyerQueueRepository, ScheduleTaskRepository scheduleTaskRepository)
    {
        this.buyerQueueRepository = buyerQueueRepository;
        this.scheduleTaskRepository = scheduleTaskRepository;
    }

    @Transactional
    public void expireQueueEntry(UUID scheduledTaskId)
    {
        ScheduledTask scheduledTask = scheduleTaskRepository
                .findById(scheduledTaskId)
                .orElseThrow(() -> new IllegalStateException("Scheduled task not found"));
        if (scheduledTask.getScheduledTaskStatus() == ScheduledTaskStatus.COMPLETED ||
                scheduledTask.getScheduledTaskStatus() == ScheduledTaskStatus.CANCELLED)
        {
            // Duplicate execution  : nothing to do
            return;
        }
        if (scheduledTask.getScheduledTaskType() != ScheduledTaskType.QUEUE_ENTRY_EXPIRY ||
                scheduledTask.getScheduledTaskStatus() != ScheduledTaskStatus.PENDING)
        {
            throw new IllegalStateException("Task is not a queue entry expiry task");
        }
        BuyerQueueEntry buyerQueueEntry = buyerQueueRepository
                .findByIdForUpdate(scheduledTask.getReferenceId())
                .orElseThrow(() -> new IllegalStateException("Buyer queue entry not found"));

        switch(buyerQueueEntry.getStatus())
        {
            case ACTIVE ->
            {
                //Additional check to ensure that buyer queue has indeed expired
                if (Instant.now().isBefore(buyerQueueEntry.getExpiresAt()))
                {
                    throw new IllegalStateException("Buyer queue entry has not expired yet");
                }
                scheduledTask.markAsProcessing();
                PurchaseRequest purchaseRequest = buyerQueueEntry.getPurchaseRequest();
                buyerQueueEntry.markAsExpired();
                purchaseRequest.markAsQueueExpired();
                scheduledTask.markAsCompleted();
            }
            case FULFILLED,EXPIRED,CANCELLED -> scheduledTask.markAsCancelled();
        }
    }
}
