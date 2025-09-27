package com.example.taskmanagement_backend.entities;

import com.example.taskmanagement_backend.enums.FriendshipStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "friends",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "friend_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class Friend {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // Người gửi lời mời

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "friend_id", nullable = false)
    private User friend; // Người nhận lời mời

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private FriendshipStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    // Business methods
    public boolean isPending() {
        return status == FriendshipStatus.PENDING;
    }

    public boolean isAccepted() {
        return status == FriendshipStatus.ACCEPTED;
    }

    public boolean isBlocked() {
        return status == FriendshipStatus.BLOCKED;
    }

    public void accept() {
        this.status = FriendshipStatus.ACCEPTED;
        this.acceptedAt = LocalDateTime.now();
    }

    public void block() {
        this.status = FriendshipStatus.BLOCKED;
    }

    public void unblock() {
        this.status = FriendshipStatus.ACCEPTED;
    }
}
