package org.ticketstampede.dto;

import org.ticketstampede.entity.PurchaseRequestStatus;
import org.ticketstampede.entity.ReservationStatus;
import java.time.Instant;
import java.util.UUID;

public record ConfirmPurchaseResponse(
        PurchaseRequestStatus purchaseRequestStatus,
        Integer ticketNumber,
        UUID reservationId,
        ReservationStatus reservationStatus,
        Instant completedAt
)
{}
