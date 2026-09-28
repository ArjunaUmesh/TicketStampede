package org.ticketstampede.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scheduled_task")
public class ScheduledTask {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false,updatable = false)
    private ScheduledTaskType scheduledTaskType;

    @Column(name = "reference_id",nullable = false,updatable = false)
    private UUID referenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status",nullable = false)
    private ScheduledTaskStatus status;

    @Column(name = "execute_at")
    private Instant executeAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ScheduledTask(){}

    public ScheduledTask(ScheduledTaskType scheduledTaskType, UUID referenceId,Instant executeAt)
    {
        this.scheduledTaskType = scheduledTaskType;
        this.referenceId = referenceId;
        this.executeAt = executeAt;
        this.status = ScheduledTaskStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public void markAsProcessing()
    {
        if(this.status!=ScheduledTaskStatus.PENDING)
        {
            throw new IllegalStateException("Task is not pending");
        }
        this.status = ScheduledTaskStatus.PROCESSING;
    }

    public void markAsCompleted()
    {
        this.status = ScheduledTaskStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void markAsFailed()
    {
        this.status = ScheduledTaskStatus.FAILED;
        this.completedAt = Instant.now();
    }

    public void markAsCancelled()
    {
        if (this.status != ScheduledTaskStatus.PENDING &&
                this.status != ScheduledTaskStatus.PROCESSING)
        {
            throw new IllegalStateException(
                    "Only a pending or processing task can be cancelled"
            );
        }
        this.status = ScheduledTaskStatus.CANCELLED;
        this.completedAt = Instant.now();
    }

    public UUID getId(){return  id;}
    public ScheduledTaskStatus getScheduledTaskStatus(){return status;}
    public ScheduledTaskType getScheduledTaskType(){return scheduledTaskType;}
    public UUID getReferenceId(){return  referenceId;}
    public Instant getCompletedAt(){return  completedAt;}
    public Instant getCreatedAt(){return createdAt;}
    public Instant getExecuteAt(){return executeAt;}
}
