package org.ticketstampede.dto;

import org.ticketstampede.entity.PurchaseRequestStatus;
import org.ticketstampede.entity.ReservationStatus;

import java.time.Instant;
import java.util.UUID;

public record BuyTicketResponse(
        PurchaseRequestStatus status,
        UUID saleVersionId,
        UUID requestId,
        Integer ticketNumber,
        Instant completedAt,
        UUID reservationId,
        ReservationStatus reservationStatus,
        Instant reservationExpiresAt
)
{}
