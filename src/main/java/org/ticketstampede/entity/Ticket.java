package org.ticketstampede.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ticket_sale_version_number",
                columnNames = {"sale_version_id", "ticket_number"}
        ))

public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    //After creation, a Ticket should never move from one to another sale version
    @JoinColumn(name = "sale_version_id",nullable = false, updatable = false)
    private SaleVersion saleVersion;

    @Column(name = "ticket_number", nullable = false, updatable = false)
    private int ticketNumber;

    //store readable strings for enums instead of enum ordinals
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;

    @Column(name = "holder_user_id")
    private String holderUserId;

    @Column(name = "sold_at")
    private Instant soldAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Ticket()
    {}

    public Ticket(SaleVersion saleVersion,int ticketNumber)
    {
        if (saleVersion == null)
        {
            throw new IllegalArgumentException("saleVersion is required");
        }
        if (ticketNumber <= 0)
        {
            throw new IllegalArgumentException("ticketNumber must be positive");
        }
        this.saleVersion = saleVersion;
        this.ticketNumber = ticketNumber;
        this.createdAt = Instant.now();
        this.status = TicketStatus.AVAILABLE;
    }

    public void markAsSold(String holderUserId)
    {
        if (this.status != TicketStatus.AVAILABLE)
        {
            throw new IllegalStateException("Ticket is not available");
        }
        if (holderUserId == null || holderUserId.isBlank())
        {
            throw new IllegalArgumentException("holderUserId is required");
        }
        this.holderUserId = holderUserId;
        this.status = TicketStatus.SOLD;
        this.soldAt = Instant.now();
    }

    public UUID getId(){ return id;}
    public SaleVersion getSaleVersion(){ return saleVersion;}
    public int getTicketNumber(){ return ticketNumber;}
    public TicketStatus getStatus(){ return  status;}
    public String getHolderUserId(){ return holderUserId;}
    public Instant getCreatedAt(){ return createdAt;}
    public Instant getSoldAt(){ return soldAt;}

}
