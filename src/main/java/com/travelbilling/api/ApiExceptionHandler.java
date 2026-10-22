package com.travelbilling.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.travelbilling.api.response.ErrorResponse;
import com.travelbilling.shared.ConflictException;
import com.travelbilling.shared.NotFoundException;
import com.travelbilling.shared.ValidationException;

/** Turns our exceptions into consistent JSON error replies with the right HTTP status. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse validation(ValidationException e) {
        return new ErrorResponse("validation_error", e.getMessage(), null);
    }

    /** The body is not valid JSON, or a field has the wrong type (e.g. "occurred_at": "yesterday"). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse unreadableBody(HttpMessageNotReadableException e) {
        return new ErrorResponse("malformed_request",
                "request body is not valid JSON or a field has the wrong type (dates must be ISO-8601)", null);
    }

    /** A query parameter has the wrong type, e.g. ?limit=abc. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse wrongParameterType(MethodArgumentTypeMismatchException e) {
        return new ErrorResponse("validation_error", "parameter " + e.getName() + " has the wrong type", null);
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse notFound(NotFoundException e) {
        return new ErrorResponse("not_found", e.getMessage(), null);
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse conflict(ConflictException e) {
        return new ErrorResponse("conflict", e.getMessage(), e.existingId());
    }
}
