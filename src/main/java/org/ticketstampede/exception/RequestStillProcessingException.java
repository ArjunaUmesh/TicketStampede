package org.ticketstampede.exception;

public class RequestStillProcessingException extends RuntimeException{
    public RequestStillProcessingException()
    {
        super("Request Still Processing Exception");
    }

}
