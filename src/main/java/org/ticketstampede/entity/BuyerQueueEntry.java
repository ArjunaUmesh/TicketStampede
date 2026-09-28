package org.ticketstampede.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "buyer_queue")
public class BuyerQueueEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id",nullable = false,updatable = false)
    private String userId;

    @OneToOne
    @JoinColumn(name = "purchase_request_id",nullable = false,unique = true)
    private PurchaseRequest purchaseRequest;

    @Enumerated(EnumType.STRING)
    private BuyerQueueStatus status;

    @Column(name = "created_at",nullable = false,updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at",nullable = false,updatable = false)
    private Instant expiresAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected BuyerQueueEntry(){}

    public BuyerQueueEntry(String userId, PurchaseRequest purchaseRequest,Instant expiresAt)
    {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        if (purchaseRequest == null) {
            throw new IllegalArgumentException("purchaseRequest is required");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt is required");
        }
        this.userId = userId;
        this.purchaseRequest = purchaseRequest;
        this.status = BuyerQueueStatus.ACTIVE;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public void markAsFulfilled()
    {
        if (this.status != BuyerQueueStatus.ACTIVE) {
            throw new IllegalStateException("Only an active queue entry can be fulfilled");}
        this.status = BuyerQueueStatus.FULFILLED;
        this.completedAt = Instant.now();
    }

    public void markAsCancelled()
    {
        if (this.status != BuyerQueueStatus.ACTIVE) {throw new IllegalStateException("Only an active queue entry can be cancelled");}
        this.status = BuyerQueueStatus.CANCELLED;
        this.completedAt = Instant.now();
    }

    public void markAsExpired()
    {
        if (this.status != BuyerQueueStatus.ACTIVE) {throw new IllegalStateException("Only an active queue entry can be expired");}
        this.status = BuyerQueueStatus.EXPIRED;
        this.completedAt = Instant.now();
    }

    public UUID getId(){return id;}
    public PurchaseRequest getPurchaseRequest(){return purchaseRequest;}
    public String getUserId(){return userId;}
    public BuyerQueueStatus getStatus(){return status;}
    public Instant getCreatedAt(){return createdAt;}
    public Instant getExpiresAt(){return expiresAt;}
    public Instant getCompletedAt(){return completedAt;}

}
