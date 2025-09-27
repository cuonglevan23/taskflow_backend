package com.example.taskmanagement_backend.agent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tool Calling Configuration for AI Agent
 * Configures Spring AI Tool Calling with MyTask API integration
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "ai.tool-calling.enabled", havingValue = "true", matchIfMissing = true)
public class ToolCallingConfig {

    /**
     * Tool calling configuration properties
     */
    @Bean
    public ToolCallingProperties toolCallingProperties() {
        return new ToolCallingProperties();
    }

    /**
     * Tool calling properties configuration
     */
    public static class ToolCallingProperties {
        private boolean enabled = true;
        private boolean autoDetectIntent = true;
        private double intentConfidenceThreshold = 0.6;
        private int maxToolCalls = 5;
        private int timeoutSeconds = 30;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public boolean isAutoDetectIntent() { return autoDetectIntent; }
        public void setAutoDetectIntent(boolean autoDetectIntent) { this.autoDetectIntent = autoDetectIntent; }

        public double getIntentConfidenceThreshold() { return intentConfidenceThreshold; }
        public void setIntentConfidenceThreshold(double intentConfidenceThreshold) { this.intentConfidenceThreshold = intentConfidenceThreshold; }

        public int getMaxToolCalls() { return maxToolCalls; }
        public void setMaxToolCalls(int maxToolCalls) { this.maxToolCalls = maxToolCalls; }

        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }
}
