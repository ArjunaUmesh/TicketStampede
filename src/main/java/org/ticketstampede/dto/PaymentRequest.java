package org.ticketstampede.dto;

import java.util.UUID;

public record PaymentRequest(
        UUID reservationId,
        String userId
) {}

