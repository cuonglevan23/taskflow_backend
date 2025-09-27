package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.Friend;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.enums.FriendshipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendRepository extends JpaRepository<Friend, Long> {

    // Tìm mối quan hệ bạn bè giữa 2 user
    @Query("SELECT f FROM Friend f WHERE " +
           "(f.user.id = :userId AND f.friend.id = :friendId) OR " +
           "(f.user.id = :friendId AND f.friend.id = :userId)")
    Optional<Friend> findFriendshipBetween(@Param("userId") Long userId,
                                          @Param("friendId") Long friendId);

    // Lấy danh sách bạn bè đã được chấp nhận
    @Query("SELECT f FROM Friend f WHERE " +
           "((f.user.id = :userId AND f.status = :status) OR " +
           "(f.friend.id = :userId AND f.status = :status))")
    List<Friend> findAcceptedFriends(@Param("userId") Long userId,
                                    @Param("status") FriendshipStatus status);

    // Lấy lời mời kết bạn đã gửi (PENDING)
    @Query("SELECT f FROM Friend f WHERE f.user.id = :userId AND f.status = :status")
    List<Friend> findSentRequests(@Param("userId") Long userId,
                                 @Param("status") FriendshipStatus status);

    // Lấy lời mời kết bạn đã nhận (PENDING)
    @Query("SELECT f FROM Friend f WHERE f.friend.id = :userId AND f.status = :status")
    List<Friend> findReceivedRequests(@Param("userId") Long userId,
                                     @Param("status") FriendshipStatus status);

    // Kiểm tra xem có phải bạn bè không
    @Query("SELECT COUNT(f) > 0 FROM Friend f WHERE " +
           "((f.user.id = :userId AND f.friend.id = :friendId) OR " +
           "(f.user.id = :friendId AND f.friend.id = :userId)) " +
           "AND f.status = 'ACCEPTED'")
    boolean areFriends(@Param("userId") Long userId, @Param("friendId") Long friendId);

    // Kiểm tra xem có lời mời đang chờ không
    @Query("SELECT COUNT(f) > 0 FROM Friend f WHERE " +
           "((f.user.id = :userId AND f.friend.id = :friendId) OR " +
           "(f.user.id = :friendId AND f.friend.id = :userId)) " +
           "AND f.status = 'PENDING'")
    boolean hasPendingRequest(@Param("userId") Long userId, @Param("friendId") Long friendId);

    // Additional methods needed by FriendService
    List<Friend> findByFriendAndStatus(User friend, FriendshipStatus status);

    List<Friend> findByUserAndStatus(User user, FriendshipStatus status);

    // Count accepted friends for a user
    @Query("SELECT COUNT(f) FROM Friend f WHERE " +
           "((f.user.id = :userId AND f.status = :status) OR " +
           "(f.friend.id = :userId AND f.status = :status))")
    long countAcceptedFriends(@Param("userId") Long userId, @Param("status") FriendshipStatus status);

    // Get accepted friends with pagination
    @Query("SELECT f FROM Friend f WHERE " +
           "((f.user.id = :userId AND f.status = :status) OR " +
           "(f.friend.id = :userId AND f.status = :status))")
    org.springframework.data.domain.Page<Friend> findAcceptedFriendsWithPaging(
            @Param("userId") Long userId,
            @Param("status") FriendshipStatus status,
            org.springframework.data.domain.Pageable pageable);
}
