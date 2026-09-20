package org.ticketstampede.service.payment;

import org.springframework.stereotype.Service;
import org.ticketstampede.entity.PaymentStatus;
import org.ticketstampede.exception.RetryablePaymentException;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class SimulatedPaymentService implements PaymentService{

    private static final double FAILURE_PROBABILITY = 0.02;
    private static final int MAX_DELAY_MS = 20;

    @Override
    public PaymentStatus authorize(UUID requestId, String userId)
    {
        simulateDelay();
        boolean failed = ThreadLocalRandom.current().nextDouble() <= FAILURE_PROBABILITY;
        if(failed)
        {
            return PaymentStatus.RETRYABLE_FAILURE;
        }
        return PaymentStatus.SUCCESS;
    }

    private void simulateDelay() {
        long delayMs = ThreadLocalRandom.current()
                .nextLong(1, MAX_DELAY_MS + 1);
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
