package com.example.taskmanagement_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // API endpoints CORS - Adding production domain and dev origins
        registry.addMapping("/api/**")
                .allowedOrigins(
                    // Production domains
                    "https://main.d2az19adxqfdf3.amplifyapp.com",
                    "https://main.d4nz8d2yz1imm.amplifyapp.com",
                    // Development origins
                    "http://localhost:3000",
                    "http://127.0.0.1:3000",
                    "http://localhost:8081",
                    "http://127.0.0.1:8081",
                    "http://localhost",
                    "http://127.0.0.1",
                    "http://localhost:5173",  // Vite default
                    "http://127.0.0.1:5173"   // Vite default
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);

        // WebSocket endpoints CORS - Matching the same origins
        registry.addMapping("/ws/**")
                .allowedOrigins(
                    // Production domains
                    "https://main.d2az19adxqfdf3.amplifyapp.com",
                    "https://main.d4nz8d2yz1imm.amplifyapp.com",
                    // Development origins
                    "http://localhost:3000",
                    "http://127.0.0.1:3000",
                    "http://localhost:8081",
                    "http://127.0.0.1:8081",
                    "http://localhost",
                    "http://127.0.0.1",
                    "http://localhost:5173",  // Vite default
                    "http://127.0.0.1:5173"   // Vite default
                )
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Adding production domain and development origins
        configuration.setAllowedOrigins(Arrays.asList(
            // Production domain
            "https://main.d2az19adxqfdf3.amplifyapp.com",
            "https://main.d4nz8d2yz1imm.amplifyapp.com",
            // Development origins
            "http://localhost:3000",
            "http://127.0.0.1:3000",
            "http://localhost:8081",
            "http://127.0.0.1:8081",
            "http://localhost",
            "http://127.0.0.1",
            "http://localhost:5173",  // Vite default
            "http://127.0.0.1:5173"   // Vite default
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Register CORS for both API and WebSocket endpoints
        source.registerCorsConfiguration("/api/**", configuration);
        source.registerCorsConfiguration("/ws/**", configuration);

        return source;
    }
}
