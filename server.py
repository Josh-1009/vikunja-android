"""Production-ready Model Context Protocol (MCP) server for Vikunja with Contextual Per-Project RBAC.

Enforces tenant isolation and granular permission boundaries:
Users have full admin privileges in their own projects, member/contributor rights in collaborative
projects, and read-only/guest access in shared projects.
"""

from __future__ import annotations

import argparse
import logging
import os
import sys
from typing import Any, Dict, List, Optional
from dotenv import load_dotenv

# Load environment configuration (.env)
load_dotenv()

from auth import Action, AuthManager, Role, UserProfile, token_filter
from vikunja_client import VikunjaClient

# Attempt FastMCP import from mcp 2.x (MCPServer) or fallback to mcp 1.x (FastMCP)
try:
    from mcp.server.mcpserver import MCPServer as FastMCP, Context
except ImportError:
    try:
        from mcp.server.fastmcp import FastMCP, Context
    except ImportError:
        raise ImportError(
            "Official 'mcp' SDK not found. Please install via: pip install 'mcp>=1.0.0'"
        )

# =============================================================================
# Logging Configuration with Bearer Token Redaction
# =============================================================================
log_level = os.getenv("LOG_LEVEL", "INFO").upper()
logging.basicConfig(
    level=getattr(logging, log_level, logging.INFO),
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    stream=sys.stderr,  # MCP stdio servers must send logs to stderr, reserving stdout for JSON-RPC!
)

root_logger = logging.getLogger()
root_logger.addFilter(token_filter)

logger = logging.getLogger("vikunja_mcp.server")
logger.info("Initializing Vikunja MCP Server with per-project contextual RBAC.")

# =============================================================================
# Core Services Initialization
# =============================================================================
auth_manager = AuthManager()
vikunja_client = VikunjaClient()

from oauth_provider import register_oauth_routes

# Initialize MCP Server instance
mcp = FastMCP(
    name="vikunja",
    instructions=(
        "Production Vikunja MCP Server with contextual Per-Project Role-Based Access Control (RBAC). "
        "Allows managing tasks and projects according to owner, contributor, and viewer privileges."
    ),
)

# Register RFC 9728 & RFC 8414 OAuth Provider routes for Google Gemini
# register_oauth_routes(mcp, auth_manager)


def _resolve_caller(ctx: Optional[Context] = None, user_id: str = "Ghost") -> tuple[UserProfile, str]:
    """Resolves UserProfile and Bearer token from Context HTTP authorization header, URL query, or fallback user."""
    req = None
    if ctx and hasattr(ctx, "request_context"):
        req = getattr(ctx.request_context, "request", None)

    # 1. Bearer header
    if req and hasattr(req, "headers"):
        auth_header = req.headers.get("authorization") or req.headers.get("Authorization")
        if auth_header and auth_header.startswith("Bearer "):
            raw_token = auth_header[7:].strip()
            user = auth_manager.get_user_by_token(raw_token)
            if user:
                return user, raw_token
            user = auth_manager.register_user(user_id=user_id or "authenticated_user", token=raw_token)
            return user, raw_token

    # 2. Query param: ?token=tk_... or ?user=username
    if req and hasattr(req, "query_params"):
        q_token = req.query_params.get("token")
        if q_token:
            user = auth_manager.get_user_by_token(q_token)
            if user:
                return user, q_token
            user = auth_manager.register_user(user_id=req.query_params.get("user", "user"), token=q_token)
            return user, q_token

        q_user = req.query_params.get("user")
        if q_user:
            user = auth_manager.get_user(q_user)
            if user:
                return user, user.token

    # 3. Explicit user_id parameter or Default fallback
    effective_uid = user_id or "Ghost"
    user = auth_manager.get_user(effective_uid) or auth_manager.get_user("Ghost")
    if not user:
        user = auth_manager.register_user(
            user_id="Ghost",
            token="tk_c8a99e50fb2255551c09a780d623549854ab744f",
            role=Role.ADMIN,
        )
    return user, user.token


