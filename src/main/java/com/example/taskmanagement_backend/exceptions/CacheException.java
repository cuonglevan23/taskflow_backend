package com.example.taskmanagement_backend.exceptions;

/**
 * Professional Cache Exception
 * 
 * Custom exception for cache-related operations
 * Provides clear error messaging and proper exception chaining
 * 
 * @author Task Management Team
 * @version 1.0
 */
public class CacheException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new cache exception with the specified detail message.
     *
     * @param message the detail message
     */
    public CacheException(String message) {
        super(message);
    }

    /**
     * Constructs a new cache exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the cause
     */
    public CacheException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new cache exception with the specified cause.
     *
     * @param cause the cause
     */
    public CacheException(Throwable cause) {
        super(cause);
    }
}