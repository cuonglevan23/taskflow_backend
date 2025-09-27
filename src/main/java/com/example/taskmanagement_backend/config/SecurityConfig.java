package com.example.taskmanagement_backend.config;

import com.example.taskmanagement_backend.exceptions.CustomAccessDeniedHandler;
import com.example.taskmanagement_backend.exceptions.CustomAuthenticationEntryPoint;
import com.example.taskmanagement_backend.filters.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    private final CorsConfigurationSource  corsConfigurationSource;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(customAuthenticationEntryPoint)
                        .accessDeniedHandler(customAccessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth

                        // ✅ WebSocket endpoints - Allow handshake without authentication
                        .requestMatchers("/ws/**").permitAll()

                        // OAuth2 Google Login - Allow the actual endpoints
                        .requestMatchers("/api/auth/google/url").permitAll() // Get auth URL
                        .requestMatchers("/api/auth/google/callback").permitAll() // Handle callback
                        .requestMatchers("/api/auth/google/validate").permitAll() // Token validation
                        .requestMatchers("/api/auth/google/status").authenticated() // Status check needs auth

                        // Legacy endpoint for backward compatibility
                        .requestMatchers("/api/auth/google/login-url").permitAll()

                        // Auth endpoints cơ bản
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/logout").permitAll() // Allow logout even with expired tokens
                        .requestMatchers("/api/auth/refresh").permitAll()
                        .requestMatchers("/api/auth/check").authenticated() // Cần authentication để check status

                        // ✅ NEW: Search endpoints - require authentication
                        .requestMatchers("/api/search/**").authenticated() // All search endpoints require authentication
                        .requestMatchers("/api/search/global").authenticated() // Global search
                        .requestMatchers("/api/search/tasks/**").authenticated() // Task search
                        .requestMatchers("/api/search/projects/**").authenticated() // Project search
                        .requestMatchers("/api/search/users/**").authenticated() // User search
                        .requestMatchers("/api/search/teams/**").authenticated() // Team search
                        .requestMatchers("/api/search/quick").authenticated() // Quick search
                        .requestMatchers("/api/search/my").authenticated() // My content search
                        .requestMatchers("/api/search/autocomplete").authenticated() // Universal autocomplete
                        .requestMatchers("/api/search/history/**").authenticated() // Search history
                        .requestMatchers("/api/search/smart-suggestions").authenticated() // Smart suggestions
                        .requestMatchers("/api/search/reindex").authenticated() // Bulk reindex
                        .requestMatchers("/api/search/admin/**").authenticated() // Admin endpoints
                        .requestMatchers(HttpMethod.POST, "/api/search").authenticated() // Unified search

                        // User profile endpoints
                        .requestMatchers("/api/user-profiles/me").authenticated() // Current user profile
                        .requestMatchers("/api/user-profiles/**").authenticated() // Other user profiles

                        // Project endpoints - User's own projects
                        .requestMatchers("/api/projects/my-projects").authenticated() // All user's projects
                        .requestMatchers("/api/projects/my-personal-projects").authenticated() // Personal projects
                        .requestMatchers("/api/projects/my-team-projects").authenticated() // Team projects
                        .requestMatchers("/api/projects/my-projects/progress").authenticated() // Projects progress
                        .requestMatchers("/api/projects/**").authenticated() // Other project endpoints

                        // Team endpoints
                        .requestMatchers("/api/teams/progress/all").authenticated() // All teams progress
                        .requestMatchers("/api/teams/**").authenticated() // Other team endpoints

                        // ✅ NEW: Friend Management endpoints - require authentication
                        .requestMatchers("/api/friends/**").authenticated() // All friend management endpoints

                        // ✅ NEW: Posts and Newsfeed endpoints - require authentication
                        .requestMatchers("/api/posts/**").authenticated() // All post endpoints
                        .requestMatchers("/api/newsfeed/**").authenticated() // Newsfeed endpoints

                        // ✅ NEW: Chat and Messages endpoints - require authentication
                        .requestMatchers("/api/chat/**").authenticated() // All chat endpoints
                        .requestMatchers("/api/messages/**").authenticated() // All message endpoints

                        // ✅ NEW: Profile settings endpoints - require authentication
                        .requestMatchers("/api/profile/**").authenticated() // All profile settings endpoints

                        // ✅ NEW: Notifications endpoints - require authentication
                        .requestMatchers("/api/notifications/**").authenticated() // All notification endpoints

                        // ✅ NEW: Dashboard and Analytics endpoints - require authentication
                        .requestMatchers("/api/dashboard/**").authenticated() // Dashboard endpoints
                        .requestMatchers("/api/analytics/**").authenticated() // Analytics endpoints

                        // Public endpoints
                        .requestMatchers("/api/public/**").permitAll()

                        // Swagger docs
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/index.html"
                        ).permitAll()

                        // User registration
                        .requestMatchers(HttpMethod.POST, "/api/users").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/organizations").permitAll()

                        // ✅ NEW: File Upload endpoints - require authentication
                        .requestMatchers("/api/files/presigned-upload-url").authenticated()
                        .requestMatchers("/api/files/upload").authenticated()
                        .requestMatchers("/api/files/download/**").authenticated()
                        .requestMatchers("/api/files/info/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/files/**").authenticated()

                        // ✅ NEW: Task endpoints with file support
                        .requestMatchers("/api/tasks/my-tasks/*/with-files").authenticated()
                        .requestMatchers("/api/tasks/**").authenticated()

                        // ✅ Default: All other API endpoints require authentication
                        .requestMatchers("/api/**").authenticated()

                        // Allow everything else (static resources, etc.)
                        .anyRequest().permitAll()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
