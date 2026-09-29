package org.ticketstampede.service.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.ticketstampede.dto.SimulatedPayment;
import org.ticketstampede.entity.PaymentStatus;
import org.ticketstampede.exception.RetryablePaymentException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class SimulatedPaymentService implements PaymentService{

//    private static final double FAILURE_PROBABILITY = 0.2;
//    private static final int MAX_DELAY_MS = 20;

    private final double failureProbability;
    private final int maxDelaySeconds;

    public SimulatedPaymentService(@Value("${ticketstampede.payment.failure-probability}") double failureProbability,
                                   @Value("${ticketstampede.payment.max-delay-ms}") int maxDelaySeconds)
    {
        this.failureProbability = failureProbability;
        this.maxDelaySeconds = maxDelaySeconds;
    }


    private final ConcurrentMap<UUID, SimulatedPayment> payments =
            new ConcurrentHashMap<>();

    @Override
    public SimulatedPayment authorize(UUID reservationId, String userId)
    {
        UUID paymentId = UUID.randomUUID();
//        simulateDelay();
        boolean failed = ThreadLocalRandom.current().nextDouble() < failureProbability;
        PaymentStatus status = failed ? PaymentStatus.RETRYABLE_FAILURE : PaymentStatus.SUCCESS;
        SimulatedPayment simulatedPayment = new SimulatedPayment(paymentId,reservationId,userId,status);
        payments.put(paymentId,simulatedPayment);
        return simulatedPayment;
    }

    @Override
    public SimulatedPayment verifyPayment(UUID paymentId)
    {
        SimulatedPayment simulatedPayment = payments.get(paymentId);
        if (simulatedPayment == null) {
            throw new RuntimeException("Payment not found");
        }
        return simulatedPayment;
    }

    private void simulateDelay() {
        long delayMs = ThreadLocalRandom.current()
                .nextLong(1, maxDelaySeconds + 1);
        try
        {
            Thread.sleep(delayMs);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new RetryablePaymentException();
        }
    }

}
