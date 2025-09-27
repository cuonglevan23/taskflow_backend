package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.RefreshToken;
import com.example.taskmanagement_backend.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    
    Optional<RefreshToken> findByTokenAndIsRevokedFalse(String token);
    
    List<RefreshToken> findByUserAndIsRevokedFalse(User user);
    
    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken rt SET rt.isRevoked = true WHERE rt.user = :user")
    void revokeAllByUser(User user);
    
    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now OR rt.isRevoked = true")
    int deleteExpiredAndRevokedTokens(LocalDateTime now);

    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken rt WHERE rt.createdAt < :cutoffDate")
    int deleteTokensOlderThan(LocalDateTime cutoffDate);

    @Modifying
    @Transactional
    @Query("""
        DELETE FROM RefreshToken rt WHERE rt.id NOT IN (
            SELECT sub.id FROM (
                SELECT rt2.id as id FROM RefreshToken rt2 
                WHERE rt2.user = rt.user 
                ORDER BY rt2.createdAt DESC 
                LIMIT :maxTokensPerUser
            ) sub
        )
        """)
    int deleteExcessTokensPerUser(int maxTokensPerUser);

    // Statistics queries
    @Query("SELECT COUNT(rt) FROM RefreshToken rt WHERE rt.isRevoked = false AND rt.expiresAt > :now")
    long countActiveTokens(LocalDateTime now);

    @Query("SELECT COUNT(rt) FROM RefreshToken rt WHERE rt.expiresAt < :now")
    long countExpiredTokens(LocalDateTime now);

    @Query("SELECT COUNT(rt) FROM RefreshToken rt WHERE rt.isRevoked = true")
    long countRevokedTokens();

    // Convenience methods with default parameter
    default long countActiveTokens() {
        return countActiveTokens(LocalDateTime.now());
    }
}