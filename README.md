

## API Documentation & Implementation Status

### Authentication & OAuth2 APIs
| Method | Endpoint | Description | Status |
|--------|----------|-------------|--------|
| `POST` | `/api/auth/login` | Traditional email/password login | ✅ (Done) |
| `POST` | `/api/auth/refresh` | Refresh access token | ✅ (Done) |
| `POST` | `/api/auth/logout` | Logout and revoke tokens | ✅ (Done) |
| `GET` | `/api/auth/google/url` | Get Google OAuth2 authorization URL | ✅ (Done) |
| `GET` | `/api/auth/google/callback` | Handle OAuth2 callback (redirect) | ✅ (Done) |
| `POST` | `/api/auth/google/callback` | Handle OAuth2 callback (API) | ✅ (Done) |
| `GET` | `/api/auth/google/validate` | Validate OAuth2 state | ✅ (Done) |
| `GET` | `/api/auth/me` | Get current user info | ❌ (Planned) |

### Task Management APIs
| Method | Endpoint | Description | Status |
|--------|----------|-------------|--------|
| `GET` | `/api/tasks` | Get all tasks | ✅ (Done) |
| `POST` | `/api/tasks` | Create new task | ✅ (Done) |
| `GET` | `/api/tasks/{id}` | Get task by ID | ✅ (Done) |
| `PUT` | `/api/tasks/{id}` | Update task | ✅ (Done) |
| `DELETE` | `/api/tasks/{id}` | Delete task | ✅ (Done) |
| `PATCH` | `/api/tasks/{id}/status` | Update task status | ❌ (Planned) |
| `PATCH` | `/api/tasks/{id}/assign` | Assign task to user | ❌ (Planned) |
| `GET` | `/api/tasks/project/{projectId}` | Get tasks by project | ❌ (Planned) |
| `GET` | `/api/tasks/stats` | Get task statistics | ❌ (Planned) |
| `PATCH` | `/api/tasks/bulk-update` | Bulk update tasks | ❌ (Planned) |

### Task Checklist APIs
| Method | Endpoint | Description | Status |
|--------|----------|-------------|--------|
| `POST` | `/api/task-checklists` | Create task checklist | ✅ (Done) |
| `PUT` | `/api/task-checklists/{id}` | Update checklist | ✅ (Done) |
| `GET` | `/api/task-checklists/{id}` | Get checklist by ID | ✅ (Done) |
| `GET` | `/api/task-checklists` | Get all checklists | ✅ (Done) |
| `DELETE` | `/api/task-checklists/{id}` | Delete checklist | ✅ (Done) |
| `GET` | `/api/task-checklists/task/{taskId}` | Get checklists by task | ✅ (Done) |

### Task Attachment APIs
| Method | Endpoint | Description | Status |
|--------|----------|-------------|--------|
| `POST` | `/api/task-attachments` | Upload task attachment | ✅ (Done) |
| `PUT` | `/api/task-attachments/{id}` | Update attachment | ✅ (Done) |
| `GET` | `/api/task-attachments` | Get all attachments | ✅ (Done) |
| `GET` | `/api/task-attachments/{id}` | Get attachment by ID | ✅ (Done) |
| `DELETE` | `/api/task-attachments/{id}` | Delete attachment | ✅ (Done) |
| `GET` | `/api/task-attachments/task/{taskId}` | Get attachments by task | ✅ (Done) |

### User Management APIs
| Method | Endpoint | Description | Status |
|--------|----------|-------------|--------|
| `POST` | `/api/users` | Create new user | ✅ (Done) |
| `PUT` | `/api/users/{id}` | Update user | ✅ (Done) |
| `DELETE` | `/api/users/{id}` | Delete user | ✅ (Done) |
| `GET` | `/api/users/{id}` | Get user by ID | ✅ (Done) |
| `GET` | `/api/users/by-email` | Get user by email | ✅ (Done) |
| `GET` | `/api/users` | Get all users | ✅ (Done) |
| `PATCH` | `/api/users/{id}/role` | Update user role | ❌ (Planned) |
| `PATCH` | `/api/users/{id}/status` | Update user status | ❌ (Planned) |

### User Profile APIs
| Method | Endpoint | Description | Status |
|--------|----------|-------------|--------|
| `GET` | `/api/user-profiles/{id}` | Get user profile | ✅ (Done) |
| `PUT` | `/api/user-profiles/{id}` | Update user profile | ✅ (Done) |
| `POST` | `/api/user-profiles` | Create user profile | ❌ (Planned) |
| `DELETE` | `/api/user-profiles/{id}` | Delete user profile | ❌ (Planned) |

### Organization Management APIs
| Method | Endpoint | Description | Status |
|--------|----------|-------------|--------|
| `GET` | `/api/organizations` | Get all organizations | ✅ (Done) |
| `GET` | `/api/organizations/{id}` | Get organization by ID | ✅ (Done) |
| `POST` | `/api/organizations` | Create organization | ✅ (Done) |
| `PUT` | `/api/organizations/{id}` | Update organization | ✅ (Done) |
| `DELETE` | `/api/organizations/{id}` | Delete organization | ✅ (Done) |
| `GET` | `/api/organizations/{id}/users` | Get organization users | ❌ (Planned) |
| `POST` | `/api/organizations/{id}/invite` | Invite user to organization | ❌ (Planned) |

