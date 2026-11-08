package com.codereview.agent.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Every error leaves the API as an RFC 9457 ProblemDetail with a stable machine-readable "code". */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ReviewAnalysisException.class)
    public ResponseEntity<ProblemDetail> handleReviewFailure(ReviewAnalysisException ex) {
        var reason = ex.getReason();
        if (reason.httpStatus() >= 500) {
            log.error("Review failed: {}", reason, ex);
        }
        else {
            log.warn("Review rejected: {} - {}", reason, ex.getMessage());
        }
        return respond(reason.httpStatus(), reason.name(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
        ResponseEntity<ProblemDetail> response =
                respond(400, "VALIDATION_FAILED", "Request validation failed.");
        response.getBody().setProperty("errors", errors);
        return response;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException ex) {
        return respond(400, "MALFORMED_REQUEST", "Request body is missing or is not valid JSON.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        // Spring MVC's own exceptions (404, 405, 415...) implement ErrorResponse: keep their status.
        if (ex instanceof ErrorResponse errorResponse) {
            return ResponseEntity.status(errorResponse.getStatusCode()).body(errorResponse.getBody());
        }
        log.error("Unexpected error", ex);
        return respond(500, "INTERNAL_ERROR", "An unexpected error occurred.");
    }

    private static ResponseEntity<ProblemDetail> respond(int status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
        problem.setProperty("code", code);
        return ResponseEntity.status(status).body(problem);
    }
}