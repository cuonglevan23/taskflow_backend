package com.example.taskmanagement_backend.controllers;
    import com.example.taskmanagement_backend.dtos.UserDto.CreateUserRequestDto;
    import com.example.taskmanagement_backend.dtos.UserDto.UpdateUserRequestDto;
    import com.example.taskmanagement_backend.dtos.UserDto.UserResponseDto;
    import com.example.taskmanagement_backend.dtos.TeamDto.TeamResponseDto;
    import com.example.taskmanagement_backend.dtos.ProjectDto.ProjectResponseDto;

    import com.example.taskmanagement_backend.entities.User;
    import com.example.taskmanagement_backend.services.UserService;
    import com.example.taskmanagement_backend.services.TeamService;
    import com.example.taskmanagement_backend.services.ProjectService;
    import jakarta.validation.Valid;
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.http.ResponseEntity;
    import org.springframework.security.access.prepost.PreAuthorize;
    import org.springframework.security.core.Authentication;
    import org.springframework.security.core.context.SecurityContextHolder;
    import org.springframework.security.core.userdetails.UserDetails;
    import org.springframework.web.bind.annotation.*;

    import java.util.List;
@CrossOrigin(origins = {"https://main.d2az19adxqfdf3.amplifyapp.com", "https://main.d4nz8d2yz1imm.amplifyapp.com", "http://localhost:3000", "http://localhost:5173"}, allowCredentials = "true")
    @RestController
    @RequestMapping("/api/users")
    public class UserController {

        @Autowired
        private UserService userService;
        
        @Autowired
        private TeamService teamService;
        
        @Autowired
        private ProjectService projectService;

        @Autowired
        public UserController(UserService userService, TeamService teamService, ProjectService projectService) {
            this.userService = userService;
            this.teamService = teamService;
            this.projectService = projectService;
        }

//        @PreAuthorize("hasAnyRole('owner')")
        @PostMapping
        public ResponseEntity<UserResponseDto> createUser(@Valid @RequestBody CreateUserRequestDto dto) {
            return ResponseEntity.ok(userService.createUser(dto));
        }

        @PutMapping("/{id}")
        public ResponseEntity<UserResponseDto> updateUser(@PathVariable Long id, @RequestBody UpdateUserRequestDto dto) {
            return ResponseEntity.ok(userService.updateUser(id, dto));
        }
        @DeleteMapping("/{id}")
        public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
            userService.deleteUser((long) Math.toIntExact(id));
            return ResponseEntity.noContent().build();
        }
        @GetMapping("/{id}")
        public ResponseEntity<UserResponseDto> getUserById(@PathVariable Long id) {
            return ResponseEntity.ok(userService.getUserById(id));
        }
    //    @PutMapping("/{id}")
    //    public User updateUser(@PathVariable Integer id, @Valid @RequestBody UpdateUserRequestDto dto) {
    //        return userService.updateUser(id, dto);
    //    }

//        @GetMapping("/{id}")
//        public UserResponseDto getUserById(@PathVariable Integer id) {
//            return userService.getUserById(id);
//        }

        @GetMapping("/by-email")
        public ResponseEntity<UserResponseDto> getUserByEmail(@RequestParam("email") String email) {
            return ResponseEntity.ok(userService.getUserByEmail(email));
        }
