package com.example.taskmanagement_backend.config;

import com.example.taskmanagement_backend.services.OnlineStatusService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
@Slf4j
public class OnlineStatusInterceptor implements HandlerInterceptor {

    private final OnlineStatusService onlineStatusService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Chỉ cập nhật lastSeen cho authenticated users
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated() &&
            !"anonymousUser".equals(authentication.getName())) {

            // Chỉ cập nhật cho các API calls, không phải static resources
            String requestURI = request.getRequestURI();
            if (requestURI.startsWith("/api/")) {
                try {
                    onlineStatusService.updateLastSeen(authentication.getName());
                } catch (Exception e) {
                    // Không để lỗi này ảnh hưởng đến request chính
                    log.debug("Failed to update lastSeen for user {}: {}", authentication.getName(), e.getMessage());
                }
            }
        }

        return true;
    }
}
