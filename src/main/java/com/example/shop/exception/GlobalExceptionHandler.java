package com.example.shop.exception;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.databind.exc.InvalidFormatException;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Catches exceptions thrown from ANY controller and turns them into a clean JSON error response
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 404 - something we looked up by ID doesn't exist
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), null);
    }

    // 409 - the request breaks one of our rules (e.g. email already taken)
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), null);
    }

    // 409 - the database rejected a duplicate value. This happens when two requests sign up with the
    // same email/username at the same moment: both pass our "is it taken?" check, the database's
    // UNIQUE rule stops the second one. The constraint names come from the Flyway scripts (V1, V2, V6).
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String databaseMessage = String.valueOf(ex.getMostSpecificCause().getMessage());

        String message;
        if (databaseMessage.contains("customer_email_key")) {
            message = "Email is already taken";
        } else if (databaseMessage.contains("customer_credentials_username_key")) {
            message = "Username is already taken";
        } else if (databaseMessage.contains("payment_order_id_key")) {
            message = "Order already has a payment";
        } else {
            message = "Request conflicts with existing data";
        }
        return buildResponse(HttpStatus.CONFLICT, message, null);
    }

    // 409 - two requests changed the same row at the same moment; this one lost (see @Version in the entities)
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException ex) {
        return buildResponse(HttpStatus.CONFLICT,
                "This record was changed by someone else, please reload and try again", null);
    }

    // 400 - the request is wrong in a way @Valid can't check (e.g. same product twice in one order)
    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(InvalidRequestException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), null);
    }

    // 400 - a required URL parameter is missing (e.g. GET /api/orders without ?customerId=)
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Parameter '" + ex.getParameterName() + "' is required", null);
    }

    // 400 - request body failed validation (e.g. missing email, negative price)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return buildResponse(HttpStatus.BAD_REQUEST, "Validation failed", fieldErrors);
    }

    // 400 - request body is not valid JSON, or has a wrong value (e.g. product type "TOYS")
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        // Special case: a field from a fixed list got a wrong value, e.g. "type": "TOYS".
        // Tell the caller which field is wrong and which values are allowed.
        if (ex.getCause() instanceof InvalidFormatException invalidFormat
                && invalidFormat.getTargetType() != null
                && invalidFormat.getTargetType().isEnum()) {

            List<InvalidFormatException.Reference> path = invalidFormat.getPath();
            String fieldName = path.isEmpty() ? "value" : path.get(path.size() - 1).getPropertyName();

            String allowedValues = Arrays.stream(invalidFormat.getTargetType().getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));

            String message = "Invalid value for '" + fieldName + "'. Allowed: " + allowedValues;
            return buildResponse(HttpStatus.BAD_REQUEST, message, null);
        }

        return buildResponse(HttpStatus.BAD_REQUEST, "Request body is missing or not valid JSON", null);
    }

    // 404 - the URL doesn't match any endpoint (e.g. GET /api/anything)
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoEndpoint(NoResourceFoundException ex) {
        String message = "No endpoint " + ex.getHttpMethod() + " /" + ex.getResourcePath();
        return buildResponse(HttpStatus.NOT_FOUND, message, null);
    }

    // 405 - the URL exists, but not for this HTTP method (e.g. PATCH where only PUT is allowed)
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        String message = "Method " + ex.getMethod() + " is not supported for this URL";
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, message, null);
    }

    // 400 - a URL value has the wrong type (e.g. /api/products/abc when the ID must be a number)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Parameter '" + ex.getName() + "' has an invalid value";
        return buildResponse(HttpStatus.BAD_REQUEST, message, null);
    }

    // 503 - too many requests at the same time (Resilience4j bulkhead is full)
    @ExceptionHandler(BulkheadFullException.class)
    public ResponseEntity<ErrorResponse> handleBulkheadFull(BulkheadFullException ex) {
        ResponseEntity<ErrorResponse> response =
                buildResponse(HttpStatus.SERVICE_UNAVAILABLE, "Server is busy, please try again shortly", null);

        // Retry-After tells the caller to wait 1 second before trying again
        return ResponseEntity.status(response.getStatusCode())
                .header(HttpHeaders.RETRY_AFTER, "1")
                .body(response.getBody());
    }

    // 503 - the endpoint's database work took longer than its timeout
    // (shop.timeout.read-seconds / shop.timeout.write-seconds in application.properties).
    // QueryTimeoutException: the database stopped a query that ran too long.
    // TransactionTimedOutException: the time was already used up before the next query started.
    @ExceptionHandler({QueryTimeoutException.class, TransactionTimedOutException.class})
    public ResponseEntity<ErrorResponse> handleTimeout(Exception ex) {
        log.warn("Request timed out: {}", ex.getClass().getSimpleName());
        ResponseEntity<ErrorResponse> response =
                buildResponse(HttpStatus.SERVICE_UNAVAILABLE, "The request took too long, please try again", null);

        return ResponseEntity.status(response.getStatusCode())
                .header(HttpHeaders.RETRY_AFTER, "1")
                .body(response.getBody());
    }

    // 503 - the third-party currency service (Frankfurter) failed:
    // RestClientException: it was down / too slow / answered with an error, even after retries.
    // CallNotPermittedException: the circuit breaker is open, so we didn't even try.
    @ExceptionHandler({RestClientException.class, CallNotPermittedException.class})
    public ResponseEntity<ErrorResponse> handleExternalServiceFailure(Exception ex) {
        log.warn("Currency service failed: {} - {}", ex.getClass().getSimpleName(), ex.getMessage());
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE,
                "Currency service is unavailable, please try again later", null);
    }

    // 500 - anything we didn't expect. Log the details, but don't show them to the caller.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong", null);
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String message, Map<String, String> fieldErrors) {
        ErrorResponse body = new ErrorResponse(
                OffsetDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                fieldErrors
        );
        return ResponseEntity.status(status).body(body);
    }
}
