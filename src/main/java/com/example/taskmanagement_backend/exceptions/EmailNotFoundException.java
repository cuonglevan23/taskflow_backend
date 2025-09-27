package com.example.taskmanagement_backend.exceptions;

import lombok.Getter;

import java.util.List;

/**
 * Exception thrown when email addresses are not found in the system
 */
@Getter
public class EmailNotFoundException extends RuntimeException {

    private final List<String> invalidEmails;

    public EmailNotFoundException(String message, List<String> invalidEmails) {
        super(message);
        this.invalidEmails = invalidEmails;
    }

    public EmailNotFoundException(List<String> invalidEmails) {
        super("The following email addresses were not found: " + String.join(", ", invalidEmails));
        this.invalidEmails = invalidEmails;
    }
}
