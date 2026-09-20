package org.ticketstampede.exception;

public class NoActiveSaleException extends RuntimeException{
    public NoActiveSaleException()
    {
        super("No active sale version found");
    }
}