### 📊 Project Management APIs
| Method | Endpoint | Description | Status |
|--------|----------|-------------|--------|
| `GET` | `/api/projects` | Get all projects | ✅ (Done) |
| `GET` | `/api/projects/{id}` | Get project by ID | ✅ (Done) |
| `POST` | `/api/projects` | Create project | ✅ (Done) |
| `PUT` | `/api/projects/{id}` | Update project | ✅ (Done) |
| `DELETE` | `/api/projects/{id}` | Delete project | ✅ (Done) |
| `GET` | `/api/projects/{id}/tasks` | Get project tasks | ❌ (Planned) |
| `GET` | `/api/projects/{id}/members` | Get project members | ❌ (Planned) |
| `POST` | `/api/projects/{id}/members` | Add project member | ❌ (Planned) |

### Team Management APIs
| Method | Endpoint | Description | Status |
|--------|----------|-------------|--------|
| `GET` | `/api/teams` | Get all teams | ✅ (Done) |
| `GET` | `/api/teams/{id}` | Get team by ID | ✅ (Done) |
| `POST` | `/api/teams` | Create team | ✅ (Done) |
| `PUT` | `/api/teams/{id}` | Update team | ✅ (Done) |
| `DELETE` | `/api/teams/{id}` | Delete team | ✅ (Done) |
| `GET` | `/api/teams/{id}/members` | Get team members | ❌ (Planned) |
| `POST` | `/api/teams/{id}/members` | Add team member | ❌ (Planned) |
| `DELETE` | `/api/teams/{id}/members/{userId}` | Remove team member | ❌ (Planned) |

### Calendar Integration APIs
| Method | Endpoint | Description | Status |
|--------|----------|-------------|--------|
| `POST` | `/api/calendar-integrations` | Create calendar integration | ✅ (Done) |
| `PUT` | `/api/calendar-integrations/{id}` | Update integration | ✅ (Done) |
| `GET` | `/api/calendar-integrations/{id}` | Get integration by ID | ✅ (Done) |
| `GET` | `/api/calendar-integrations` | Get all integrations | ✅ (Done) |
| `DELETE` | `/api/calendar-integrations/{id}` | Delete integration | ✅ (Done) |
| `GET` | `/api/calendar-integrations/user/{userId}` | Get user integrations | ✅ (Done) |
| `POST` | `/api/calendar-integrations/sync` | Sync with external calendar | ❌ (Planned) |

### Analytics & Reporting APIs
| Method | Endpoint | Description | Status |
|--------|----------|-------------|--------|
| `GET` | `/api/analytics/tasks/stats` | Get task statistics | ❌ (Planned) |
| `GET` | `/api/analytics/projects/stats` | Get project statistics | ❌ (Planned) |
| `GET` | `/api/analytics/users/activity` | Get user activity | ❌ (Planned) |
| `GET` | `/api/analytics/dashboard` | Get dashboard data | ❌ (Planned) |

###  Token Management APIs
| Method | Endpoint | Description | Status |
|--------|----------|-------------|--------|
| `GET` | `/api/tokens/stats` | Get token statistics | ✅ (Done) |
| `POST` | `/api/tokens/cleanup/expired` | Cleanup expired tokens | ✅ (Done) |
| `POST` | `/api/tokens/cleanup/old` | Cleanup old tokens | ✅ (Done) |
| `POST` | `/api/tokens/limit-per-user` | Limit tokens per user | ✅ (Done) |
| `GET` | `/api/tokens/health` | Token system health check | ✅ (Done) |

### Audit Log APIs
| Method | Endpoint | Description | Status |
|--------|----------|-------------|--------|
| `POST` | `/api/audit-logs` | Create audit log | ✅ (Done) |
| `GET` | `/api/audit-logs` | Get all audit logs | ✅ (Done) |
| `GET` | `/api/audit-logs/{id}` | Get audit log by ID | ✅ (Done) |
| `DELETE` | `/api/audit-logs/{id}` | Delete audit log | ✅ (Done) |
| `GET` | `/api/audit-logs/user/{userId}` | Get user audit logs | ✅ (Done) |

###  Planned Future APIs
| Method | Endpoint | Description | Status |
|--------|----------|-------------|--------|
| `GET` | `/api/notifications` | Get user notifications | ❌ (Planned) |
| `POST` | `/api/notifications/mark-read` | Mark notifications as read | ❌ (Planned) |
| `GET` | `/api/search/tasks` | Search tasks | ❌ (Planned) |
| `GET` | `/api/search/projects` | Search projects | ❌ (Planned) |
| `POST` | `/api/tasks/{id}/comments` | Add task comment | ❌ (Planned) |
| `GET` | `/api/tasks/{id}/comments` | Get task comments | ❌ (Planned) |
| `POST` | `/api/tasks/{id}/time-tracking` | Log time on task | ❌ (Planned) |
| `GET` | `/api/reports/time-tracking` | Time tracking reports | ❌ (Planned) |
| `POST` | `/api/integrations/slack` | Slack integration | ❌ (Planned) |
| `POST` | `/api/integrations/jira` | JIRA integration | ❌ (Planned) |





