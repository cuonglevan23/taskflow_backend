package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.TeamMemberDto.AddTeamMemberByEmailRequestDto;
import com.example.taskmanagement_backend.dtos.TeamMemberDto.CreateTeamMemberRequestDto;
import com.example.taskmanagement_backend.dtos.TeamMemberDto.TeamMemberResponseDto;
import com.example.taskmanagement_backend.entities.Team;
import com.example.taskmanagement_backend.entities.TeamMember;
import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.entities.UserProfile;

import com.example.taskmanagement_backend.repositories.TeamJpaRepository;
import com.example.taskmanagement_backend.repositories.TeamMemberJpaRepository;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TeamMemberService {
    @Autowired
    private TeamMemberJpaRepository teamMemberJpaRepository;
    @Autowired
    private TeamJpaRepository teamJpaRepository;
    @Autowired
    private UserJpaRepository userRepository;

    public List<TeamMemberResponseDto> getMembersByTeam(Long teamId) {
        List<TeamMember> members = teamMemberJpaRepository.findByTeamId(teamId);
        System.out.println("🔍 Found " + members.size() + " team members for team ID: " + teamId);
        if (!members.isEmpty()) {
            System.out.println("First member details - User ID: " + members.get(0).getUser().getId() + 
                             ", Team ID: " + members.get(0).getTeam().getId());
        }
        return members.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    public TeamMemberResponseDto createTeamMember(CreateTeamMemberRequestDto dto) {
        // Tìm user bằng email
        User user = userRepository.findByEmail(dto.getUserEmail())
                .orElseThrow(() -> new RuntimeException("User not found with email: " + dto.getUserEmail()));

        // Kiểm tra user đã tồn tại trong team hay chưa
        if (teamMemberJpaRepository.existsByTeamIdAndUserId(dto.getTeamId(), user.getId())) {
            throw new RuntimeException("User is already a member of this team");
        }

        Team team = teamJpaRepository.findById(dto.getTeamId())
                .orElseThrow(()-> new RuntimeException("team not found with id: " + dto.getTeamId()));

        TeamMember teamMember = TeamMember.builder()
                .team(team)
                .user(user)
                .joinedAt(LocalDateTime.now())
                .build();
        return  convertToDto(teamMemberJpaRepository.save(teamMember));


    }
    public void deleteTeamMember(Long id) {
        if (!teamMemberJpaRepository.existsById(id)) {
            throw new RuntimeException("Team member not found with id: " + id);
        }
        teamMemberJpaRepository.deleteById(id);
    }

    /**
     * Adds a team member using email address
     */
    public TeamMemberResponseDto addMember(AddTeamMemberByEmailRequestDto dto) {
        // Tìm user bằng email
        User user = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found with email: " + dto.getEmail()));

        // For now we'll default to team ID 1, you should update this to get the team ID from the DTO
        // when you update the AddTeamMemberByEmailRequestDto to include a teamId field
        Long teamId = 1L; // This needs to be updated when the DTO is updated

        // Kiểm tra user đã tồn tại trong team hay chưa
        if (teamMemberJpaRepository.existsByTeamIdAndUserId(teamId, user.getId())) {
            throw new RuntimeException("User is already a member of this team");
        }

        Team team = teamJpaRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("team not found with id: " + teamId));

        TeamMember teamMember = TeamMember.builder()
                .team(team)
                .user(user)
                .role(dto.getRole()) // Use the role from the DTO
                .joinedAt(LocalDateTime.now())
                .build();

        return convertToDto(teamMemberJpaRepository.save(teamMember));
    }

    private TeamMemberResponseDto convertToDto(TeamMember entity) {
        User user = entity.getUser();
        UserProfile profile = user.getUserProfile();
        
        return TeamMemberResponseDto.builder()
                .id(entity.getId())
                .teamId(entity.getTeam().getId())
                .userId(user.getId())
                .email(user.getEmail())
                .role(entity.getRole())  // Thêm thông tin role
                .joinedAt(entity.getJoinedAt())
                .aboutMe(profile != null ? profile.getAboutMe() : null)
                .department(profile != null ? profile.getDepartment() : null)
                .jobTitle(profile != null ? profile.getJobTitle() : null)
                .avatarUrl(profile != null ? profile.getAvtUrl() : null)
                .firstName(profile != null ? profile.getFirstName() : null)
                .lastName(profile != null ? profile.getLastName() : null)
                .build();
    }


}
