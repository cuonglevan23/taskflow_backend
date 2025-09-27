package com.example.taskmanagement_backend.services.infrastructure;

import com.example.taskmanagement_backend.exceptions.HttpException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Map;

@Service
@Slf4j
public class GoogleTokenService {

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    @Value("${spring.security.oauth2.client.registration.google.redirect-uri}")
    private String redirectUri;

    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_USERINFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo";

    public Map<String, Object> exchangeCodeForTokens(String code) {
        RestTemplate restTemplate = new RestTemplate();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("code", code);
        params.add("grant_type", "authorization_code");
        params.add("redirect_uri", redirectUri);
        
        log.info("Exchanging code for tokens with Google");
        log.info("Client ID: {}", clientId.substring(0, 10) + "...");
        log.info("Redirect URI: {}", redirectUri);
        
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(GOOGLE_TOKEN_URL, request, Map.class);
            
            log.info("Google token response status: {}", response.getStatusCode());
            
            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> responseBody = response.getBody();
                log.info("Successfully received tokens from Google");
                return responseBody;
            } else {
                log.error("Failed to exchange code for tokens. Status: {}", response.getStatusCode());
                throw new HttpException("Failed to exchange code for tokens", HttpStatus.UNAUTHORIZED);
            }
        } catch (Exception e) {
            log.error("Error exchanging code for tokens: {}", e.getMessage(), e);
            throw new HttpException("OAuth token exchange failed: " + e.getMessage(), HttpStatus.UNAUTHORIZED);
        }
    }

    public Map<String, Object> getUserInfo(String accessToken) {
        RestTemplate restTemplate = new RestTemplate();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                GOOGLE_USERINFO_URL, 
                HttpMethod.GET, 
                request, 
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            } else {
                throw new HttpException("Failed to get user info", HttpStatus.UNAUTHORIZED);
            }
        } catch (Exception e) {
            log.error("Error getting user info", e);
            throw new HttpException("Failed to get user info from Google", HttpStatus.UNAUTHORIZED);
        }
    }
}