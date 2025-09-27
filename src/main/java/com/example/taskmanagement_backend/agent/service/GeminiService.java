package com.example.taskmanagement_backend.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Simple Gemini Service - Mock implementation
 */
@Slf4j
@Service
public class GeminiService {

    private final Random random = new Random();
    private final List<String> mockResponses = Arrays.asList(
        "Tôi hiểu yêu cầu của bạn về Taskflow. Để hỗ trợ tốt hơn, bạn có thể chi tiết hơn không?",
        "Về quản lý task trong Taskflow, tôi có thể hướng dẫn bạn các bước cụ thể.",
        "Dựa trên kinh nghiệm sử dụng Taskflow, tôi khuyên bạn nên...",
        "Đây là tính năng hữu ích trong Taskflow. Tôi sẽ giải thích chi tiết."
    );

    public String generateResponse(String userMessage, String context, String ragContext) {
        try {
            Thread.sleep(1000 + random.nextInt(1500)); // Simulate processing
            return getRandomResponse();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Xin lỗi, có sự cố kỹ thuật. Vui lòng thử lại.";
        }
    }

    public String detectIntent(String message) {
        String lower = message.toLowerCase();
        if (lower.contains("task")) return "task_management";
        if (lower.contains("project")) return "project_management";
        if (lower.contains("team")) return "team_collaboration";
        return "general";
    }

    public double calculateConfidence(String userMessage, String response, String context) {
        return 0.85; // Mock confidence
    }

    private String getRandomResponse() {
        return mockResponses.get(random.nextInt(mockResponses.size()));
    }
}
