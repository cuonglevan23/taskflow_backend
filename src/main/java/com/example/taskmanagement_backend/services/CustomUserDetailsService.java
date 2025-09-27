package com.example.taskmanagement_backend.services;


import java.util.ArrayList;
import java.util.List;

import com.example.taskmanagement_backend.entities.User;
import com.example.taskmanagement_backend.repositories.UserJpaRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;



@Service
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final UserJpaRepository userRepository;

    public CustomUserDetailsService(UserJpaRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        log.debug("🔍 Loading user by email: {}", email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + email));

        List<GrantedAuthority> authorities = new ArrayList<>();

        // Handle SystemRole enum (single role per user)
        if (user.getSystemRole() != null) {
            String roleName = user.getSystemRole().name();
            // Add both formats for compatibility
            authorities.add(new SimpleGrantedAuthority(roleName));
            authorities.add(new SimpleGrantedAuthority("ROLE_" + roleName));

            log.debug("✅ Added role: {} and ROLE_{} for user: {}", roleName, roleName, email);
        }

        log.debug("✅ Successfully loaded user: {} with role: {}", email, user.getSystemRole());

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPassword())
                .authorities(authorities)
                .build();
    }
}