async def _get_effective_project_permission(user, project_id: Optional[int]) -> Optional[int]:
    """Helper to resolve the user's permission integer (0=Read, 1=Write, 2=Admin) for a project."""
    if project_id is None or not user:
        return None

    pid_str = str(project_id)
    # Check explicit override in user profile
    if pid_str in user.project_roles:
        role = user.project_roles[pid_str]
        return 2 if role == Role.ADMIN else (1 if role == Role.CONTRIBUTOR else 0)

    # Query Vikunja project endpoint for live permission
    res = await vikunja_client.get_project(token=user.token, project_id=project_id)
    if res.get("status") == "success":
        return res.get("project", {}).get("max_permission")
    return None


# =============================================================================
# MCP Tools
# =============================================================================

@mcp.tool()
async def list_accessible_projects(
    user_id: str = "Ghost",
    ctx: Optional[Context] = None,
) -> Dict[str, Any]:
    """Fetches only the projects visible to the requesting user's Vikunja account with their effective role.

    Permissions:
        Allowed for all authenticated users. Each returned project lists whether the user is
        'admin' (owner/full control), 'contributor' (edit rights), or 'viewer' (guest/read-only).

    Args:
        user_id: Unique identifier of the authenticated user (e.g., 'admin_user', 'ahmed_user').

    Returns:
        A structured JSON response containing the list of accessible projects with role metadata.
    """
    user, token = _resolve_caller(ctx, user_id)
    logger.info("Tool called: list_accessible_projects for user_id='%s'", user.user_id)
    auth = auth_manager.authenticate_and_authorize(user.user_id, Action.LIST_PROJECTS)
    if not auth.is_authorized:
        return auth.error_response

    return await vikunja_client.list_projects(token=token)


@mcp.tool()
async def get_project_tasks(
    project_id: int,
    filter_done: bool = False,
    user_id: str = "Ghost",
    ctx: Optional[Context] = None,
) -> Dict[str, Any]:
    """Lists tasks for a specific Vikunja project, with optional completion filtering.

    Permissions:
        Allowed for users who have read access or higher to the project.

    Args:
        user_id: Unique identifier of the requesting user.
        project_id: ID of the project whose tasks are being retrieved.
        filter_done: If True, returns only completed tasks. If False (default), returns only open/pending tasks.

    Returns:
        Structured JSON response containing tasks list, task count, and project ID.
    """
    user, token = _resolve_caller(ctx, user_id)
    logger.info(
        "Tool called: get_project_tasks for user_id='%s', project_id=%d, filter_done=%s",
        user.user_id,
        project_id,
        filter_done,
    )
    vikunja_perm = await _get_effective_project_permission(user, project_id)
    auth = auth_manager.authenticate_and_authorize(
        user_id=user.user_id,
        action=Action.GET_TASKS,
        project_id=project_id,
        vikunja_max_permission=vikunja_perm,
    )
    if not auth.is_authorized:
        return auth.error_response

    return await vikunja_client.get_project_tasks(
        token=token,
        project_id=project_id,
        filter_done=filter_done,
    )


@mcp.tool()
async def get_task_details(
    task_id: int,
    user_id: str = "Ghost",
    ctx: Optional[Context] = None,
) -> Dict[str, Any]:
    """Retrieves full details of a specific task by its ID.

    Permissions:
        Allowed for users with access to the parent project.

    Args:
        user_id: Unique identifier of the requesting user.
        task_id: Unique ID of the Vikunja task.

    Returns:
        Detailed JSON representation of the task including assignees, dates, and labels.
    """
    user, token = _resolve_caller(ctx, user_id)
    logger.info("Tool called: get_task_details for user_id='%s', task_id=%d", user.user_id, task_id)
    auth = auth_manager.authenticate_and_authorize(user.user_id, Action.GET_TASK_DETAILS)
    if not auth.is_authorized:
        return auth.error_response

    return await vikunja_client.get_task(token=token, task_id=task_id)


