package com.example.taskmanagement_backend.entities;


import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "organization_invitations")
public class OrganizationInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;

    @ManyToOne
    @JoinColumn(name = "organization_id", foreignKey = @ForeignKey(name = "fk_invitation_organization"))
    private Organization organization;

    @ManyToOne
    @JoinColumn(name = "invited_by", foreignKey = @ForeignKey(name = "fk_invitation_user"))
    private User invitedBy;

    private String status;

    private String token;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}