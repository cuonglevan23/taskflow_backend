package com.example.taskmanagement_backend.agent.moderation;

import com.example.taskmanagement_backend.agent.dto.ModerationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Enhanced Toxic Filter - Rule-based và AI-pattern filter
 * Chặn spam/toxic/trộm dữ liệu theo flow: Moderation Layer → Memory Layer → RAG Layer → LLM Layer
 */
@Slf4j
@Component
public class ToxicFilter {

    // Toxic keywords (English & Vietnamese)
    private static final List<String> TOXIC_KEYWORDS = Arrays.asList(
        // English
        "spam", "hack", "cheat", "exploit", "scam", "malware", "virus", "phishing",
        "steal", "fraud", "fake", "bot", "automated", "script",
        // Vietnamese
        "lừa đảo", "hack", "cheat", "gian lận", "spam", "rác", "độc hại",
        "ăn cắp", "trộm", "giả mạo", "lừa", "bịp", "chiếm đoạt"
    );

    // Data theft patterns
    private static final List<String> DATA_THEFT_PATTERNS = Arrays.asList(
        "password", "mật khẩu", "login", "đăng nhập", "token", "api key",
        "database", "cơ sở dữ liệu", "backup", "export", "download",
        "admin", "root", "superuser", "privilege", "quyền admin"
    );

    // Inappropriate content (Vietnamese context)
    private static final List<String> INAPPROPRIATE_WORDS = Arrays.asList(
        "bẩn", "tục tĩu", "khiêu dâm", "sex", "nude", "xxx"
    );

    // Spam injection patterns
    private static final Pattern SQL_INJECTION = Pattern.compile(".*(?i)(select|insert|update|delete|drop|union|script).*");
    private static final Pattern XSS_PATTERN = Pattern.compile(".*(?i)(<script|javascript:|onload=|onerror=).*");
    private static final Pattern EXCESSIVE_CAPS = Pattern.compile(".*[A-Z]{5,}.*");
    private static final Pattern REPEATED_CHARS = Pattern.compile("(..)\\1{4,}");
    private static final Pattern EXCESSIVE_PUNCTUATION = Pattern.compile("[!@#$%^&*()]{4,}");
    private static final Pattern SUSPICIOUS_URLS = Pattern.compile(".*(?i)(bit\\.ly|tinyurl|t\\.co|goo\\.gl|short\\.link).*");

    /**
     * Main toxicity check method với comprehensive analysis
     */
    public ModerationResult checkToxicity(String content) {
        return checkToxicity(content, null);
    }

    /**
     * Enhanced toxicity check với user context
     */
    public ModerationResult checkToxicity(String content, String userContext) {
        if (content == null || content.trim().isEmpty()) {
            return ModerationResult.builder()
                .safe(false)
                .reason("Empty content")
                .category("INVALID")
                .confidence(1.0)
                .recommendation("BLOCK")
                .build();
        }

        String lowerContent = content.toLowerCase();
        log.debug("Checking toxicity for content length: {}", content.length());

        // Step 1: Check for data theft attempts
        ModerationResult dataTheftCheck = checkDataTheftAttempts(lowerContent);
        if (!dataTheftCheck.isSafe()) {
            log.warn("Data theft attempt detected: {}", dataTheftCheck.getReason());
            return dataTheftCheck;
        }

        // Step 2: Check for injection attacks
        ModerationResult injectionCheck = checkInjectionAttempts(content);
        if (!injectionCheck.isSafe()) {
            log.warn("Injection attempt detected: {}", injectionCheck.getReason());
            return injectionCheck;
        }

        // Step 3: Check for toxic keywords
        List<String> flaggedTerms = TOXIC_KEYWORDS.stream()
            .filter(lowerContent::contains)
            .toList();

        if (!flaggedTerms.isEmpty()) {
            return ModerationResult.builder()
                .safe(false)
                .reason("Contains toxic keywords: " + String.join(", ", flaggedTerms))
                .category("TOXIC")
                .confidence(0.9)
                .flaggedTerms(flaggedTerms.toArray(new String[0]))
                .recommendation("BLOCK")
                .build();
        }

        // Step 4: Check for inappropriate content
        List<String> inappropriateFound = INAPPROPRIATE_WORDS.stream()
            .filter(lowerContent::contains)
            .toList();

        if (!inappropriateFound.isEmpty()) {
            return ModerationResult.builder()
                .safe(false)
                .reason("Contains inappropriate content")
                .category("INAPPROPRIATE")
                .confidence(0.8)
                .flaggedTerms(inappropriateFound.toArray(new String[0]))
                .recommendation("REVIEW")
                .build();
        }

        // Step 5: Check for spam patterns
        ModerationResult spamCheck = checkSpamPatterns(content);
        if (!spamCheck.isSafe()) {
            return spamCheck;
        }

        // Step 6: Check suspicious URLs
        ModerationResult urlCheck = checkSuspiciousUrls(content);
        if (!urlCheck.isSafe()) {
            return urlCheck;
        }

        return ModerationResult.builder()
            .safe(true)
            .reason("Content passed all security checks")
            .category("SAFE")
            .confidence(0.95)
            .recommendation("ALLOW")
            .build();
    }

