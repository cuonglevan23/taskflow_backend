package com.example.taskmanagement_backend.repositories;

import com.example.taskmanagement_backend.entities.CalendarIntegration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CalendarIntegrationJpaRepository extends JpaRepository<CalendarIntegration, Long> {
    List<CalendarIntegration> findByUserId(Long userId);

}