//        @PreAuthorize("hasAnyRole('owner', 'pm', 'leader')")
        @GetMapping
        public ResponseEntity<List<UserResponseDto>> getAllUsers() {
            return ResponseEntity.ok(userService.getAllUsers());
        }

        // ===== SENIOR LEVEL ENDPOINTS =====
        
        /**
         * Get all teams that a user either created or joined as a member
         * Supports both current user access and admin/owner access to other users
         * 
         * @param id The user ID to get teams for
         * @return List of teams the user is associated with
         */
        @GetMapping("/{id}/teams")
        @PreAuthorize("authentication.principal.username == @userService.getUserById(#id).email or hasAnyRole('OWNER', 'ADMIN')")
        public ResponseEntity<List<TeamResponseDto>> getUserTeams(@PathVariable Long id) {
            try {
                List<TeamResponseDto> teams = teamService.getTeamsByUserId(id);
                return ResponseEntity.ok(teams);
            } catch (RuntimeException e) {
                return ResponseEntity.notFound().build();
            }
        }

        /**
         * Get all projects that a user either created, owns or joined as a member
         * Supports both current user access and admin/owner access to other users
         * 
         * @param id The user ID to get projects for
         * @return List of projects the user is associated with
         */
        @GetMapping("/{id}/projects")
        @PreAuthorize("authentication.principal.username == @userService.getUserById(#id).email or hasAnyRole('OWNER', 'ADMIN')")
        public ResponseEntity<List<ProjectResponseDto>> getUserProjects(@PathVariable Long id) {
            try {
                List<ProjectResponseDto> projects = projectService.getProjectsByUserId(id);
                return ResponseEntity.ok(projects);
            } catch (RuntimeException e) {
                return ResponseEntity.notFound().build();
            }
        }

        /**
         * Get teams created by current authenticated user
         * Convenience endpoint for getting own created teams
         * 
         * @return List of teams created by the current user
         */
        @GetMapping("/me/teams")
        public ResponseEntity<List<TeamResponseDto>> getCurrentUserTeams() {
            try {
                Long currentUserId = getCurrentUserId();
                List<TeamResponseDto> teams = teamService.getTeamsByUserId(currentUserId);
                return ResponseEntity.ok(teams);
            } catch (RuntimeException e) {
                return ResponseEntity.badRequest().build();
            }
        }

        /**
         * Get projects created/owned/joined by current authenticated user
         * Convenience endpoint for getting own projects
         * 
         * @return List of projects associated with the current user
         */
        @GetMapping("/me/projects")
        public ResponseEntity<List<ProjectResponseDto>> getCurrentUserProjects() {
            try {
                Long currentUserId = getCurrentUserId();
                List<ProjectResponseDto> projects = projectService.getProjectsByUserId(currentUserId);
                return ResponseEntity.ok(projects);
            } catch (RuntimeException e) {
                return ResponseEntity.badRequest().build();
            }
        }

        /**
         * Legacy support for client code using the params[page] and params[size] format.
         * This method extracts the actual values and delegates to the proper paginated endpoint.
         * Note: This is a temporary fix - clients should update to use /me/projects/paginated
         */
        @GetMapping(value = "/me/projects", params = {"params.page", "params.size"})
        public ResponseEntity<?> getCurrentUserProjectsLegacyPagination(
                @RequestParam(value = "params.page", defaultValue = "0") int page,
                @RequestParam(value = "params.size", defaultValue = "20") int size) {
            try {
                Long currentUserId = getCurrentUserId();
                return ResponseEntity.ok(projectService.getProjectsByUserIdPaginated(currentUserId, page, size));
            } catch (RuntimeException e) {
                return ResponseEntity.badRequest().body(e.getMessage());
            }
        }

        /**
         * Get paginated projects created/owned/joined by current authenticated user
         * Supports proper pagination with page and size parameters
         *
         * @param page The page number (0-based)
         * @param size The page size
         * @return Paginated list of projects associated with the current user
         */
        @GetMapping("/me/projects/paginated")
        public ResponseEntity<?> getCurrentUserProjectsPaginated(
                @RequestParam(defaultValue = "0") int page,
                @RequestParam(defaultValue = "20") int size) {
            try {
                Long currentUserId = getCurrentUserId();
                return ResponseEntity.ok(projectService.getProjectsByUserIdPaginated(currentUserId, page, size));
            } catch (RuntimeException e) {
                return ResponseEntity.badRequest().body(e.getMessage());
            }
        }

        /**
         * Get teams created by a specific user (only created, not joined)
         * Admin/Owner can access any user, regular users can only access their own
         * 
         * @param id The user ID
         * @return List of teams created by the user
         */
        @GetMapping("/{id}/teams/created")
        @PreAuthorize("authentication.principal.username == @userService.getUserById(#id).email or hasAnyRole('OWNER', 'ADMIN')")
        public ResponseEntity<List<TeamResponseDto>> getUserCreatedTeams(@PathVariable Long id) {
            try {
                List<TeamResponseDto> teams = teamService.getTeamsCreatedByUser(id);
                return ResponseEntity.ok(teams);
            } catch (RuntimeException e) {
                return ResponseEntity.notFound().build();
            }
        }

        /**
         * Get projects created by a specific user (only created, not owned/joined)
         * Admin/Owner can access any user, regular users can only access their own
         * 
         * @param id The user ID
         * @return List of projects created by the user
         */
        @GetMapping("/{id}/projects/created")
        @PreAuthorize("authentication.principal.username == @userService.getUserById(#id).email or hasAnyRole('OWNER', 'ADMIN')")
        public ResponseEntity<List<ProjectResponseDto>> getUserCreatedProjects(@PathVariable Long id) {
            try {
                List<ProjectResponseDto> projects = projectService.getProjectsCreatedByUser(id);
                return ResponseEntity.ok(projects);
            } catch (RuntimeException e) {
                return ResponseEntity.notFound().build();
            }
        }

        /**
         * Get projects owned by a specific user
         * Admin/Owner can access any user, regular users can only access their own
         * 
         * @param id The user ID
         * @return List of projects owned by the user
         */
        @GetMapping("/{id}/projects/owned")
        @PreAuthorize("authentication.principal.username == @userService.getUserById(#id).email or hasAnyRole('OWNER', 'ADMIN')")
        public ResponseEntity<List<ProjectResponseDto>> getUserOwnedProjects(@PathVariable Long id) {
            try {
                List<ProjectResponseDto> projects = projectService.getProjectsOwnedByUser(id);
                return ResponseEntity.ok(projects);
            } catch (RuntimeException e) {
                return ResponseEntity.notFound().build();
            }
        }

        // ===== HELPER METHODS =====
        
        /**
         * Get current authenticated user's ID
         * @return Current user ID
         * @throws RuntimeException if user not authenticated or not found
         */
        private Long getCurrentUserId() {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                throw new RuntimeException("User not authenticated");
            }
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            UserResponseDto currentUser = userService.getUserByEmail(userDetails.getUsername());
            return currentUser.getId();
        }
    }
