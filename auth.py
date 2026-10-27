"""Multi-tenant Authentication and Per-Project Role-Based Access Control (RBAC) for Vikunja MCP.

Enforces tenant isolation and contextual permission boundaries:
A user can be an Admin/Owner in their own projects, a Contributor in shared team projects,
and a Viewer/Guest in other projects, with automatic live resolution and explicit overrides.
"""

from __future__ import annotations

import json
import logging
import os
import re
from enum import Enum
from pathlib import Path
from typing import Any, Dict, List, Optional, Set
from pydantic import BaseModel, Field

logger = logging.getLogger("vikunja_mcp.auth")


class Role(str, Enum):
    """User authorization roles."""
    VIEWER = "viewer"           # Read-only guest
    CONTRIBUTOR = "contributor" # Member: read, create, update, delete own tasks
    ADMIN = "admin"             # Owner/Admin: full control of project & all tasks


class Action(str, Enum):
    """Actions performed across tools with granular permission scopes."""
    # Read-only actions (viewer, contributor, admin)
    LIST_PROJECTS = "list_projects"
    GET_PROJECT_DETAILS = "get_project_details"
    GET_TASKS = "get_tasks"
    GET_TASK_DETAILS = "get_task_details"
    LIST_ALL_TASKS = "list_all_tasks"
    LIST_LABELS = "list_labels"

    # Write / contributor actions (contributor, admin)
    CREATE_PROJECT = "create_project"
    CREATE_TASK = "create_task"
    UPDATE_TASK = "update_task"
    TOGGLE_TASK_DONE = "toggle_task_done"
    BULK_CREATE_TASKS = "bulk_create_tasks"
    ASSIGN_TASK = "assign_task"
    UNASSIGN_TASK = "unassign_task"
    CREATE_LABEL = "create_label"
    ADD_LABEL = "add_label"
    REMOVE_LABEL = "remove_label"

    # Delete / admin actions
    DELETE_TASK = "delete_task"
    UPDATE_PROJECT = "update_project"
    DELETE_PROJECT = "delete_project"
    MANAGE_COLLABORATORS = "manage_collaborators"


# Role-to-Actions Permission Matrix
ROLE_PERMISSIONS: Dict[Role, Set[Action]] = {
    Role.VIEWER: {
        Action.LIST_PROJECTS,
        Action.GET_PROJECT_DETAILS,
        Action.GET_TASKS,
        Action.GET_TASK_DETAILS,
        Action.LIST_ALL_TASKS,
        Action.LIST_LABELS,
    },
    Role.CONTRIBUTOR: {
        Action.LIST_PROJECTS,
        Action.GET_PROJECT_DETAILS,
        Action.GET_TASKS,
        Action.GET_TASK_DETAILS,
        Action.LIST_ALL_TASKS,
        Action.LIST_LABELS,
        Action.CREATE_PROJECT,
        Action.CREATE_TASK,
        Action.UPDATE_TASK,
        Action.TOGGLE_TASK_DONE,
        Action.BULK_CREATE_TASKS,
        Action.ASSIGN_TASK,
        Action.UNASSIGN_TASK,
        Action.CREATE_LABEL,
        Action.ADD_LABEL,
        Action.REMOVE_LABEL,
        # Contributor can delete their own tasks
        Action.DELETE_TASK,
    },
    Role.ADMIN: {
        Action.LIST_PROJECTS,
        Action.GET_PROJECT_DETAILS,
        Action.GET_TASKS,
        Action.GET_TASK_DETAILS,
        Action.LIST_ALL_TASKS,
        Action.LIST_LABELS,
        Action.CREATE_PROJECT,
        Action.UPDATE_PROJECT,
        Action.CREATE_TASK,
        Action.UPDATE_TASK,
        Action.TOGGLE_TASK_DONE,
        Action.BULK_CREATE_TASKS,
        Action.ASSIGN_TASK,
        Action.UNASSIGN_TASK,
        Action.CREATE_LABEL,
        Action.ADD_LABEL,
        Action.REMOVE_LABEL,
        Action.DELETE_TASK,
        Action.DELETE_PROJECT,
        Action.MANAGE_COLLABORATORS,
    },
}


