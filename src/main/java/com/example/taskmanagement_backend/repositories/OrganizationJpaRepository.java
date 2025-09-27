package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.Organization;
import com.example.taskmanagement_backend.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrganizationJpaRepository extends JpaRepository<Organization, Long> {



    /**
     * Find organization by email domain
     * @param emailDomain the domain part of email (e.g., "company.com")
     * @return Optional<Organization>
     */
    Optional<Organization> findByEmailDomain(String emailDomain);
    Optional<Organization> findByOwnerId(Long ownerId);

}