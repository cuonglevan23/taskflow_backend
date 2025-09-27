    package com.example.taskmanagement_backend.entities;

    import jakarta.persistence.*;
    import lombok.AllArgsConstructor;
    import lombok.Builder;
    import lombok.Data;
    import lombok.NoArgsConstructor;

    import java.util.HashSet;
    import java.util.Set;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Entity
    @Table(name = "permissions")
    public class Permission {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private String name;

        @ManyToMany(mappedBy = "permissions")
        @Builder.Default
        private Set<Role> roles = new HashSet<>();

    }