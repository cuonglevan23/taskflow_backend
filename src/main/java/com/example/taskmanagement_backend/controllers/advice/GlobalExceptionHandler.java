package com.example.taskmanagement_backend.controllers.advice;

import com.example.taskmanagement_backend.exceptions.DuplicateEmailException;
import com.example.taskmanagement_backend.exceptions.HttpException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.apache.catalina.connector.ClientAbortException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpException.class)
    public ResponseEntity<ErrorResponse> handleHttpException(HttpException ex) {
        log.error("HTTP Exception: {}", ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(ex.getStatus().value())
                .error(ex.getStatus().getReasonPhrase())
                .message(ex.getMessage())
                .path(null) // Could be enhanced to include request path
                .build();
                
        return ResponseEntity.status(ex.getStatus()).body(errorResponse);
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEmailException(DuplicateEmailException ex) {
        log.error("Duplicate Email Exception: {}", ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error("Conflict")
                .message(ex.getMessage())
                .build();
                
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        log.error("Validation Exception: {}", ex.getMessage());
        
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        ValidationErrorResponse errorResponse = new ValidationErrorResponse();
        errorResponse.setTimestamp(LocalDateTime.now());
        errorResponse.setStatus(HttpStatus.BAD_REQUEST.value());
        errorResponse.setError("Validation Failed");
        errorResponse.setMessage("Invalid input parameters");
        errorResponse.setValidationErrors(errors);

        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Handle client disconnection errors (Broken pipe, Connection reset, etc.)
     * These are normal occurrences when users close browser/navigate away
     */
    @ExceptionHandler({
        AsyncRequestNotUsableException.class,
        ClientAbortException.class
    })
    public void handleClientDisconnection(Exception ex) {
        // Check if it's a broken pipe or connection reset error
        String message = ex.getMessage();
        if (message != null && (
            message.contains("Broken pipe") ||
            message.contains("Connection reset") ||
            message.contains("ServletOutputStream failed to flush") ||
            ex.getCause() instanceof IOException
        )) {
            // Log at DEBUG level instead of ERROR to reduce noise
            log.debug("🔌 Client disconnected during response: {}", ex.getClass().getSimpleName());
        } else {
            // If it's not a typical client disconnection, log as warning
            log.warn("⚠️ Async request issue: {}", ex.getMessage());
        }
        // Don't return any response since client has disconnected
    }

    /**
     * Handle IOException specifically for broken pipe errors
     */
    @ExceptionHandler(IOException.class)
    public void handleIOException(IOException ex) {
        String message = ex.getMessage();
        if (message != null && (
            message.contains("Broken pipe") ||
            message.contains("Connection reset") ||
            message.contains("Connection timed out")
        )) {
            // This is a normal client disconnection, log at debug level
            log.debug("🔌 Client disconnected (IO): {}", message);
        } else {
            // This might be a real IO issue, log as warning
            log.warn("⚠️ IO Exception: {}", message, ex);
        }
        // Don't return response for IO exceptions
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        // Check if this is a client disconnection wrapped in another exception
        if (isClientDisconnectionError(ex)) {
            log.debug("🔌 Client disconnected (wrapped): {}", ex.getClass().getSimpleName());
            return null; // Don't try to send response to disconnected client
        }

        log.error("❌ Unexpected error occurred", ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("An unexpected error occurred")
                .build();
                
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Check if the exception is related to client disconnection
     */
    private boolean isClientDisconnectionError(Exception ex) {
        // Check the exception message
        String message = ex.getMessage();
        if (message != null && (
            message.contains("Broken pipe") ||
            message.contains("Connection reset") ||
            message.contains("ServletOutputStream failed to flush") ||
            message.contains("AsyncRequestNotUsableException")
        )) {
            return true;
        }

        // Check the cause chain
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof IOException || cause instanceof ClientAbortException) {
                String causeMessage = cause.getMessage();
                if (causeMessage != null && (
                    causeMessage.contains("Broken pipe") ||
                    causeMessage.contains("Connection reset")
                )) {
                    return true;
                }
            }
            cause = cause.getCause();
        }

        // Check exception type
        return ex instanceof AsyncRequestNotUsableException ||
               ex instanceof ClientAbortException;
    }

    // Base error response
    public static class ErrorResponse {
        private LocalDateTime timestamp;
        private int status;
        private String error;
        private String message;
        private String path;

        public static ErrorResponseBuilder builder() {
            return new ErrorResponseBuilder();
        }

        // Getters and setters
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        public int getStatus() { return status; }
        public void setStatus(int status) { this.status = status; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }

        public static class ErrorResponseBuilder {
            private LocalDateTime timestamp;
            private int status;
            private String error;
            private String message;
            private String path;

            public ErrorResponseBuilder timestamp(LocalDateTime timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public ErrorResponseBuilder status(int status) {
                this.status = status;
                return this;
            }

            public ErrorResponseBuilder error(String error) {
                this.error = error;
                return this;
            }

            public ErrorResponseBuilder message(String message) {
                this.message = message;
                return this;
            }

            public ErrorResponseBuilder path(String path) {
                this.path = path;
                return this;
            }

            public ErrorResponse build() {
                ErrorResponse response = new ErrorResponse();
                response.timestamp = this.timestamp;
                response.status = this.status;
                response.error = this.error;
                response.message = this.message;
                response.path = this.path;
                return response;
            }
        }
    }

    // Validation error response
    public static class ValidationErrorResponse extends ErrorResponse {
        private Map<String, String> validationErrors;

        public static ValidationErrorResponseBuilder builder() {
            return new ValidationErrorResponseBuilder();
        }

        public Map<String, String> getValidationErrors() { return validationErrors; }
        public void setValidationErrors(Map<String, String> validationErrors) { this.validationErrors = validationErrors; }

        public static class ValidationErrorResponseBuilder extends ErrorResponseBuilder {
            private Map<String, String> validationErrors;

            public ValidationErrorResponseBuilder validationErrors(Map<String, String> validationErrors) {
                this.validationErrors = validationErrors;
                return this;
            }

            @Override
            public ValidationErrorResponse build() {
                ValidationErrorResponse response = new ValidationErrorResponse();
                response.setTimestamp(super.timestamp);
                response.setStatus(super.status);
                response.setError(super.error);
                response.setMessage(super.message);
                response.setPath(super.path);
                response.validationErrors = this.validationErrors;
                return response;
            }
        }
    }
}

