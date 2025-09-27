package com.example.taskmanagement_backend.agent.service;

import com.example.taskmanagement_backend.agent.dto.ModerationResult;
import com.example.taskmanagement_backend.agent.moderation.ToxicFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Simple Moderation Service - Basic wrapper cho ToxicFilter
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModerationService {

    private final ToxicFilter toxicFilter;

    /**
     * Moderate content
     */
    public ModerationResult moderateContent(String content, String userId) {
        return toxicFilter.checkToxicity(content);
    }
}