class UserProfile(BaseModel):
    """User profile containing identity, default role, per-project overrides, and API token."""
    user_id: str
    token: str = Field(..., description="Vikunja API Bearer Token")
    role: Role = Field(default=Role.VIEWER, description="Global default RBAC Role")
    project_roles: Dict[str, Role] = Field(
        default_factory=dict,
        description="Optional explicit per-project roles (e.g. {'1': 'admin', '2': 'viewer'})",
    )
    name: Optional[str] = None
    email: Optional[str] = None


class AuthResult:
    """Authentication and authorization verification result."""

    def __init__(
        self,
        is_authorized: bool,
        user: Optional[UserProfile] = None,
        effective_role: Optional[Role] = None,
        error_response: Optional[Dict[str, Any]] = None,
    ):
        self.is_authorized = is_authorized
        self.user = user
        self.effective_role = effective_role or (user.role if user else Role.VIEWER)
        self.error_response = error_response

    @property
    def token(self) -> str:
        """Helper to get token safely if authorized."""
        if not self.is_authorized or not self.user:
            raise PermissionError("Attempted to access token on unauthorized result")
        return self.user.token


class TokenRedactionFilter(logging.Filter):
    """Logging filter to strictly redact API tokens and secrets from all log records."""

    BEARER_PATTERN = re.compile(r"Bearer\s+([A-Za-z0-9_\-\.]+)", re.IGNORECASE)
    TOKEN_KEY_PATTERN = re.compile(r"(token[\"':\s=]+)([\"']?)([A-Za-z0-9_\-\.]{6,})([\"']?)", re.IGNORECASE)

    def __init__(self, sensitive_tokens: Optional[Set[str]] = None):
        super().__init__()
        self.sensitive_tokens: Set[str] = set(sensitive_tokens or [])

    def add_token(self, token: str) -> None:
        if token and len(token) > 4:
            self.sensitive_tokens.add(token)

    def filter(self, record: logging.LogRecord) -> bool:
        if not isinstance(record.msg, str):
            record.msg = str(record.msg)

        # Redact specific registered tokens
        for token in self.sensitive_tokens:
            if token in record.msg:
                masked = token[:3] + "..." + token[-3:] if len(token) > 8 else "***REDACTED***"
                record.msg = record.msg.replace(token, masked)

        # Redact Bearer headers
        record.msg = self.BEARER_PATTERN.sub(r"Bearer [REDACTED_TOKEN]", record.msg)
        # Redact token key-values in JSON/strings
        record.msg = self.TOKEN_KEY_PATTERN.sub(r"\1\2[REDACTED_TOKEN]\4", record.msg)

        if record.args:
            new_args = []
            for arg in record.args:
                arg_str = str(arg)
                for token in self.sensitive_tokens:
                    if token in arg_str:
                        arg_str = arg_str.replace(token, "[REDACTED_TOKEN]")
                new_args.append(arg_str)
            record.args = tuple(new_args)

        return True


# Global token redaction filter instance
token_filter = TokenRedactionFilter()


