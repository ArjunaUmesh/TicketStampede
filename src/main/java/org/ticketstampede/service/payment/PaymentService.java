package org.ticketstampede.service.payment;

import org.ticketstampede.dto.SimulatedPayment;

import java.util.UUID;

public interface PaymentService {
    SimulatedPayment authorize(UUID reservationId, String userId);
    SimulatedPayment verifyPayment(UUID paymentId);
}
