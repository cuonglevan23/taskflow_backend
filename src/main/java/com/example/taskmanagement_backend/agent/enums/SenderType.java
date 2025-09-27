package com.example.taskmanagement_backend.agent.enums;

/**
 * Enum to distinguish message senders in AI Agent chat system
 */
public enum SenderType {
    USER,    // Regular user message
    AGENT,   // AI Agent response
    SUPERVISOR     // Role/Supervisor takeover message
}
