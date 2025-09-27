-- Xóa dữ liệu cũ trong bảng
DELETE FROM roles_permissions;
DELETE FROM users_roles;
DELETE FROM roles;
DELETE FROM permissions;

-- Thêm lại permissions mới
INSERT INTO permissions (name) VALUES
('VIEW_USERS'),
('CREATE_USER'),
('UPDATE_USER'),
('DELETE_USER'),
('VIEW_SETTINGS'),
('UPDATE_SETTINGS'),
('VIEW_TEAM'),
('CREATE_TEAM'),
('UPDATE_TEAM'),
('DELETE_TEAM'),
('MANAGE_TEAM_MEMBERS'),
('VIEW_PROJECT'),
('CREATE_PROJECT'),
('UPDATE_PROJECT'),
('DELETE_PROJECT'),
('MANAGE_PROJECT_MEMBERS'),
('VIEW_TASK'),
('CREATE_TASK'),
('UPDATE_TASK'),
('DELETE_TASK'),
('MANAGE_TASK_ASSIGNEES');

-- Thêm roles mới theo cấu trúc phân quyền mới
-- SYSTEM ROLES
INSERT INTO roles (name) VALUES ('ROLE_ADMIN'), ('ROLE_MEMBER');

-- TEAM ROLES  
INSERT INTO roles (name) VALUES ('ROLE_TEAM_OWNER'), ('ROLE_TEAM_MEMBER');

-- PROJECT ROLES
INSERT INTO roles (name) VALUES ('ROLE_PROJECT_OWNER'), ('ROLE_PROJECT_MEMBER');

-- Gán permissions cho ADMIN
INSERT INTO roles_permissions (role_id, permission_id)
SELECT r.id, p.id 
FROM roles r 
CROSS JOIN permissions p
WHERE r.name = 'ROLE_ADMIN';

-- Gán permissions cho MEMBER
INSERT INTO roles_permissions (role_id, permission_id)
SELECT r.id, p.id 
FROM roles r 
CROSS JOIN permissions p
WHERE r.name = 'ROLE_MEMBER'
AND p.name IN (
  'VIEW_TEAM',
  'VIEW_PROJECT',
  'VIEW_TASK',
  'CREATE_TASK',
  'UPDATE_TASK'
);

-- Gán permissions cho TEAM_OWNER
INSERT INTO roles_permissions (role_id, permission_id)
SELECT r.id, p.id 
FROM roles r 
CROSS JOIN permissions p
WHERE r.name = 'ROLE_TEAM_OWNER'
AND p.name IN (
  'VIEW_TEAM',
  'UPDATE_TEAM', 
  'DELETE_TEAM',
  'MANAGE_TEAM_MEMBERS',
  'CREATE_PROJECT',
  'VIEW_PROJECT',
  'UPDATE_PROJECT',
  'DELETE_PROJECT',
  'MANAGE_PROJECT_MEMBERS',
  'VIEW_TASK',
  'CREATE_TASK',
  'UPDATE_TASK',
  'DELETE_TASK',
  'MANAGE_TASK_ASSIGNEES'
);

-- Gán permissions cho TEAM_MEMBER
INSERT INTO roles_permissions (role_id, permission_id)
SELECT r.id, p.id 
FROM roles r 
CROSS JOIN permissions p
WHERE r.name = 'ROLE_TEAM_MEMBER'
AND p.name IN (
  'VIEW_TEAM',
  'VIEW_PROJECT',
  'VIEW_TASK',
  'CREATE_TASK',
  'UPDATE_TASK'
);

-- Gán permissions cho PROJECT_OWNER
INSERT INTO roles_permissions (role_id, permission_id)
SELECT r.id, p.id 
FROM roles r 
CROSS JOIN permissions p
WHERE r.name = 'ROLE_PROJECT_OWNER'
AND p.name IN (
  'VIEW_PROJECT',
  'UPDATE_PROJECT',
  'DELETE_PROJECT', 
  'MANAGE_PROJECT_MEMBERS',
  'VIEW_TASK',
  'CREATE_TASK',
  'UPDATE_TASK',
  'DELETE_TASK',
  'MANAGE_TASK_ASSIGNEES'
);

-- Gán permissions cho PROJECT_MEMBER
INSERT INTO roles_permissions (role_id, permission_id)
SELECT r.id, p.id 
FROM roles r 
CROSS JOIN permissions p
WHERE r.name = 'ROLE_PROJECT_MEMBER'
AND p.name IN (
  'VIEW_PROJECT',
  'VIEW_TASK',
  'CREATE_TASK',
  'UPDATE_TASK'
);
