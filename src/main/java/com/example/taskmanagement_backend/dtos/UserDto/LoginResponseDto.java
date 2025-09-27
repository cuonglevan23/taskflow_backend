package com.example.taskmanagement_backend.dtos.UserDto;

import com.example.taskmanagement_backend.dtos.UserProfileDto.UserProfileResponseDto;
import com.example.taskmanagement_backend.entities.Role;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class LoginResponseDto {
    String email;
    Long id;
    List<Role> roles;
    UserProfileResponseDto profile;
    String accessToken;
}