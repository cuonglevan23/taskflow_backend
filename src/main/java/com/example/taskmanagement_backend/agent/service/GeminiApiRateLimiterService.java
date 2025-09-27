package com.example.taskmanagement_backend.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Centralized service to handle Gemini API rate limiting across all application services
 * This prevents different services from competing for the same rate limit quota
 */
@Slf4j
@Service
public class GeminiApiRateLimiterService {

    // Shared rate limiter
    private final Semaphore rateLimiter;
    private final ScheduledExecutorService scheduledExecutor;

    // Rate limit tracking
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong lastApiCallTime = new AtomicLong(0);
    private final Object apiCallLock = new Object();

    // API configuration
    @Value("${gemini.api.requests.per.minute:10}")
    private int requestsPerMinute;

    // Constants
    private static final int DEFAULT_MAX_PERMITS = 5;
    private static final int MIN_DELAY_BETWEEN_CALLS_MS = 1000; // 1 second
    private static final int BACKOFF_PERIOD_MS = 30_000; // 30 seconds base backoff
    private static final int MAX_BACKOFF_EXPONENT = 5; // 2^5 = 32x multiplier max

    public GeminiApiRateLimiterService() {
        this.rateLimiter = new Semaphore(DEFAULT_MAX_PERMITS);
        this.scheduledExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "gemini-rate-limiter-thread");
            t.setDaemon(true);
            return t;
        });
    }

    @PostConstruct
    public void init() {
        // Calculate replenishment interval (ensure minimum of 1 request per minute)
        int replenishIntervalMs = 60_000 / Math.max(1, requestsPerMinute);

        // Schedule token replenishment
        scheduledExecutor.scheduleAtFixedRate(() -> {
            if (rateLimiter.availablePermits() < requestsPerMinute) {
                rateLimiter.release(1);
                log.debug("Gemini API rate limit token replenished. Available permits: {}", rateLimiter.availablePermits());
            }
        }, replenishIntervalMs, replenishIntervalMs, TimeUnit.MILLISECONDS);

        log.info("Centralized Gemini API rate limiter initialized with {} requests/minute", requestsPerMinute);
    }

    @PreDestroy
    public void shutdown() {
        scheduledExecutor.shutdownNow();
    }

    /**
     * Acquires permission to call Gemini API, enforcing rate limits
     * @param timeoutSeconds How long to wait for a permit before failing
     * @return True if permission granted, false if rate limited
     */
    public boolean acquirePermit(int timeoutSeconds) {
        try {
            // First check if we should implement backoff due to consecutive failures
            synchronized (apiCallLock) {
                int failures = consecutiveFailures.get();
                if (failures > 0) {
                    long now = System.currentTimeMillis();
                    long lastFailure = lastApiCallTime.get();

                    // Calculate exponential backoff time
                    long backoffMs = BACKOFF_PERIOD_MS * (1L << Math.min(failures - 1, MAX_BACKOFF_EXPONENT));

                    // If we're still in backoff period, deny the request
                    if ((now - lastFailure) < backoffMs) {
                        log.warn("In exponential backoff period ({}ms) due to previous 429 errors. Denying API call.", backoffMs);
                        return false;
                    }
                }

                // Now enforce minimum delay between calls
                long now = System.currentTimeMillis();
                long lastCall = lastApiCallTime.get();
                long timeSinceLastCall = now - lastCall;

                if (lastCall > 0 && timeSinceLastCall < MIN_DELAY_BETWEEN_CALLS_MS) {
                    long waitTime = MIN_DELAY_BETWEEN_CALLS_MS - timeSinceLastCall;
                    log.info("Enforcing minimum delay between Gemini API calls: waiting {}ms", waitTime);
                    Thread.sleep(waitTime);
                }

                // Try to acquire a permit from the rate limiter
                if (rateLimiter.tryAcquire(timeoutSeconds, TimeUnit.SECONDS)) {
                    // Record the time of this API call
                    lastApiCallTime.set(System.currentTimeMillis());
                    return true;
                } else {
                    log.warn("Failed to acquire Gemini API permit within {} seconds", timeoutSeconds);
                    return false;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while waiting for rate limit permit", e);
            return false;
        }
    }

    /**
     * Report successful API call to reset failure tracking
     */
    public void reportSuccess() {
        consecutiveFailures.set(0);
    }

    /**
     * Report a 429 Too Many Requests error to implement exponential backoff
     */
    public void reportRateLimitExceeded() {
        int failures = consecutiveFailures.incrementAndGet();
        lastApiCallTime.set(System.currentTimeMillis());

        // Calculate backoff period
        long backoffMs = BACKOFF_PERIOD_MS * (1L << Math.min(failures - 1, MAX_BACKOFF_EXPONENT));
        log.warn("Gemini API rate limit exceeded. Implementing exponential backoff of {}ms (consecutive failures: {})",
                 backoffMs, failures);
    }

    /**
     * Release a permit (rarely needed as scheduler handles replenishment)
     */
    public void releasePermit() {
        rateLimiter.release();
    }
}
