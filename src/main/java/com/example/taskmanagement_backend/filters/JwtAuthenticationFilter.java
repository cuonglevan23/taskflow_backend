package com.example.taskmanagement_backend.filters;


import java.io.IOException;

import com.example.taskmanagement_backend.services.infrastructure.JwtService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        // Skip JWT processing for public endpoints
        String requestPath = request.getRequestURI();
        if (isPublicEndpoint(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        String jwt = null;
        String email = null;

        // Đọc JWT từ cookie trước (ưu tiên cho web browser)
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("accessToken".equals(cookie.getName())) {
                    jwt = cookie.getValue();
                    break;
                }
            }
        }

        // Fallback: vẫn support Authorization header cho API clients (mobile, postman, etc.)
        if (jwt == null) {
            final String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                jwt = authHeader.substring(7);
            }
        }

        if (jwt != null) {
            try {
                email = this.jwtService.extractEmail(jwt);
            } catch (ExpiredJwtException e) {
                log.warn("❌ JWT token expired for request: {}", requestPath);
                clearAuthCookies(response);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Token expired\",\"message\":\"Please login again\"}");
                return;
            } catch (MalformedJwtException | SignatureException e) {
                log.warn("❌ Invalid JWT token for request: {}", requestPath);
                clearAuthCookies(response);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Invalid token\",\"message\":\"Please login again\"}");
                return;
            } catch (Exception e) {
                log.error("❌ JWT processing error for request: {}: {}", requestPath, e.getMessage());
                clearAuthCookies(response);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Authentication failed\",\"message\":\"Please login again\"}");
                return;
            }
        }

        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(email);

                if (jwtService.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.debug("✅ Successfully authenticated user: {}", email);
                } else {
                    log.warn("❌ Invalid JWT token for user: {}", email);
                    // Clear invalid cookies to prevent retry loops
                    if (cookies != null) {
                        clearAuthCookies(response);
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\":\"Invalid token\",\"message\":\"Please login again\"}");
                        return;
                    }
                }
            } catch (Exception e) {
                log.error("❌ Cannot set user authentication for {}: {}", email, e.getMessage());

                // Clear invalid cookie nếu JWT từ cookie
                if (jwt != null && cookies != null) {
                    clearAuthCookies(response);
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Authentication failed\",\"message\":\"Please login again\"}");
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }
    
    private boolean isPublicEndpoint(String requestPath) {
        return requestPath.startsWith("/api/auth/") ||
               requestPath.startsWith("/api/public/") ||
               requestPath.startsWith("/swagger-ui/") ||
               requestPath.startsWith("/v3/api-docs/") ||
               requestPath.equals("/swagger-ui.html");
    }

    /**
     * Clear authentication cookies when token is invalid
     */
    private void clearAuthCookies(HttpServletResponse response) {
        Cookie accessTokenCookie = new Cookie("accessToken", "");
        accessTokenCookie.setMaxAge(0);
        accessTokenCookie.setPath("/");
        accessTokenCookie.setHttpOnly(true);
        response.addCookie(accessTokenCookie);

        Cookie refreshTokenCookie = new Cookie("refreshToken", "");
        refreshTokenCookie.setMaxAge(0);
        refreshTokenCookie.setPath("/");
        refreshTokenCookie.setHttpOnly(true);
        response.addCookie(refreshTokenCookie);

        log.info("🧹 Cleared invalid authentication cookies");
    }
}