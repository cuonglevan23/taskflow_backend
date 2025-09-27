package com.example.taskmanagement_backend.security;

import com.example.taskmanagement_backend.services.infrastructure.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT Blacklist Filter
 * 
 * Intercepts all requests to check if JWT token is blacklisted
 * Integrates with NextAuth OAuth2 flow for secure logout
 * 
 * @author Task Management Team
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtBlacklistFilter extends OncePerRequestFilter {

    private final TokenBlacklistService tokenBlacklistService;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        try {
            // Extract JWT token from Authorization header
            String token = extractTokenFromRequest(request);
            
            if (token != null) {
                // Check if token is blacklisted
                if (tokenBlacklistService.isTokenBlacklisted(token)) {
                    log.warn("🚫 Blacklisted token detected from IP: {}", getClientIpAddress(request));
                    
                    // Return 401 Unauthorized for blacklisted tokens
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType("application/json");
                    response.getWriter().write(
                        "{\"error\":\"Token has been revoked\",\"message\":\"Please login again\"}"
                    );
                    return; // Stop filter chain
                }
            }
            
            // Continue with filter chain if token is valid or not present
            filterChain.doFilter(request, response);
            
        } catch (Exception e) {
            log.error("❌ Error in JWT blacklist filter", e);
            // Continue with filter chain on error (fail-safe approach)
            filterChain.doFilter(request, response);
        }
    }
    
    /**
     * Extract JWT token from Authorization header
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7); // Remove "Bearer " prefix
        }
        
        return null;
    }
    
    /**
     * Get client IP address for logging
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * Skip filter for certain endpoints (like login, public endpoints)
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        
        // Skip blacklist check for these endpoints
        return path.startsWith("/api/auth/login") ||
               path.startsWith("/api/auth/google") ||
               path.startsWith("/swagger-ui") ||
               path.startsWith("/v3/api-docs") ||
               path.equals("/api/auth/refresh"); // Allow refresh even if access token is blacklisted
    }
}