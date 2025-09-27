package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.Payment;
import com.example.taskmanagement_backend.entities.Payment.PaymentStatus;
import com.example.taskmanagement_backend.entities.Subscription;
import com.example.taskmanagement_backend.entities.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByStripePaymentIntentId(String stripePaymentIntentId);

    List<Payment> findByUser(User user);

    List<Payment> findByUserOrderByCreatedAtDesc(User user);

    List<Payment> findBySubscription(Subscription subscription);

    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);

    Page<Payment> findByUser(User user, Pageable pageable);

    @Query("SELECT p FROM Payment p WHERE p.user = :user AND p.status = :status")
    List<Payment> findByUserAndStatus(@Param("user") User user, @Param("status") PaymentStatus status);

    @Query("SELECT p FROM Payment p WHERE p.createdAt >= :startDate AND p.createdAt <= :endDate")
    List<Payment> findByCreatedAtBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.status = :status")
    Long countByStatus(@Param("status") PaymentStatus status);

    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.status = 'SUCCEEDED' AND p.createdAt >= :startDate AND p.createdAt <= :endDate")
    BigDecimal sumSuccessfulPaymentsBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.status = 'SUCCEEDED' AND p.user = :user")
    BigDecimal sumSuccessfulPaymentsByUser(@Param("user") User user);

    @Query("SELECT p FROM Payment p WHERE p.user.id = :userId ORDER BY p.createdAt DESC")
    Page<Payment> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId, Pageable pageable);
}
