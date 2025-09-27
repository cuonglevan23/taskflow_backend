package com.example.taskmanagement_backend.agent.service;

import com.example.taskmanagement_backend.agent.dto.ModerationResult;
import com.example.taskmanagement_backend.agent.exception.AgentException;
import com.example.taskmanagement_backend.agent.moderation.ToxicFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Enhanced Moderation Service - Rule-based ONLY (No AI/Gemini dependencies)
 * Kiểm tra spam, toxic, ngôn từ nhạy cảm, gắn tag chi tiết
 * Tích hợp ToxicFilter để có comprehensive protection
 */
@Slf4j
@Service
public class EnhancedModerationService {

    private final ToxicFilter toxicFilter;

    // Constructor - REMOVED WebClient dependency
    public EnhancedModerationService(ToxicFilter toxicFilter) {
        this.toxicFilter = toxicFilter;
    }

    // Business threats patterns (specific to platform)
    private static final List<String> BUSINESS_THREATS = Arrays.asList(
        "competitor", "steal", "copy", "leak", "hack", "breach", "reverse engineer"
    );

    /**
     * Comprehensive moderation với multiple layers (RULE-BASED ONLY - No Gemini calls)
     */
    public ModerationResult moderateContent(String content, String userId, String context) {
        try {
            log.debug("Moderating content for user: {} with context: {}", userId, context);

            // Layer 1: Enhanced ToxicFilter check (includes data theft, injection, spam)
            ModerationResult toxicFilterResult = toxicFilter.checkToxicity(content, context);
            if (!toxicFilterResult.isSafe()) {
                log.warn("Content blocked by ToxicFilter: {}", toxicFilterResult.getReason());
                return toxicFilterResult;
            }

            // Layer 2: Business logic moderation
            ModerationResult businessResult = performBusinessLogicModeration(content, userId);
            if (!businessResult.isSafe()) {
                return businessResult;
            }

            // Layer 3: Spam pattern check (rule-based)
            ModerationResult spamResult = checkSpamPatterns(content);
            if (!spamResult.isSafe()) {
                return spamResult;
            }

            // Layer 4: Context-aware moderation
            ModerationResult contextResult = performContextAwareModeration(content, context);

            // Combine results - all rule-based, no AI calls
            return combineResults(Arrays.asList(toxicFilterResult, businessResult, spamResult, contextResult));

        } catch (Exception e) {
            log.error("Error during content moderation", e);
            // Failsafe: allow content but flag for review
            return ModerationResult.builder()
                .safe(true)
                .reason("Moderation service error - flagged for manual review")
                .category("REVIEW_REQUIRED")
                .confidence(0.5)
                .recommendation("REVIEW")
                .flaggedTerms(new String[]{"SYSTEM_ERROR"})
                .build();
        }
    }

    /**
     * Context-aware moderation
     */
    private ModerationResult performContextAwareModeration(String content, String context) {
        List<String> concerns = new ArrayList<>();

        // Check insufficient data patterns
        if (isInsufficientData(content)) {
            concerns.add("INSUFFICIENT_DATA");
        }

        // Check context mismatches
        if (context != null && !context.isEmpty()) {
            if (isContextMismatch(content, context)) {
                concerns.add("CONTEXT_MISMATCH");
            }
        }

        // Check for potential confusion
        if (isConfusingContent(content)) {
            concerns.add("UNCLEAR_INTENT");
        }

        if (!concerns.isEmpty()) {
            return ModerationResult.builder()
                .safe(true) // Allow but tag for improvement
                .reason("Content quality concerns")
                .category("UNFIT_DATA")
                .confidence(0.6)
                .flaggedTerms(concerns.toArray(new String[0]))
                .recommendation("REVIEW")
                .build();
        }

        return ModerationResult.builder()
            .safe(true)
            .category("SAFE")
            .confidence(0.8)
            .recommendation("ALLOW")
            .build();
    }