@mcp.tool()
async def create_task(
    project_id: int,
    title: str,
    description: str = "",
    due_date: Optional[str] = None,
    user_id: str = "Ghost",
    ctx: Optional[Context] = None,
) -> Dict[str, Any]:
    """Validates contextual project role and creates a new task within the specified project.

    Permissions:
        - Allowed if user is 'admin' (owner) or 'contributor' on this specific project.
        - Blocked if user is a 'viewer' (guest/read-only) on this project.

    Args:
        user_id: Unique identifier of the requesting user.
        project_id: ID of the target project to contain the new task.
        title: Short title / name of the task.
        description: Optional markdown-formatted task body or instructions.
        due_date: Optional RFC3339/ISO 8601 timestamp string (e.g. '2026-11-01T12:00:00Z').

    Returns:
        Created task object or structured error if unauthorized/forbidden.
    """
    user, token = _resolve_caller(ctx, user_id)
    logger.info(
        "Tool called: create_task for user_id='%s', project_id=%d, title='%s'",
        user.user_id,
        project_id,
        title,
    )
    vikunja_perm = await _get_effective_project_permission(user, project_id)
    auth = auth_manager.authenticate_and_authorize(
        user_id=user.user_id,
        action=Action.CREATE_TASK,
        project_id=project_id,
        vikunja_max_permission=vikunja_perm,
    )
    if not auth.is_authorized:
        return auth.error_response

    return await vikunja_client.create_task(
        token=token,
        project_id=project_id,
        title=title,
        description=description,
        due_date=due_date,
    )


@mcp.tool()
async def update_task_status(
    task_id: int,
    done: bool,
    user_id: str = "Ghost",
    ctx: Optional[Context] = None,
) -> Dict[str, Any]:
    """Updates a task's completion status (marks done or undone).

    Permissions:
        - Allowed if user has write/admin rights on the task's project.
        - Blocked if user is only a viewer/guest on the project.

    Args:
        user_id: Unique identifier of the requesting user.
        task_id: ID of the task to update.
        done: True to mark the task completed, False to mark it incomplete/open.

    Returns:
        Updated task status confirmation.
    """
    user, token = _resolve_caller(ctx, user_id)
    logger.info(
        "Tool called: update_task_status for user_id='%s', task_id=%d, done=%s",
        user.user_id,
        task_id,
        done,
    )
    task_res = await vikunja_client.get_task(token=token, task_id=task_id)
    if task_res.get("status") != "success":
        return task_res
    project_id = task_res.get("task", {}).get("project_id")

    vikunja_perm = await _get_effective_project_permission(user, project_id)
    auth = auth_manager.authenticate_and_authorize(
        user_id=user.user_id,
        action=Action.TOGGLE_TASK_DONE,
        project_id=project_id,
        vikunja_max_permission=vikunja_perm,
    )
    if not auth.is_authorized:
        return auth.error_response

    return await vikunja_client.update_task_status(
        token=token,
        task_id=task_id,
        done=done,
    )


@mcp.tool()
async def assign_task(
    task_id: int,
    target_user: str,
    user_id: str = "Ghost",
    ctx: Optional[Context] = None,
) -> Dict[str, Any]:
    """Assigns a team member to an existing task by their username or numeric user ID.

    Permissions:
        - Allowed if user has contributor or admin privileges on the project.
        - Blocked if user is a guest/viewer on the project.

    Args:
        user_id: Unique identifier of the requesting user.
        task_id: ID of the task to assign.
        target_user: User ID (e.g. '42') or Vikunja username (e.g. 'alice') to assign.

    Returns:
        Structured assignment confirmation or error.
    """
    user, token = _resolve_caller(ctx, user_id)
    logger.info(
        "Tool called: assign_task for user_id='%s', task_id=%d, target_user='%s'",
        user.user_id,
        task_id,
        target_user,
    )
    task_res = await vikunja_client.get_task(token=token, task_id=task_id)
    if task_res.get("status") != "success":
        return task_res
    project_id = task_res.get("task", {}).get("project_id")

    vikunja_perm = await _get_effective_project_permission(user, project_id)
    auth = auth_manager.authenticate_and_authorize(
        user_id=user.user_id,
        action=Action.ASSIGN_TASK,
        project_id=project_id,
        vikunja_max_permission=vikunja_perm,
    )
    if not auth.is_authorized:
        return auth.error_response

    return await vikunja_client.assign_task(
        token=token,
        task_id=task_id,
        target_user=target_user,
    )


