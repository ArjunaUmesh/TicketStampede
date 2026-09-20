package org.ticketstampede.service.payment;

import org.ticketstampede.entity.PaymentStatus;

import java.util.UUID;

public interface PaymentService {
    PaymentStatus authorize(UUID requestId, String userId);
}
