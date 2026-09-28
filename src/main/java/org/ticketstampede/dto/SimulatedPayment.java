package org.ticketstampede.dto;

import org.ticketstampede.entity.PaymentStatus;

import java.util.UUID;

public record SimulatedPayment
        (UUID paymentId,
         UUID reservationId,
         String userId,
         PaymentStatus paymentStatus)
{}