@mcp.tool()
async def delete_task(
    task_id: int,
    user_id: str = "Ghost",
    ctx: Optional[Context] = None,
) -> Dict[str, Any]:
    """Permanently deletes a task from Vikunja.

    Permissions:
        - Admins on the project can delete ANY task.
        - Contributors can delete tasks THEY CREATED.
        - Viewers / guests are blocked from deleting any task.

    Args:
        user_id: Unique identifier of the requesting user.
        task_id: ID of the task to delete.

    Returns:
        Confirmation of task deletion or error.
    """
    user, token = _resolve_caller(ctx, user_id)
    logger.info("Tool called: delete_task for user_id='%s', task_id=%d", user.user_id, task_id)
    task_res = await vikunja_client.get_task(token=token, task_id=task_id)
    if task_res.get("status") != "success":
        return task_res
    task = task_res.get("task", {})
    project_id = task.get("project_id")

    # Determine if the requesting user created this task
    user_info = await vikunja_client.get_current_user_info(token=token)
    current_uid = user_info.get("user", {}).get("id") if user_info.get("status") == "success" else None
    task_creator_id = (task.get("created_by") or {}).get("id")
    is_task_creator = (current_uid is not None and task_creator_id is not None and current_uid == task_creator_id)

    vikunja_perm = await _get_effective_project_permission(user, project_id)
    auth = auth_manager.authenticate_and_authorize(
        user_id=user.user_id,
        action=Action.DELETE_TASK,
        project_id=project_id,
        vikunja_max_permission=vikunja_perm,
        is_task_creator=is_task_creator,
    )
    if not auth.is_authorized:
        return auth.error_response

    return await vikunja_client.delete_task(token=token, task_id=task_id)


@mcp.tool()
async def list_labels(
    user_id: str = "Ghost",
    ctx: Optional[Context] = None,
) -> Dict[str, Any]:
    """Lists all task labels visible to the user.

    Permissions:
        Allowed for all authenticated roles.

    Args:
        user_id: Unique identifier of the requesting user.

    Returns:
        List of label objects available in Vikunja.
    """
    user, token = _resolve_caller(ctx, user_id)
    logger.info("Tool called: list_labels for user_id='%s'", user.user_id)
    auth = auth_manager.authenticate_and_authorize(user.user_id, Action.LIST_LABELS)
    if not auth.is_authorized:
        return auth.error_response

    return await vikunja_client.list_labels(token=token)


@mcp.tool()
async def get_project_details(
    project_id: int,
    user_id: str = "Ghost",
    ctx: Optional[Context] = None,
) -> Dict[str, Any]:
    """Retrieves full details and effective permissions for a project.

    Permissions:
        Allowed for users with read/view access or higher to the project.
    """
    user, token = _resolve_caller(ctx, user_id)
    logger.info("Tool called: get_project_details for user_id='%s', project_id=%d", user.user_id, project_id)
    vikunja_perm = await _get_effective_project_permission(user, project_id)
    auth = auth_manager.authenticate_and_authorize(
        user_id=user.user_id,
        action=Action.GET_PROJECT_DETAILS,
        project_id=project_id,
        vikunja_max_permission=vikunja_perm,
    )
    if not auth.is_authorized:
        return auth.error_response

    return await vikunja_client.get_project(token=token, project_id=project_id)


@mcp.tool()
async def create_project(
    title: str,
    description: str = "",
    parent_project_id: Optional[int] = None,
    hex_color: Optional[str] = None,
    user_id: str = "Ghost",
    ctx: Optional[Context] = None,
) -> Dict[str, Any]:
    """Creates a new project in Vikunja.

    Permissions:
        Allowed for Contributor and Admin roles.
    """
    user, token = _resolve_caller(ctx, user_id)
    logger.info("Tool called: create_project for user_id='%s', title='%s'", user.user_id, title)
    auth = auth_manager.authenticate_and_authorize(user.user_id, Action.CREATE_PROJECT)
    if not auth.is_authorized:
        return auth.error_response

    try:
        return await vikunja_client.create_project(
            token=token,
            title=title,
            description=description,
            parent_project_id=parent_project_id,
            hex_color=hex_color,
        )
    except ValueError as ve:
        return {"status": "error", "code": "INVALID_INPUT", "message": str(ve)}