    /**
     * Check for data theft attempts - Phát hiện thử trộm dữ liệu
     */
    private ModerationResult checkDataTheftAttempts(String lowerContent) {
        List<String> dataTheftFound = DATA_THEFT_PATTERNS.stream()
            .filter(lowerContent::contains)
            .toList();

        if (!dataTheftFound.isEmpty()) {
            return ModerationResult.builder()
                .safe(false)
                .reason("Potential data theft attempt detected")
                .category("DATA_THEFT")
                .confidence(0.95)
                .flaggedTerms(dataTheftFound.toArray(new String[0]))
                .recommendation("BLOCK")
                .build();
        }

        return ModerationResult.builder().safe(true).build();
    }

    /**
     * Check for injection attacks - Phát hiện injection
     */
    private ModerationResult checkInjectionAttempts(String content) {
        if (SQL_INJECTION.matcher(content).matches()) {
            return ModerationResult.builder()
                .safe(false)
                .reason("SQL injection attempt detected")
                .category("SECURITY_THREAT")
                .confidence(0.95)
                .recommendation("BLOCK")
                .build();
        }

        if (XSS_PATTERN.matcher(content).matches()) {
            return ModerationResult.builder()
                .safe(false)
                .reason("XSS attack attempt detected")
                .category("SECURITY_THREAT")
                .confidence(0.95)
                .recommendation("BLOCK")
                .build();
        }

        return ModerationResult.builder().safe(true).build();
    }

    /**
     * Enhanced spam pattern detection
     */
    private ModerationResult checkSpamPatterns(String content) {
        // Excessive caps
        if (EXCESSIVE_CAPS.matcher(content).matches() && content.length() > 10) {
            return ModerationResult.builder()
                .safe(false)
                .reason("Excessive capital letters detected")
                .category("SPAM")
                .confidence(0.7)
                .recommendation("REVIEW")
                .build();
        }

        // Repeated characters (more strict)
        if (REPEATED_CHARS.matcher(content).find()) {
            return ModerationResult.builder()
                .safe(false)
                .reason("Repeated character spam pattern detected")
                .category("SPAM")
                .confidence(0.8)
                .recommendation("BLOCK")
                .build();
        }

        // Excessive punctuation
        if (EXCESSIVE_PUNCTUATION.matcher(content).find()) {
            return ModerationResult.builder()
                .safe(false)
                .reason("Excessive punctuation spam detected")
                .category("SPAM")
                .confidence(0.6)
                .recommendation("REVIEW")
                .build();
        }

        // Message too long (potential spam)
        if (content.length() > 5000) {
            return ModerationResult.builder()
                .safe(false)
                .reason("Message exceeds maximum length (potential spam)")
                .category("SPAM")
                .confidence(0.9)
                .recommendation("BLOCK")
                .build();
        }

        // Too many repeated words
        String[] words = content.split("\\s+");
        if (words.length > 10) {
            long uniqueWords = Arrays.stream(words).distinct().count();
            double repetitionRatio = (double) uniqueWords / words.length;

            if (repetitionRatio < 0.3) { // Less than 30% unique words
                return ModerationResult.builder()
                    .safe(false)
                    .reason("High word repetition detected (spam pattern)")
                    .category("SPAM")
                    .confidence(0.8)
                    .recommendation("REVIEW")
                    .build();
            }
        }

        return ModerationResult.builder().safe(true).build();
    }

    /**
     * Check for suspicious URLs
     */
    private ModerationResult checkSuspiciousUrls(String content) {
        if (SUSPICIOUS_URLS.matcher(content).find()) {
            return ModerationResult.builder()
                .safe(false)
                .reason("Suspicious shortened URL detected")
                .category("SUSPICIOUS_LINK")
                .confidence(0.8)
                .recommendation("REVIEW")
                .build();
        }

        return ModerationResult.builder().safe(true).build();
    }

    /**
     * Get moderation statistics for monitoring
     */
    public String getModerationStats() {
        return String.format(
            "ToxicFilter Stats - Patterns: %d toxic keywords, %d data theft patterns, %d inappropriate words",
            TOXIC_KEYWORDS.size(), DATA_THEFT_PATTERNS.size(), INAPPROPRIATE_WORDS.size()
        );
    }
}
