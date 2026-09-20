package org.ticketstampede.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "purchase_request")

public class PurchaseRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    //request id has to be globally unique
    @Column(name = "request_id",nullable = false,updatable = false,unique = true)
    private UUID requestId;

    @ManyToOne
    @JoinColumn(name = "sale_version_id")
    private SaleVersion saleVersion;

    @Column(name = "user_id",nullable = false,updatable = false)
    private String userId;

    @OneToOne
    @JoinColumn(name = "ticket_id",unique = true)
    private Ticket ticket;

    //store readable strings for enums instead of enum ordinals
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PurchaseRequestStatus status;

    @Column(name = "created_at",nullable = false,updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public PurchaseRequest()
    {}

    public PurchaseRequest(UUID requestId, String userId)
    {
        if (requestId == null)
        {
            throw new IllegalArgumentException("requestId is required");
        }
        if (userId == null || userId.isBlank())
        {
            throw new IllegalArgumentException("userId is required");
        }
        this.requestId = requestId;
        this.userId = userId;
        this.createdAt = Instant.now();
        this.status = PurchaseRequestStatus.PROCESSING;
    }

    public void bindToSale(SaleVersion saleVersion)
    {
        if (this.status != PurchaseRequestStatus.PROCESSING)
        {
            throw new IllegalStateException("Only a processing request can be bound to a sale");
        }
        if (saleVersion == null)
        {
            throw new IllegalArgumentException("saleVersion is required");
        }
        if (this.saleVersion != null)
        {
            throw new IllegalStateException("Purchase request is already bound to a sale");
        }
        this.saleVersion = saleVersion;
    }

    public void markAsPurchased(Ticket ticket)
    {
        if (this.status != PurchaseRequestStatus.PROCESSING)
        {
            throw new IllegalStateException("Only a processing request can be purchased");
        }
        if (this.saleVersion == null)
        {
            throw new IllegalStateException("Purchase request must be bound to a sale");
        }
        if (ticket == null)
        {
            throw new IllegalArgumentException("ticket is required");
        }
        //ensure ticket is sold before marking purchase request as purchased
        if (ticket.getStatus() != TicketStatus.SOLD) {
            throw new IllegalStateException("Ticket must be sold first");
        }
        //ensure userId in the ticket and purchase request match
        if (!this.userId.equals(ticket.getHolderUserId())) {
            throw new IllegalArgumentException("Ticket holder does not match request user");
        }
        //ensure sale version of the purchase request and ticket match
        if (!ticket.getSaleVersion().getId().equals(this.saleVersion.getId()))
        {
            throw new IllegalArgumentException("Ticket belongs to a different sale");
        }

        this.ticket = ticket;
        this.completedAt = Instant.now();
        this.status = PurchaseRequestStatus.PURCHASED;
    }

    public void markAsSoldOut()
    {
        if (this.status != PurchaseRequestStatus.PROCESSING)
        {
            throw new IllegalStateException("Only a processing request can become sold out");
        }

        if (this.saleVersion == null)
        {
            throw new IllegalStateException("Purchase request must be bound to a sale");
        }
        this.completedAt = Instant.now();
        this.status = PurchaseRequestStatus.SOLD_OUT;
    }

    public UUID getId(){ return id;}
    public UUID getRequestId(){return requestId;}
    public SaleVersion getSaleVersion(){ return saleVersion;}
    public String getUserId(){ return userId;}
    public Ticket getTicket(){return ticket;}
    public PurchaseRequestStatus getStatus(){return status;}
    public Instant getCreatedAt(){ return createdAt;}
    public Instant getCompletedAt(){ return completedAt;}

}