    /**
     * Business logic moderation
     */
    private ModerationResult performBusinessLogicModeration(String content, String userId) {
        List<String> businessFlags = new ArrayList<>();

        // Check for competitor mentions
        if (containsCompetitorMentions(content)) {
            businessFlags.add("COMPETITOR_MENTION");
        }

        // Check for sensitive business terms
        if (containsSensitiveBusinessTerms(content)) {
            businessFlags.add("SENSITIVE_BUSINESS");
        }

        // Check for potential data requests
        if (containsDataRequests(content)) {
            businessFlags.add("DATA_REQUEST");
        }

        if (!businessFlags.isEmpty()) {
            return ModerationResult.builder()
                .safe(true) // Allow but flag for business review
                .reason("Business logic flags detected")
                .category("BUSINESS_REVIEW")
                .confidence(0.7)
                .flaggedTerms(businessFlags.toArray(new String[0]))
                .recommendation("REVIEW")
                .build();
        }

        return ModerationResult.builder()
            .safe(true)
            .category("SAFE")
            .confidence(0.9)
            .recommendation("ALLOW")
            .build();
    }

    /**
     * Check spam patterns với enhanced detection
     */
    private ModerationResult checkSpamPatterns(String content) {
        List<String> spamIndicators = new ArrayList<>();

        // Repeated characters
        if (content.matches(".*(..)\\1{3,}.*")) {
            spamIndicators.add("REPEATED_CHARS");
        }

        // Excessive caps
        long capsCount = content.chars().filter(Character::isUpperCase).count();
        if (capsCount > content.length() * 0.7 && content.length() > 10) {
            spamIndicators.add("EXCESSIVE_CAPS");
        }

        // Excessive punctuation
        long punctCount = content.chars().filter(ch -> "!@#$%^&*()".indexOf(ch) >= 0).count();
        if (punctCount > content.length() * 0.3) {
            spamIndicators.add("EXCESSIVE_PUNCTUATION");
        }

        // Too many numbers (might be spam codes)
        long digitCount = content.chars().filter(Character::isDigit).count();
        if (digitCount > content.length() * 0.5 && content.length() > 5) {
            spamIndicators.add("EXCESSIVE_DIGITS");
        }

        // URL patterns without context
        if (content.matches(".*https?://.*") && content.length() < 50) {
            spamIndicators.add("SUSPICIOUS_URL");
        }

        if (!spamIndicators.isEmpty()) {
            return ModerationResult.builder()
                .safe(false)
                .reason("Spam patterns detected")
                .category("SPAM")
                .confidence(0.8)
                .flaggedTerms(spamIndicators.toArray(new String[0]))
                .recommendation("BLOCK")
                .build();
        }

        return ModerationResult.builder()
            .safe(true)
            .category("SAFE")
            .confidence(0.95)
            .recommendation("ALLOW")
            .build();
    }

    // Helper methods for context analysis
    private boolean isInsufficientData(String content) {
        return content.trim().length() < 5 ||
               content.trim().split("\\s+").length < 2 ||
               content.matches("^[.!?\\s]*$");
    }

    private boolean isContextMismatch(String content, String context) {
        // Simple heuristic - can be enhanced with ML
        return false; // Placeholder
    }

    private boolean isConfusingContent(String content) {
        // Check for excessive question marks, unclear pronouns, etc.
        return content.contains("???") ||
               (content.contains("?") && content.split("\\?").length > 3);
    }

    private boolean containsCompetitorMentions(String content) {
        String lower = content.toLowerCase();
        return lower.contains("competitor") ||
               lower.contains("rival") ||
               lower.contains("alternative platform");
    }

    private boolean containsSensitiveBusinessTerms(String content) {
        String lower = content.toLowerCase();
        return lower.contains("pricing") ||
               lower.contains("revenue") ||
               lower.contains("business model") ||
               lower.contains("internal");
    }

    private boolean containsDataRequests(String content) {
        String lower = content.toLowerCase();
        return lower.contains("export data") ||
               lower.contains("download all") ||
               lower.contains("bulk data") ||
               lower.contains("database");
    }

    private ModerationResult combineResults(List<ModerationResult> results) {
        // Find the most restrictive result
        ModerationResult mostRestrictive = results.stream()
            .filter(r -> !r.isSafe())
            .findFirst()
            .orElse(results.get(0));

        // Combine all flagged terms
        Set<String> allFlags = new HashSet<>();
        for (ModerationResult result : results) {
            if (result.getFlaggedTerms() != null) {
                allFlags.addAll(Arrays.asList(result.getFlaggedTerms()));
            }
        }

        return ModerationResult.builder()
            .safe(mostRestrictive.isSafe())
            .reason(mostRestrictive.getReason())
            .category(mostRestrictive.getCategory())
            .confidence(mostRestrictive.getConfidence())
            .recommendation(mostRestrictive.getRecommendation())
            .flaggedTerms(allFlags.toArray(new String[0]))
            .build();
    }
}
