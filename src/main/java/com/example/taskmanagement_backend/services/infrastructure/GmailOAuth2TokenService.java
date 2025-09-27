package com.example.taskmanagement_backend.services.infrastructure;

import com.example.taskmanagement_backend.entities.OAuthProvider;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.repositories.OAuthProviderRepository;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GmailOAuth2TokenService {

    private final UserJpaRepository userRepository;
    private final OAuthProviderRepository oAuthProviderRepository;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    /**
     * Get valid access token for Gmail API from database
     */
    public String getValidAccessToken(String userEmail) {
        log.info("🔍 Retrieving Gmail access token for user: {}", userEmail);

        // Find user by email
        Optional<User> userOpt = userRepository.findByEmail(userEmail);
        if (userOpt.isEmpty()) {
            log.warn("⚠️ User not found: {}", userEmail);
            throw new RuntimeException("User not found: " + userEmail);
        }

        User user = userOpt.get();

        // Find OAuth provider for this user
        Optional<OAuthProvider> oAuthProviderOpt = oAuthProviderRepository
                .findByUserAndProviderName(user, "google");

        if (oAuthProviderOpt.isEmpty()) {
            log.warn("⚠️ No Gmail OAuth2 provider found for user: {}. OAuth2 flow needs to be completed.", userEmail);
            throw new RuntimeException("Gmail access token not found for user: " + userEmail +
                ". Please complete Gmail OAuth2 authorization flow first.");
        }

        OAuthProvider oAuthProvider = oAuthProviderOpt.get();
        String accessToken = oAuthProvider.getAccessToken();

        if (accessToken == null || accessToken.trim().isEmpty()) {
            log.warn("⚠️ No Gmail access token found for user: {}. OAuth2 flow needs to be completed.", userEmail);
            throw new RuntimeException("Gmail access token not found for user: " + userEmail +
                ". Please complete Gmail OAuth2 authorization flow first.");
        }

        // Check if token is expired
        if (oAuthProvider.getTokenExpiresAt() != null &&
            oAuthProvider.getTokenExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("⚠️ Gmail access token expired for user: {}. Re-authorization needed.", userEmail);
            throw new RuntimeException("Gmail access token expired for user: " + userEmail +
                ". Please re-authorize Gmail access.");
        }

        log.info("✅ Valid Gmail access token found for user: {}", userEmail);
        return accessToken;
    }

    /**
     * Check if user has valid Gmail access token
     */
    public boolean hasValidToken(String userEmail) {
        try {
            getValidAccessToken(userEmail);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get OAuth2 authorization URL for Gmail API access
     */
    public String getGmailAuthorizationUrl(String userEmail) {
        // This URL should be generated with proper OAuth2 scopes for Gmail
        String scopes = "https://www.googleapis.com/auth/gmail.send," +
                       "https://www.googleapis.com/auth/gmail.readonly," +
                       "https://www.googleapis.com/auth/gmail.compose";

        String authUrl = "https://accounts.google.com/o/oauth2/v2/auth" +
                "?client_id=" + clientId +
                "&response_type=code" +
                "&scope=" + scopes +
                "&access_type=offline" +
                "&redirect_uri=http://localhost:8080/api/auth/gmail/callback" +
                "&state=" + userEmail;

        log.info("🔗 Generated Gmail authorization URL for user: {}", userEmail);
        return authUrl;
    }

    /**
     * Check system user token status
     */
    public void checkSystemUserToken() {
        String systemEmail = "cuonglv.21ad@vku.udn.vn";

        if (!hasValidToken(systemEmail)) {
            log.info("🚀 System user Gmail token not found. Authorization required.");
            log.info("📧 System admin needs to authorize Gmail access at:");
            log.info("🔗 {}", getGmailAuthorizationUrl(systemEmail));
        } else {
            log.info("✅ System user Gmail token is available");
        }
    }
}
