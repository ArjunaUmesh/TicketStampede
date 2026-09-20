package org.ticketstampede.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.ticketstampede.dto.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;

/**
 * Centralized exception handler for the seller API.
 * Converts application, validation, and malformed-request exceptions into
 * consistent HTTP error responses so clients receive a predictable error
 * structure across /reset, /buy, and /status.
 */

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RequestIdUserMismatchException.class)
    public ResponseEntity<ErrorResponse> requestIdUserMismatch()
    {
        ErrorResponse errorResponse = new ErrorResponse(
                409,
                "REQUEST_ID_USER_MISMATCH",
                "Request ID exists with another user ID");
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(errorResponse);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> invalidRequest(IllegalArgumentException exception)
    {
        ErrorResponse errorResponse = new ErrorResponse(
                400,
                "INVALID_REQUEST",
                exception.getMessage()
        );
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }

    @ExceptionHandler(NoActiveSaleException.class)
    public ResponseEntity<ErrorResponse> noActiveSale()
    {
        ErrorResponse errorResponse = new ErrorResponse(
                409,
                "NO_ACTIVE_SALE",
                "No active sale exists"
        );
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(errorResponse);
    }

    @ExceptionHandler(RequestStillProcessingException.class)
    public ResponseEntity<ErrorResponse> requestStillProcessing()
    {
        ErrorResponse errorResponse = new ErrorResponse(
                409,
                "REQUEST_STILL_PROCESSING",
                "Request Id is still being processed"
        );
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(errorResponse);
    }

    @ExceptionHandler(RetryablePaymentException.class)
    public ResponseEntity<ErrorResponse> retryablePayment()
    {
        ErrorResponse errorResponse = new ErrorResponse(
                503,
                "RETRYABLE_PAYMENT",
                "Payment could not be processed temporarily. Retry the request."
        );
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(errorResponse);
    }

    @ExceptionHandler(RetryableTicketError.class)
    public ResponseEntity<ErrorResponse> retryableTicket(RetryableTicketError retryableTicketError)
    {
        ErrorResponse errorResponse = new ErrorResponse(
                503,
                "TICKET_CONTENTION",
                retryableTicketError.getMessage()
        );
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validationFailed()
    {
        ErrorResponse errorResponse = new ErrorResponse(
                400,
                "INVALID_REQUEST",
                "Request validation failed"
        );
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> malformedRequest()
    {
        ErrorResponse errorResponse = new ErrorResponse(
                400,
                "INVALID_REQUEST",
                "Malformed request body"
        );
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }

}