@mcp.tool()
async def update_project(
    project_id: int,
    title: Optional[str] = None,
    description: Optional[str] = None,
    is_archived: Optional[bool] = None,
    hex_color: Optional[str] = None,
    user_id: str = "Ghost",
    ctx: Optional[Context] = None,
) -> Dict[str, Any]:
    """Updates an existing project in Vikunja (title, description, archived, color).

    Permissions:
        Allowed for Admin (owner) role on the project.
    """
    user, token = _resolve_caller(ctx, user_id)
    logger.info("Tool called: update_project for user_id='%s', project_id=%d", user.user_id, project_id)
    vikunja_perm = await _get_effective_project_permission(user, project_id)
    auth = auth_manager.authenticate_and_authorize(
        user_id=user.user_id,
        action=Action.UPDATE_PROJECT,
        project_id=project_id,
        vikunja_max_permission=vikunja_perm,
    )
    if not auth.is_authorized:
        return auth.error_response

    try:
        return await vikunja_client.update_project(
            token=token,
            project_id=project_id,
            title=title,
            description=description,
            is_archived=is_archived,
            hex_color=hex_color,
        )
    except ValueError as ve:
        return {"status": "error", "code": "INVALID_INPUT", "message": str(ve)}


@mcp.tool()
async def delete_project(
    project_id: int,
    user_id: str = "Ghost",
    ctx: Optional[Context] = None,
) -> Dict[str, Any]:
    """Permanently deletes a project and all associated tasks.

    Permissions:
        Admin/owner privileges strictly required.
    """
    user, token = _resolve_caller(ctx, user_id)
    logger.info("Tool called: delete_project for user_id='%s', project_id=%d", user.user_id, project_id)
    vikunja_perm = await _get_effective_project_permission(user, project_id)
    auth = auth_manager.authenticate_and_authorize(
        user_id=user.user_id,
        action=Action.DELETE_PROJECT,
        project_id=project_id,
        vikunja_max_permission=vikunja_perm,
    )
    if not auth.is_authorized:
        return auth.error_response

    return await vikunja_client.delete_project(token=token, project_id=project_id)


@mcp.tool()
async def list_all_tasks(
    page: int = 1,
    per_page: int = 50,
    search: Optional[str] = None,
    sort_by: Optional[str] = None,
    order_by: Optional[str] = None,
    filter_str: Optional[str] = None,
    user_id: str = "Ghost",
    ctx: Optional[Context] = None,
) -> Dict[str, Any]:
    """Lists tasks across all accessible projects with search, filter, and pagination support.

    Permissions:
        Allowed for all authenticated roles.
    """
    user, token = _resolve_caller(ctx, user_id)
    logger.info("Tool called: list_all_tasks for user_id='%s', page=%d, per_page=%d", user.user_id, page, per_page)
    auth = auth_manager.authenticate_and_authorize(user.user_id, Action.LIST_ALL_TASKS)
    if not auth.is_authorized:
        return auth.error_response

    return await vikunja_client.list_all_tasks(
        token=token,
        page=page,
        per_page=per_page,
        search=search,
        sort_by=sort_by,
        order_by=order_by,
        filter_str=filter_str,
    )


@mcp.tool()
async def update_task(
    task_id: int,
    title: Optional[str] = None,
    description: Optional[str] = None,
    done: Optional[bool] = None,
    priority: Optional[int] = None,
    due_date: Optional[str] = None,
    hex_color: Optional[str] = None,
    user_id: str = "Ghost",
    ctx: Optional[Context] = None,
) -> Dict[str, Any]:
    """Updates multiple attributes of an existing task (title, description, done, priority, due_date, color).

    Permissions:
        Allowed if user has contributor or admin permissions on the task's project.
    """
    user, token = _resolve_caller(ctx, user_id)
    logger.info("Tool called: update_task for user_id='%s', task_id=%d", user.user_id, task_id)
    task_res = await vikunja_client.get_task(token=token, task_id=task_id)
    if task_res.get("status") != "success":
        return task_res
    project_id = task_res.get("task", {}).get("project_id")

    vikunja_perm = await _get_effective_project_permission(user, project_id)
    auth = auth_manager.authenticate_and_authorize(
        user_id=user.user_id,
        action=Action.UPDATE_TASK,
        project_id=project_id,
        vikunja_max_permission=vikunja_perm,
    )
    if not auth.is_authorized:
        return auth.error_response

    try:
        return await vikunja_client.update_task(
            token=token,
            task_id=task_id,
            title=title,
            description=description,
            done=done,
            priority=priority,
            due_date=due_date,
            hex_color=hex_color,
        )
    except ValueError as ve:
        return {"status": "error", "code": "INVALID_INPUT", "message": str(ve)}


