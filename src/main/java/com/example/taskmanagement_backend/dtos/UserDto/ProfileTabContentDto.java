package com.example.taskmanagement_backend.dtos.UserDto;

import com.example.taskmanagement_backend.dtos.FriendDto.FriendDto;
import com.example.taskmanagement_backend.dtos.PostDto.PostResponseDto;
import com.example.taskmanagement_backend.dtos.TaskDto.TaskResponseDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileTabContentDto {
    // Posts tab content
    private List<PostResponseDto> posts;
    private boolean hasMorePosts;
    private int totalPages;

    // Friends tab content
    private List<FriendDto> friends;
    private List<FriendDto> mutualFriends;
    private boolean hasMoreFriends;

    // Tasks tab content
    private List<TaskResponseDto> publicTasks;
    private List<TaskResponseDto> sharedTasks;
    private boolean hasMoreTasks;

    // Tab-specific metadata
    private String tabType; // "posts", "friends", "tasks"
    private int currentPage;
    private int pageSize;
}
