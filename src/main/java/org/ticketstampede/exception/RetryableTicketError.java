package org.ticketstampede.exception;

public class RetryableTicketError extends RuntimeException {
    public RetryableTicketError()
    {
        super("Ticket temporarily unavailable due to contention");
    }
}
