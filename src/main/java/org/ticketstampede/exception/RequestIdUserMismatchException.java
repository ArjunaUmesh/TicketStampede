package org.ticketstampede.exception;

public class RequestIdUserMismatchException extends  RuntimeException{

    public RequestIdUserMismatchException()
    {
        super("Request Id exists with another user Id");
    }

}
