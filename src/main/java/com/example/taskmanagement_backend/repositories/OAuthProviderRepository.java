package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.OAuthProvider;
import com.example.taskmanagement_backend.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OAuthProviderRepository extends JpaRepository<OAuthProvider, Long> {
    
    Optional<OAuthProvider> findByProviderNameAndProviderUserId(String providerName, String providerUserId);
    
    Optional<OAuthProvider> findByUserAndProviderName(User user, String providerName);
    
    Optional<OAuthProvider> findByUser_EmailAndProviderName(String userEmail, String providerName);

    boolean existsByProviderNameAndProviderUserId(String providerName, String providerUserId);
}