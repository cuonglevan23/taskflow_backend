package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.Subscription;
import com.example.taskmanagement_backend.entities.Subscription.PlanType;
import com.example.taskmanagement_backend.entities.Subscription.SubscriptionStatus;
import com.example.taskmanagement_backend.entities.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);

    Optional<Subscription> findByUserAndStatus(User user, SubscriptionStatus status);

    Optional<Subscription> findByUserIdAndStatus(Long userId, SubscriptionStatus status);

    List<Subscription> findByUser(User user);

    List<Subscription> findByUserOrderByCreatedAtDesc(User user);

    Page<Subscription> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<Subscription> findByUserAndStatusIn(User user, List<SubscriptionStatus> statuses);

    Page<Subscription> findByStatus(SubscriptionStatus status, Pageable pageable);

    Page<Subscription> findByPlanType(PlanType planType, Pageable pageable);

    @Query("SELECT s FROM Subscription s WHERE s.status = 'ACTIVE' AND s.currentPeriodEnd <= :endDate")
    List<Subscription> findActiveSubscriptionsExpiringBefore(@Param("endDate") LocalDateTime endDate);

    @Query("SELECT s FROM Subscription s WHERE s.status = 'TRIALING' AND s.trialEnd <= :endDate")
    List<Subscription> findTrialingSubscriptionsExpiringBefore(@Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(s) FROM Subscription s WHERE s.status = :status")
    Long countByStatus(@Param("status") SubscriptionStatus status);

    @Query("SELECT COUNT(s) FROM Subscription s WHERE s.planType = :planType AND s.status = 'ACTIVE'")
    Long countActiveByPlanType(@Param("planType") PlanType planType);

    @Query("SELECT s FROM Subscription s WHERE s.user.id = :userId AND s.status IN ('ACTIVE', 'TRIALING')")
    Optional<Subscription> findActiveSubscriptionByUserId(@Param("userId") Long userId);

    boolean existsByUserAndStatusIn(User user, List<SubscriptionStatus> statuses);
}
