package org.ticketstampede.exception;

public class RetryablePaymentException extends RuntimeException{
    public RetryablePaymentException()
    {
        super("Payment authorization temporarily failed");
    }
}
