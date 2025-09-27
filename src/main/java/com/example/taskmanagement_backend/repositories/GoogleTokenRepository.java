package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.GoogleToken;
import com.example.taskmanagement_backend.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GoogleTokenRepository extends JpaRepository<GoogleToken, Long> {

    /**
     * Tìm Google token theo user
     */
    Optional<GoogleToken> findByUser(User user);

    /**
     * Tìm Google token theo user ID
     */
    Optional<GoogleToken> findByUserId(Long userId);

    /**
     * Tìm Google token active theo user email
     */
    @Query("SELECT gt FROM GoogleToken gt WHERE gt.user.email = :email AND gt.isActive = true")
    Optional<GoogleToken> findActiveTokenByUserEmail(@Param("email") String email);

    /**
     * Kiểm tra user có Google Calendar permissions không
     */
    @Query("SELECT CASE WHEN COUNT(gt) > 0 THEN true ELSE false END " +
           "FROM GoogleToken gt WHERE gt.user.id = :userId " +
           "AND gt.isActive = true AND gt.scope LIKE '%calendar%'")
    boolean hasCalendarPermissions(@Param("userId") Long userId);

    /**
     * Xóa tất cả tokens của user
     */
    void deleteByUser(User user);
}
