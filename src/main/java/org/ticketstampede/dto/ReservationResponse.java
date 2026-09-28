package org.ticketstampede.dto;

import org.ticketstampede.entity.ReservationStatus;
import java.time.Instant;
import java.util.UUID;

public record ReservationResponse(
    UUID reservationId,
    UUID ticketIt,
    ReservationStatus reservationStatus,
    Instant expiresAt
)
{}