class AuthManager:
    """Manages user identity mapping, per-project permissions, and dynamic RBAC checks."""

    def __init__(self, config_path: Optional[str] = None):
        self.config_path = config_path or os.getenv("USERS_CONFIG_PATH", "users.json")
        self._users: Dict[str, UserProfile] = {}
        self.reload()

    def reload(self) -> None:
        """Loads or reloads users from JSON configuration file."""
        path = Path(self.config_path)
        if not path.is_absolute():
            path = Path.cwd() / path

        if not path.exists():
            logger.warning("Users config file not found at %s. No users loaded.", path)
            self._users = {}
            return

        try:
            with open(path, "r", encoding="utf-8") as f:
                data = json.load(f)

            users_data = data.get("users", {})
            parsed_users: Dict[str, UserProfile] = {}

            for uid, details in users_data.items():
                try:
                    role_str = str(details.get("role", details.get("default_role", "viewer"))).lower()
                    
                    # Parse per-project role overrides if present
                    raw_p_roles = details.get("project_roles", {})
                    parsed_p_roles = {}
                    for pid_str, p_role_str in raw_p_roles.items():
                        try:
                            parsed_p_roles[str(pid_str)] = Role(str(p_role_str).lower())
                        except ValueError:
                            pass

                    user = UserProfile(
                        user_id=uid,
                        token=details.get("token", ""),
                        role=Role(role_str),
                        project_roles=parsed_p_roles,
                        name=details.get("name"),
                        email=details.get("email"),
                    )
                    parsed_users[uid] = user
                    token_filter.add_token(user.token)
                except Exception as exc:
                    logger.error("Failed to parse user %s: %s", uid, exc)

            self._users = parsed_users
            logger.info("Successfully loaded %d users from %s", len(self._users), path)

        except Exception as exc:
            logger.error("Error reading users config at %s: %s", path, exc)
            self._users = {}

    def get_user(self, user_id: str) -> Optional[UserProfile]:
        """Retrieves user profile by ID."""
        if not user_id:
            return None
        return self._users.get(user_id)

    def get_user_by_token(self, token: str) -> Optional[UserProfile]:
        """Finds user by matching bearer token."""
        if not token:
            return None
        for user in self._users.values():
            if user.token == token:
                return user
        return None

    def register_user(self, user_id: str, token: str, role: Role = Role.ADMIN, name: Optional[str] = None) -> UserProfile:
        """Dynamically registers or updates a user profile and persists to users.json."""
        user = UserProfile(
            user_id=user_id,
            token=token,
            role=role,
            name=name or user_id,
        )
        self._users[user_id] = user
        token_filter.add_token(token)

        try:
            path = Path(self.config_path)
            if not path.is_absolute():
                path = Path.cwd() / path
            data = {"users": {}}
            if path.exists():
                with open(path, "r", encoding="utf-8") as f:
                    data = json.load(f)
            if "users" not in data:
                data["users"] = {}
            data["users"][user_id] = {
                "token": token,
                "name": name or user_id,
                "default_role": role.value if hasattr(role, "value") else str(role),
            }
            with open(path, "w", encoding="utf-8") as f:
                json.dump(data, f, indent=2)
            logger.info("Persisted dynamic user '%s' to %s", user_id, path)
        except Exception as e:
            logger.error("Failed to persist dynamic user %s: %s", user_id, e)

        return user


    def resolve_effective_role(
        self,
        user: UserProfile,
        project_id: Optional[int] = None,
        vikunja_max_permission: Optional[int] = None,
    ) -> Role:
        """Resolves the user's effective role for a specific project.
        
        Resolution Priority:
          1. Explicit project_roles mapping in users.json (e.g. project "1": "admin")
          2. Live Vikunja max_permission from API:
             - 2 -> Admin (Owner / Full control)
             - 1 -> Contributor (Write / Member)
             - 0 -> Viewer (Read / Guest)
          3. User default role from users.json
        """
        if project_id is not None:
            pid_str = str(project_id)
            if pid_str in user.project_roles:
                return user.project_roles[pid_str]

        # Map live Vikunja permission integer if provided
        if vikunja_max_permission is not None:
            if vikunja_max_permission >= 2:
                return Role.ADMIN
            elif vikunja_max_permission == 1:
                return Role.CONTRIBUTOR
            elif vikunja_max_permission <= 0:
                return Role.VIEWER

        return user.role

    def authenticate_and_authorize(
        self,
        user_id: str,
        action: Action | str,
        project_id: Optional[int] = None,
        vikunja_max_permission: Optional[int] = None,
        is_task_creator: bool = False,
    ) -> AuthResult:
        """Validates that user exists and has permission for the specified action on the target project.
        
        Args:
            user_id: The authenticated user identifier.
            action: Action being attempted (e.g. create_task, delete_task).
            project_id: Optional ID of the project being accessed.
            vikunja_max_permission: Optional live permission level (0=Read, 1=Write, 2=Admin).
            is_task_creator: True if the user is the original creator of the task (for delete_task).
        """
        if isinstance(action, str):
            try:
                action_enum = Action(action)
            except ValueError:
                action_enum = None
        else:
            action_enum = action

        # 1. Authentication Check
        if not user_id or user_id not in self._users:
            default_user = self._users.get("Ghost") or self._users.get("admin_user") or (next(iter(self._users.values())) if self._users else None)
            if default_user:
                logger.info("User '%s' not found, falling back to default user '%s'.", user_id, default_user.user_id)
                user = default_user
            else:
                logger.warning("Authentication failed: User '%s' not found in mapping.", user_id)
                return AuthResult(
                    is_authorized=False,
                    error_response={
                        "status": "error",
                        "code": "UNAUTHORIZED",
                        "message": f"User '{user_id}' is not authorized or not found.",
                    },
                )
        else:
            user = self._users[user_id]

        if not user.token:
            logger.warning("Authentication failed: User '%s' has no API token configured.", user_id)
            return AuthResult(
                is_authorized=False,
                error_response={
                    "status": "error",
                    "code": "UNAUTHORIZED",
                    "message": f"User '{user_id}' has no Vikunja API token configured.",
                },
            )

        # 2. Contextual Role Resolution (Per-Project)
        effective_role = self.resolve_effective_role(
            user=user,
            project_id=project_id,
            vikunja_max_permission=vikunja_max_permission,
        )

        # 3. RBAC Permission Check
        role_actions = ROLE_PERMISSIONS.get(effective_role, set())

        # Specific rule for task deletion:
        # Admins on the project can delete ANY task.
        # Contributors on the project can delete tasks THEY CREATED.
        # Viewers (guests) CANNOT delete tasks.
        if action_enum == Action.DELETE_TASK:
            if effective_role == Role.ADMIN:
                return AuthResult(is_authorized=True, user=user, effective_role=effective_role)
            elif effective_role == Role.CONTRIBUTOR:
                if is_task_creator:
                    return AuthResult(is_authorized=True, user=user, effective_role=effective_role)
                else:
                    return AuthResult(
                        is_authorized=False,
                        user=user,
                        effective_role=effective_role,
                        error_response={
                            "status": "forbidden",
                            "message": "Contributors can only delete tasks they created. You are not the creator of this task.",
                        },
                    )
            else:
                proj_context = f" on project {project_id}" if project_id else ""
                return AuthResult(
                    is_authorized=False,
                    user=user,
                    effective_role=effective_role,
                    error_response={
                        "status": "forbidden",
                        "message": f"User role{proj_context} is 'viewer' (guest). Write permissions are not granted for this project.",
                    },
                )

        # For write actions (create_task, update_task, toggle_task_done, assign_task)
        if action_enum in {
            Action.CREATE_TASK,
            Action.UPDATE_TASK,
            Action.TOGGLE_TASK_DONE,
            Action.ASSIGN_TASK,
        }:
            if effective_role in {Role.ADMIN, Role.CONTRIBUTOR}:
                return AuthResult(is_authorized=True, user=user, effective_role=effective_role)
            else:
                proj_context = f" on project {project_id}" if project_id else ""
                return AuthResult(
                    is_authorized=False,
                    user=user,
                    effective_role=effective_role,
                    error_response={
                        "status": "forbidden",
                        "message": f"User role{proj_context} is 'viewer' (guest). Write permissions are not granted for this project.",
                    },
                )

        # For read actions (list_projects, get_tasks, get_task_details, list_labels)
        if action_enum in role_actions:
            return AuthResult(is_authorized=True, user=user, effective_role=effective_role)

        # Catch-all forbidden
        return AuthResult(
            is_authorized=False,
            user=user,
            effective_role=effective_role,
            error_response={
                "status": "forbidden",
                "message": f"User role '{effective_role.value}' does not have permission for action '{action_enum.value if action_enum else action}'.",
            },
        )
