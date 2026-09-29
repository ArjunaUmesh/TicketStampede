package org.ticketstampede.entity;

import jakarta.persistence.*;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservation")
public class Reservation {

    private static final Duration RESERVATION_TTL = Duration.ofSeconds(60*5);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne()
    @JoinColumn(name = "ticket_id",nullable = false)
    private Ticket ticket;

    @OneToOne
    @JoinColumn(name = "purchase_request_id",nullable = false, unique = true)
    private PurchaseRequest purchaseRequest;

    @Column(name = "user_id",nullable = false,updatable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status",nullable = false)
    private ReservationStatus reservationStatus;

    @Column(name = "expires_at", nullable = false,updatable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Reservation(){}

    public Reservation(Ticket ticket, PurchaseRequest purchaseRequest, String userId,Instant expiresAt)
    {
        this.ticket = ticket;
        this.purchaseRequest = purchaseRequest;
        this.userId = userId;
        this.reservationStatus = ReservationStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;//createdAt.plus(RESERVATION_TTL);
    }

    public void markAsExpired()
    {
        this.reservationStatus = ReservationStatus.EXPIRED;
        this.completedAt = Instant.now();
    }

    public void markAsCancelled()
    {
        this.reservationStatus = ReservationStatus.CANCELLED;
        this.completedAt = Instant.now();
    }

    public void markAsConfirmed()
    {
        this.reservationStatus = ReservationStatus.CONFIRMED;
        this.completedAt = Instant.now();
    }

    public UUID getId(){return id;}
    public Ticket getTicket(){return ticket;}
    public String getUserId(){return userId;}
    public PurchaseRequest getPurchaseRequest(){return purchaseRequest;}
    public ReservationStatus getReservationStatus(){return reservationStatus;}
    public Instant getCreatedAt(){return createdAt;}
    public Instant getExpiresAt(){return expiresAt;}
    public Instant getCompletedAt(){return completedAt;}

}
