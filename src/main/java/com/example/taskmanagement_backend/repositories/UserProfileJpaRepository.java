package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserProfileJpaRepository extends JpaRepository<UserProfile, Long> {

    Optional<UserProfile> findByUserId(Long userId);

    @Query("SELECT up FROM UserProfile up WHERE up.user.id = :userId")
    Optional<UserProfile> findByUserIdWithQuery(@Param("userId") Long userId);

    boolean existsByUserId(Long userId);

    void deleteByUserId(Long userId);
}