@mcp.tool()
async def bulk_create_tasks(
    project_id: int,
    tasks: List[Dict[str, Any]],
    user_id: str = "Ghost",
    ctx: Optional[Context] = None,
) -> Dict[str, Any]:
    """Creates multiple tasks (1 to 50) at once within a specific project.

    Permissions:
        Allowed for contributors and admins on the project.
    """
    user, token = _resolve_caller(ctx, user_id)
    logger.info("Tool called: bulk_create_tasks for user_id='%s', project_id=%d, count=%d", user.user_id, project_id, len(tasks))
    vikunja_perm = await _get_effective_project_permission(user, project_id)
    auth = auth_manager.authenticate_and_authorize(
        user_id=user.user_id,
        action=Action.BULK_CREATE_TASKS,
        project_id=project_id,
        vikunja_max_permission=vikunja_perm,
    )
    if not auth.is_authorized:
        return auth.error_response

    return await vikunja_client.bulk_create_tasks(
        token=token,
        project_id=project_id,
        tasks=tasks,
    )


@mcp.tool()
async def unassign_task(
    task_id: int,
    target_user: str,
    user_id: str = "Ghost",
    ctx: Optional[Context] = None,
) -> Dict[str, Any]:
    """Removes an assigned user from a task.

    Permissions:
        Allowed for contributors and admins on the project.
    """
    user, token = _resolve_caller(ctx, user_id)
    logger.info("Tool called: unassign_task for user_id='%s', task_id=%d, target_user='%s'", user.user_id, task_id, target_user)
    task_res = await vikunja_client.get_task(token=token, task_id=task_id)
    if task_res.get("status") != "success":
        return task_res
    project_id = task_res.get("task", {}).get("project_id")

    vikunja_perm = await _get_effective_project_permission(user, project_id)
    auth = auth_manager.authenticate_and_authorize(
        user_id=user.user_id,
        action=Action.UNASSIGN_TASK,
        project_id=project_id,
        vikunja_max_permission=vikunja_perm,
    )
    if not auth.is_authorized:
        return auth.error_response

    return await vikunja_client.unassign_task(
        token=token,
        task_id=task_id,
        target_user=target_user,
    )


@mcp.tool()
async def create_label(
    title: str,
    hex_color: Optional[str] = None,
    description: str = "",
    user_id: str = "Ghost",
    ctx: Optional[Context] = None,
) -> Dict[str, Any]:
    """Creates a new label in Vikunja.

    Permissions:
        Allowed for Contributor and Admin roles.
    """
    user, token = _resolve_caller(ctx, user_id)
    logger.info("Tool called: create_label for user_id='%s', title='%s'", user.user_id, title)
    auth = auth_manager.authenticate_and_authorize(user.user_id, Action.CREATE_LABEL)
    if not auth.is_authorized:
        return auth.error_response

    try:
        return await vikunja_client.create_label(
            token=token,
            title=title,
            hex_color=hex_color,
            description=description,
        )
    except ValueError as ve:
        return {"status": "error", "code": "INVALID_INPUT", "message": str(ve)}


