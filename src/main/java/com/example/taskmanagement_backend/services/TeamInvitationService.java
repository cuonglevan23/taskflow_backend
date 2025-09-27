package com.example.taskmanagement_backend.services;

import com.example.taskmanagement_backend.dtos.ProjectInvitatinDto.ProjectInvitationResponseDto;
import com.example.taskmanagement_backend.dtos.ProjectInvitatinDto.UpdateProjectInvitationStatusRequestDto;
import com.example.taskmanagement_backend.dtos.TeamInvitationDto.CreateTeamInvitationRequestDto;
import com.example.taskmanagement_backend.dtos.TeamInvitationDto.TeamInvitationResponseDto;
import com.example.taskmanagement_backend.dtos.TeamInvitationDto.UpdateTeamInvitationStatusRequestDto;
import com.example.taskmanagement_backend.entities.*;
import com.example.taskmanagement_backend.enums.InvitationStatus;
import com.example.taskmanagement_backend.repositories.*;
import com.example.taskmanagement_backend.services.infrastructure.ConcurrentTaskService;
import com.example.taskmanagement_backend.services.infrastructure.EmailService;
import jakarta.mail.MessagingException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamInvitationService {
    private final TeamInvatationJpaRepository teamInvatationJpaRepository;
    private final TeamJpaRepository teamJpaRepository;
    private final TeamMemberJpaRepository teamMemberJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final RoleJpaRepository roleJpaRepository;
    private final EmailService emailService;
    private final ConcurrentTaskService concurrentTaskService;

    public TeamInvitationResponseDto createTeamInvitation(CreateTeamInvitationRequestDto dto) {
        Team team = teamJpaRepository.findById(dto.getTeamId())
                .orElseThrow(() -> new EntityNotFoundException("Team không tồn tại"));
        User invitedBy = userJpaRepository.findById(dto.getInvitedById())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        User userInvite = userJpaRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new EntityNotFoundException("User được mời không tồn tại"));
        TeamInvitation invitation = TeamInvitation.builder()
                .email(dto.getEmail())
                .team(team)
                .invitedBy(invitedBy)
                .status(InvitationStatus.PENDING)
                .token(java.util.UUID.randomUUID().toString())
                .createdAt(LocalDateTime.now())
                .build();
            teamInvatationJpaRepository.save(invitation);
        //create Mail Link
        String inviteLink = "http://localhost:8080/api/invitations/accept-team?token=" + invitation.getToken();

        //Gui mail dong bo
        concurrentTaskService.executeTask(() -> {
            try {
                emailService.sendInvitationEmail(
                        dto.getEmail(),
                        invitation.getTeam().getName(),
                        inviteLink
                );
            } catch (MessagingException e) {
                e.printStackTrace();
            }
        });
        return toDto(invitation);
    }
    public String acceptInvitation(String token) {
        TeamInvitation invitation = teamInvatationJpaRepository.findByToken(token)
                .orElseThrow(() -> new EntityNotFoundException("Token không hợp lệ"));

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new IllegalStateException("Lời mời đã hết hạn hoặc đã xử lý trước đó");
        }

        // Tìm user theo email
        User user = userJpaRepository.findByEmail(invitation.getEmail())
                .orElseThrow(() -> new EntityNotFoundException("Người dùng không tồn tại"));

        // Cập nhật status
        invitation.setStatus(InvitationStatus.ACCEPTED);
        teamInvatationJpaRepository.save(invitation);

        // Thêm vào bảng project_members
        TeamMember member = TeamMember.builder()
                .team(invitation.getTeam())
                .user(user)
                .joinedAt(LocalDateTime.now())
                .build();

        teamMemberJpaRepository.save(member);

        return "Lời mời đã được chấp nhận";
    }
    public List<TeamInvitationResponseDto> getTeamInvitationsByTeam(Long teamId) {
        return teamInvatationJpaRepository.findByTeamId(teamId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }
    public TeamInvitationResponseDto updateStatus(Long invitationId, UpdateTeamInvitationStatusRequestDto dto) {
        TeamInvitation invitation = teamInvatationJpaRepository.findById(invitationId)
                .orElseThrow(() -> new EntityNotFoundException("Invitation không tồn tại"));

        invitation.setStatus(dto.getStatus());
        teamInvatationJpaRepository.save(invitation);

        return toDto(invitation);
    }
    private TeamInvitationResponseDto toDto(TeamInvitation entity) {
        return TeamInvitationResponseDto.builder()
                .id(entity.getId())
                .email(entity.getEmail())
                .teamId(entity.getTeam().getId())
                .teamName(entity.getTeam().getName())
                .invitedById(entity.getInvitedBy().getId())
                .invitedByName(entity.getInvitedBy().getUserProfile().getFirstName() + entity.getInvitedBy().getUserProfile().getLastName())
                .status(entity.getStatus())
                .token(entity.getToken())
                .createdAt(entity.getCreatedAt())
                .build();
    }

}
