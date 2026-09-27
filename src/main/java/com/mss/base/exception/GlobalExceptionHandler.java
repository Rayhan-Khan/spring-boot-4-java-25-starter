package com.mss.base.exception;

import com.mss.base.utils.ApiResponse;
import com.mss.base.utils.ResponseBuilder;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns exceptions into {@link ApiResponse} error bodies with a matching HTTP status.
 * <p>
 * Standard Spring MVC errors (malformed JSON, missing parameters, wrong method, unknown path, upload too large...)
 * are handled by {@link ResponseEntityExceptionHandler} and keep their standard status codes. Anything not
 * handled explicitly is a server error: it is logged with its stack trace and the client gets a generic 500.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String UNEXPECTED_ERROR = "An unexpected error occurred. Please try again later.";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        return clientError(HttpStatus.NOT_FOUND, ex.getMessage(), null);
    }

    /**
     * Also covers {@link PropertyReferenceException}: sorting by a field that does not exist.
     */
    @ExceptionHandler({CustomMessagePresentException.class, IllegalArgumentException.class, PropertyReferenceException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(RuntimeException ex) {
        return clientError(HttpStatus.BAD_REQUEST, ex.getMessage(), null);
    }

    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<ApiResponse<Void>> handleEmailNotVerified(EmailNotVerifiedException ex) {
        return clientError(HttpStatus.FORBIDDEN, ex.getMessage(), null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return clientError(HttpStatus.UNAUTHORIZED, ex.getMessage(), null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return clientError(HttpStatus.FORBIDDEN, "You do not have permission to perform this action.", null);
    }

    /**
     * A login method (or Firebase storage) was used but is switched off in .env.
     */
    @ExceptionHandler({FirebaseNotConfiguredException.class, AuthMethodDisabledException.class})
    public ResponseEntity<ApiResponse<Void>> handleDisabledFeature(RuntimeException ex) {
        log.warn("Request for a disabled feature: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ResponseBuilder.error(null, ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return clientError(HttpStatus.CONFLICT, "The request conflicts with existing data.", null);
    }

    /**
     * Validation of method parameters on classes annotated with {@code @Validated}.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        List<String> messages = ex.getConstraintViolations().stream().map(ConstraintViolation::getMessage).toList();
        return clientError(HttpStatus.BAD_REQUEST, firstOrDefault(messages), messages);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ResponseBuilder.error(null, UNEXPECTED_ERROR));
    }

    /**
     * {@code @Valid @RequestBody} failures: {@code message} is the first error, {@code errors} maps field to message.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        String message = errors.values().stream().findFirst().orElse("Validation failed");
        log.warn("Request body validation failed: {}", errors);
        return ResponseEntity.status(status).headers(headers).body(ResponseBuilder.error(errors, message));
    }

    /**
     * Constraint failures on controller method parameters ({@code @RequestParam}, {@code @PathVariable}...).
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers, HttpStatusCode status,
                                                                            WebRequest request) {
        List<String> messages = ex.getAllErrors().stream().map(MessageSourceResolvable::getDefaultMessage).toList();
        log.warn("Parameter validation failed: {}", messages);
        return ResponseEntity.status(status).headers(headers).body(ResponseBuilder.error(messages, firstOrDefault(messages)));
    }

    /**
     * Every other standard Spring MVC error: keep Spring's status code, return our envelope.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        String message;
        if (statusCode.is5xxServerError()) {
            log.error("Server error handling request", ex);
            message = UNEXPECTED_ERROR;
        } else {
            message = detailOf(ex, body);
            log.warn("{}: {}", ex.getClass().getSimpleName(), message);
        }
        return super.handleExceptionInternal(ex, ResponseBuilder.error(null, message), headers, statusCode, request);
    }

    private ResponseEntity<ApiResponse<Void>> clientError(HttpStatus status, String message, Object errors) {
        log.warn("{} {}: {}", status.value(), status.getReasonPhrase(), message);
        return ResponseEntity.status(status).body(ResponseBuilder.error(errors, message));
    }

    private static String detailOf(Exception ex, Object body) {
        if (body instanceof ProblemDetail problem && problem.getDetail() != null) {
            return problem.getDetail();
        }
        if (ex instanceof ErrorResponse errorResponse && errorResponse.getBody().getDetail() != null) {
            return errorResponse.getBody().getDetail();
        }
        return ex.getMessage();
    }

    private static String firstOrDefault(List<String> messages) {
        return messages.isEmpty() ? "Validation failed" : messages.getFirst();
    }
}