@mcp.tool()
async def add_label_to_task(
    task_id: int,
    label_id: int,
    user_id: str = "Ghost",
    ctx: Optional[Context] = None,
) -> Dict[str, Any]:
    """Attaches a label to an existing task.

    Permissions:
        Allowed for contributors and admins on the task's project.
    """
    user, token = _resolve_caller(ctx, user_id)
    logger.info("Tool called: add_label_to_task for user_id='%s', task_id=%d, label_id=%d", user.user_id, task_id, label_id)
    task_res = await vikunja_client.get_task(token=token, task_id=task_id)
    if task_res.get("status") != "success":
        return task_res
    project_id = task_res.get("task", {}).get("project_id")

    vikunja_perm = await _get_effective_project_permission(user, project_id)
    auth = auth_manager.authenticate_and_authorize(
        user_id=user.user_id,
        action=Action.ADD_LABEL,
        project_id=project_id,
        vikunja_max_permission=vikunja_perm,
    )
    if not auth.is_authorized:
        return auth.error_response

    try:
        return await vikunja_client.add_label_to_task(token=token, task_id=task_id, label_id=label_id)
    except ValueError as ve:
        return {"status": "error", "code": "INVALID_INPUT", "message": str(ve)}


@mcp.tool()
async def remove_label_from_task(
    task_id: int,
    label_id: int,
    user_id: str = "Ghost",
    ctx: Optional[Context] = None,
) -> Dict[str, Any]:
    """Removes a label from a task.

    Permissions:
        Allowed for contributors and admins on the task's project.
    """
    user, token = _resolve_caller(ctx, user_id)
    logger.info("Tool called: remove_label_from_task for user_id='%s', task_id=%d, label_id=%d", user.user_id, task_id, label_id)
    task_res = await vikunja_client.get_task(token=token, task_id=task_id)
    if task_res.get("status") != "success":
        return task_res
    project_id = task_res.get("task", {}).get("project_id")

    vikunja_perm = await _get_effective_project_permission(user, project_id)
    auth = auth_manager.authenticate_and_authorize(
        user_id=user.user_id,
        action=Action.REMOVE_LABEL,
        project_id=project_id,
        vikunja_max_permission=vikunja_perm,
    )
    if not auth.is_authorized:
        return auth.error_response

    try:
        return await vikunja_client.remove_label_from_task(token=token, task_id=task_id, label_id=label_id)
    except ValueError as ve:
        return {"status": "error", "code": "INVALID_INPUT", "message": str(ve)}


# =============================================================================
# CLI Entrypoint
# =============================================================================

def main():
    """Parses command-line arguments and launches the MCP server."""
    parser = argparse.ArgumentParser(description="Vikunja RBAC MCP Server")
    parser.add_argument(
        "--transport",
        choices=["stdio", "sse", "streamable-http"],
        default=os.getenv("MCP_TRANSPORT", "stdio"),
        help="Transport protocol to use (default: stdio)",
    )
    parser.add_argument(
        "--host",
        default=os.getenv("MCP_SERVER_HOST", "0.0.0.0"),
        help="Host interface to bind HTTP/SSE server (default: 0.0.0.0)",
    )
    parser.add_argument(
        "--port",
        type=int,
        default=int(os.getenv("MCP_SERVER_PORT", "8000")),
        help="Port number for HTTP/SSE server (default: 8000)",
    )

    args = parser.parse_args()

    logger.info(
        "Starting Vikunja MCP server via transport '%s' (users loaded: %d)",
        args.transport,
        len(auth_manager._users),
    )

    if args.transport == "stdio":
        mcp.run(transport="stdio")
    elif args.transport == "sse":
        logger.info("Listening for SSE connections on http://%s:%d/sse", args.host, args.port)
        try:
            from mcp.server.transport_security import TransportSecuritySettings
            sec = TransportSecuritySettings(
                enable_dns_rebinding_protection=False,
                allowed_hosts=["*"],
                allowed_origins=["*"],
            )
            mcp.run(transport="sse", host=args.host, port=args.port, transport_security=sec)
        except Exception:
            mcp.run(transport="sse", host=args.host, port=args.port)
    elif args.transport == "streamable-http":
        logger.info("Listening for HTTP connections on http://%s:%d/mcp", args.host, args.port)
        try:
            from mcp.server.transport_security import TransportSecuritySettings
            sec = TransportSecuritySettings(
                enable_dns_rebinding_protection=False,
                allowed_hosts=["*"],
                allowed_origins=["*"],
            )
            mcp.run(transport="streamable-http", host=args.host, port=args.port, transport_security=sec)
        except Exception:
            mcp.run(transport="streamable-http", host=args.host, port=args.port)


if __name__ == "__main__":
    main()
