package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.Role;
import com.example.taskmanagement_backend.entities.User;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface RoleJpaRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(String name);

//    List<Role> findAllById(@NotNull List<Long> roleIds);

}