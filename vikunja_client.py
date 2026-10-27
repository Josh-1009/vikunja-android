"""Vikunja HTTP API Client with per-user token authentication and robust error handling.

Handles all REST interactions with the Vikunja backend (projects, tasks, assignees, labels)
while strictly redacting secrets and translating HTTP status codes into structured responses.
"""

from __future__ import annotations

import asyncio
import logging
import math
import os
import random
import re
import time
from typing import Any, Dict, List, Optional, Union
import httpx

from auth import token_filter

logger = logging.getLogger("vikunja_mcp.client")
logger.addFilter(token_filter)

# =============================================================================
# Input Validation Helpers (Inspired by DoD STIG / NIST SI-10 Zod Schemas)
# =============================================================================
HEX_COLOR_REGEX = re.compile(r"^#[0-9a-fA-F]{6}$")

def validate_positive_id(val: Any, name: str = "ID") -> int:
    """Validates that a parameter is a positive integer."""
    try:
        ival = int(val)
        if ival <= 0:
            raise ValueError()
        return ival
    except (ValueError, TypeError):
        raise ValueError(f"{name} must be a positive integer, got '{val}'.")

def validate_hex_color(color: Optional[str]) -> Optional[str]:
    """Validates hex color format (#RRGGBB)."""
    if not color:
        return None
    cleaned = color.strip()
    if not HEX_COLOR_REGEX.match(cleaned):
        raise ValueError(f"Invalid hex_color '{color}'. Must be in #RRGGBB format (e.g., '#ff4136').")
    return cleaned

def validate_priority(priority: Optional[int]) -> Optional[int]:
    """Validates Vikunja priority range (0=none, 1=low, 2=medium, 3=high, 4=urgent)."""
    if priority is None:
        return None
    if not (0 <= int(priority) <= 4):
        raise ValueError(f"Priority must be between 0 and 4, got {priority}.")
    return int(priority)

def validate_per_page(per_page: Optional[int], default: int = 50, max_val: int = 200) -> int:
    """Clamps per_page pagination between 1 and max_val to prevent DoS."""
    if per_page is None:
        return default
    return max(1, min(int(per_page), max_val))


# =============================================================================
# Token-Bucket Rate Limiter with Backoff (NIST SC-5)
# =============================================================================
class RateLimiter:
    """Token-bucket rate limiter with automatic refill and wait logic."""

    def __init__(self, requests_per_minute: Optional[int] = None):
        rpm_env = os.getenv("VIKUNJA_RATE_LIMIT", "60")
        try:
            rpm = requests_per_minute or int(rpm_env)
        except ValueError:
            rpm = 60
        self.rpm = max(1, rpm)
        self.max_tokens = float(self.rpm)
        self.tokens = float(self.rpm)
        self.refill_rate = self.max_tokens / 60.0  # tokens per second
        self.last_refill = time.monotonic()
        self._lock = asyncio.Lock()

    def _refill(self) -> None:
        now = time.monotonic()
        elapsed = now - self.last_refill
        self.tokens = min(self.max_tokens, self.tokens + (elapsed * self.refill_rate))
        self.last_refill = now

    async def acquire(self) -> None:
        async with self._lock:
            self._refill()
            if self.tokens >= 1.0:
                self.tokens -= 1.0
                return
            wait_s = (1.0 - self.tokens) / self.refill_rate
            logger.warning("Vikunja rate limit reached, waiting %.2fs", wait_s)
            await asyncio.sleep(wait_s)
            self._refill()
            self.tokens = max(0.0, self.tokens - 1.0)


class VikunjaClient:
    """Async client for interacting with Vikunja API v1 endpoints."""

    def __init__(self, base_url: Optional[str] = None, timeout: float = 15.0):
        raw_url = base_url or os.getenv("VIKUNJA_API_URL", "http://localhost:3456/api/v1")
        cleaned = raw_url.rstrip("/")
        if not cleaned.endswith("/api/v1"):
            cleaned = f"{cleaned}/api/v1"
        self.base_url = cleaned
        self.timeout = timeout
        self.limiter = RateLimiter()
        logger.info("Initialized VikunjaClient with base_url: %s", self.base_url)

    def _get_headers(self, token: str) -> Dict[str, str]:
        """Builds request headers with user-scoped Bearer token."""
        return {
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
            "Accept": "application/json",
        }

    async def _request(
        self,
        method: str,
        endpoint: str,
        token: str,
        params: Optional[Dict[str, Any]] = None,
        json_data: Optional[Dict[str, Any]] = None,
    ) -> Dict[str, Any]:
        """Executes an authenticated HTTP request against Vikunja with rate limiting and retry."""
        url = f"{self.base_url}{endpoint}"
        headers = self._get_headers(token)

        max_retries = 3
        base_backoff = 1.0
        max_backoff = 20.0

        for attempt in range(max_retries + 1):
            await self.limiter.acquire()
            logger.debug("HTTP %s request to %s (attempt %d/%d, params: %s)", method, endpoint, attempt + 1, max_retries + 1, params)

            try:
                async with httpx.AsyncClient(timeout=self.timeout) as client:
                    response = await client.request(
                        method=method,
                        url=url,
                        headers=headers,
                        params=params,
                        json=json_data,
                    )
            except httpx.TimeoutException as exc:
                if attempt == max_retries:
                    logger.error("Timeout connecting to Vikunja API at %s: %s", endpoint, exc)
                    return {"status": "error", "code": "TIMEOUT", "message": f"Request timed out after {self.timeout}s."}
                await asyncio.sleep(base_backoff * (2 ** attempt))
                continue
            except httpx.ConnectError as exc:
                logger.error("Connection failed to Vikunja API at %s: %s", self.base_url, exc)
                return {"status": "error", "code": "CONNECTION_FAILED", "message": f"Could not connect to {self.base_url}."}
            except Exception as exc:
                logger.exception("Unexpected error while calling Vikunja API %s", endpoint)
                return {"status": "error", "code": "INTERNAL_CLIENT_ERROR", "message": str(exc)}

            # Retry on 429 (Rate Limit) or 503 (Service Unavailable)
            if response.status_code in (429, 503) and attempt < max_retries:
                retry_header = response.headers.get("Retry-After")
                if retry_header and retry_header.isdigit():
                    backoff = min(max_backoff, float(retry_header))
                else:
                    backoff = min(max_backoff, base_backoff * (2 ** attempt) + random.uniform(0.1, 0.5))
                logger.warning("Vikunja returned %d, retrying in %.2fs (attempt %d/%d)", response.status_code, backoff, attempt + 1, max_retries)
                await asyncio.sleep(backoff)
                continue

            break

        # Check status codes and map to standard structured responses
        if response.status_code == 401:
            logger.warning("Vikunja 401 Unauthorized for endpoint %s", endpoint)
            return {
                "status": "error",
                "code": "VIKUNJA_UNAUTHORIZED",
                "message": "Vikunja API token is invalid or expired.",
            }

        if response.status_code == 403:
            logger.warning("Vikunja 403 Forbidden for endpoint %s", endpoint)
            return {
                "status": "error",
                "code": "VIKUNJA_FORBIDDEN",
                "message": "Vikunja API: Insufficient permissions for this resource or project.",
            }

        if response.status_code == 404:
            logger.warning("Vikunja 404 Not Found for endpoint %s", endpoint)
            return {
                "status": "error",
                "code": "NOT_FOUND",
                "message": "The requested project, task, or resource was not found in Vikunja.",
            }

        if response.status_code == 429:
            logger.warning("Vikunja 429 Rate Limited for endpoint %s", endpoint)
            return {
                "status": "error",
                "code": "RATE_LIMITED",
                "message": "Vikunja API rate limit exceeded. Please retry after a brief delay.",
            }

        # Handle unexpected 4xx or 5xx
        if response.is_error:
            error_body = response.text
            try:
                error_json = response.json()
                error_msg = error_json.get("message", error_body)
            except Exception:
                error_msg = error_body

            logger.error(
                "Vikunja API error %d on %s: %s",
                response.status_code,
                endpoint,
                error_msg[:200],
            )
            return {
                "status": "error",
                "code": f"HTTP_{response.status_code}",
                "message": f"Vikunja API error ({response.status_code}): {error_msg}",
            }

        # Success responses (200, 201, 204)
        if response.status_code == 204 or not response.content:
            return {"status": "success", "data": None}

        return {"status": "success", "data": response.json()}

    # =========================================================================
    # User Profile & Projects
    # =========================================================================
    async def get_current_user_info(self, token: str) -> Dict[str, Any]:
        """Fetches the authenticated user's profile from /user."""
        res = await self._request("GET", "/user", token)
        if res.get("status") == "success" and isinstance(res.get("data"), dict):
            return {"status": "success", "user": res["data"]}
        return res

    async def get_project(self, token: str, project_id: int) -> Dict[str, Any]:
        """Fetches details and user permissions for a specific project."""
        res = await self._request("GET", f"/projects/{project_id}", token)
        if res.get("status") == "success" and isinstance(res.get("data"), dict):
            p = res["data"]
            max_perm = p.get("max_permission")
            role_desc = "admin" if (max_perm is not None and max_perm >= 2) else (
                "contributor" if (max_perm == 1) else "viewer"
            )
            return {
                "status": "success",
                "project": {
                    "id": p.get("id"),
                    "title": p.get("title"),
                    "description": p.get("description"),
                    "max_permission": max_perm,
                    "effective_role": role_desc,
                    "is_archived": p.get("is_archived", False),
                },
            }
        return res

    async def list_projects(self, token: str) -> Dict[str, Any]:
        """Fetches all projects visible to user, expanding permission level for each."""
        res = await self._request("GET", "/projects", token, params={"expand": "permissions"})
        if res.get("status") == "success" and isinstance(res.get("data"), list):
            projects = []
            for p in res["data"]:
                max_perm = p.get("max_permission")
                role_desc = "admin" if (max_perm is not None and max_perm >= 2) else (
                    "contributor" if (max_perm == 1) else "viewer"
                )
                projects.append({
                    "id": p.get("id"),
                    "title": p.get("title"),
                    "description": p.get("description"),
                    "role": role_desc,
                    "max_permission": max_perm,
                    "is_archived": p.get("is_archived", False),
                    "is_favorite": p.get("is_favorite", False),
                    "created": p.get("created"),
                    "updated": p.get("updated"),
                })
            return {"status": "success", "projects": projects}
        return res

    # =========================================================================
    # Tasks
    # =========================================================================
    async def get_project_tasks(
        self,
        token: str,
        project_id: int,
        filter_done: Optional[bool] = None,
    ) -> Dict[str, Any]:
        """Lists tasks inside a specific project, with optional completion filtering."""
        res = await self._request("GET", f"/projects/{project_id}/tasks", token)
        if res.get("status") == "success" and isinstance(res.get("data"), list):
            tasks = res["data"]
            if filter_done is not None:
                tasks = [t for t in tasks if t.get("done") is filter_done]

            proj_res = await self.get_project(token, project_id)
            project_title = proj_res.get("project", {}).get("title", f"Project #{project_id}") if proj_res.get("status") == "success" else f"Project #{project_id}"

            formatted = []
            for t in tasks:
                formatted.append({
                    "id": t.get("id"),
                    "project_id": t.get("project_id"),
                    "project_title": project_title,
                    "title": t.get("title"),
                    "description": t.get("description"),
                    "done": t.get("done", False),
                    "due_date": t.get("due_date"),
                    "priority": t.get("priority"),
                    "assignees": [
                        {"id": a.get("id"), "username": a.get("username"), "name": a.get("name")}
                        for a in (t.get("assignees") or [])
                    ],
                })
            return {
                "status": "success",
                "project_id": project_id,
                "project_title": project_title,
                "count": len(formatted),
                "tasks": formatted,
            }
        return res

    async def get_task(self, token: str, task_id: int) -> Dict[str, Any]:
        """Retrieves details of a single task."""
        res = await self._request("GET", f"/tasks/{task_id}", token)
        if res.get("status") == "success" and isinstance(res.get("data"), dict):
            return {"status": "success", "task": res["data"]}
        return res

    async def create_task(
        self,
        token: str,
        project_id: int,
        title: str,
        description: str = "",
        due_date: Optional[str] = None,
    ) -> Dict[str, Any]:
        """Creates a new task in a project via Vikunja's PUT /projects/{id}/tasks endpoint."""
        payload: Dict[str, Any] = {
            "title": title,
            "description": description,
        }
        if due_date:
            payload["due_date"] = due_date

        res = await self._request("PUT", f"/projects/{project_id}/tasks", token, json_data=payload)
        if res.get("status") == "success" and isinstance(res.get("data"), dict):
            task = res["data"]
            return {
                "status": "success",
                "message": f"Task '{title}' created successfully.",
                "task": {
                    "id": task.get("id"),
                    "project_id": task.get("project_id", project_id),
                    "title": task.get("title"),
                    "description": task.get("description"),
                    "done": task.get("done", False),
                    "due_date": task.get("due_date"),
                },
            }
        return res

    async def update_task_status(self, token: str, task_id: int, done: bool) -> Dict[str, Any]:
        """Updates task completion state via POST /tasks/{id}."""
        payload = {"done": done}
        res = await self._request("POST", f"/tasks/{task_id}", token, json_data=payload)
        if res.get("status") == "success" and isinstance(res.get("data"), dict):
            task = res["data"]
            return {
                "status": "success",
                "message": f"Task {task_id} marked as {'done' if done else 'undone'}.",
                "task": {
                    "id": task.get("id"),
                    "title": task.get("title"),
                    "done": task.get("done"),
                },
            }
        return res

    async def assign_task(self, token: str, task_id: int, target_user: str) -> Dict[str, Any]:
        """Assigns a user to a task.
        
        Accepts numeric user ID ('42') or username string ('johndoe').
        If given a username, resolves the user ID via /users query first.
        """
        user_id_to_assign: Optional[int] = None

        if str(target_user).strip().isdigit():
            user_id_to_assign = int(target_user)
        else:
            # Resolve username via /users?s=<username>
            user_res = await self._request("GET", "/users", token, params={"s": target_user})
            if user_res.get("status") == "success" and isinstance(user_res.get("data"), list):
                users = user_res["data"]
                # Find exact username match or first close match
                matched = next(
                    (u for u in users if u.get("username", "").lower() == str(target_user).lower()),
                    users[0] if users else None,
                )
                if matched:
                    user_id_to_assign = matched.get("id")
                else:
                    return {
                        "status": "error",
                        "code": "USER_NOT_FOUND",
                        "message": f"Vikunja user '{target_user}' could not be found to assign.",
                    }
            else:
                return {
                    "status": "error",
                    "code": "USER_LOOKUP_FAILED",
                    "message": f"Failed to search for user '{target_user}': {user_res.get('message')}",
                }

        payload = {"user_id": user_id_to_assign}
        res = await self._request("PUT", f"/tasks/{task_id}/assignees", token, json_data=payload)
        if res.get("status") == "success":
            return {
                "status": "success",
                "message": f"Assigned user '{target_user}' (ID: {user_id_to_assign}) to task {task_id}.",
                "task_id": task_id,
                "assigned_user_id": user_id_to_assign,
            }
        return res

    async def delete_task(self, token: str, task_id: int) -> Dict[str, Any]:
        """Permanently deletes a task via DELETE /tasks/{id} (Admin only)."""
        res = await self._request("DELETE", f"/tasks/{task_id}", token)
        if res.get("status") == "success":
            return {
                "status": "success",
                "message": f"Task {task_id} was successfully deleted.",
            }
        return res

    async def create_project(
        self,
        token: str,
        title: str,
        description: str = "",
        parent_project_id: Optional[int] = None,
        hex_color: Optional[str] = None,
    ) -> Dict[str, Any]:
        """Creates a new project in Vikunja via PUT /projects."""
        payload: Dict[str, Any] = {
            "title": title.strip(),
            "description": description.strip(),
        }
        if parent_project_id:
            payload["parent_project_id"] = validate_positive_id(parent_project_id, "parent_project_id")
        if hex_color:
            payload["hex_color"] = validate_hex_color(hex_color)

        res = await self._request("PUT", "/projects", token, json_data=payload)
        if res.get("status") == "success" and isinstance(res.get("data"), dict):
            p = res["data"]
            return {
                "status": "success",
                "message": f"Project '{p.get('title')}' created successfully.",
                "project": {
                    "id": p.get("id"),
                    "title": p.get("title"),
                    "description": p.get("description"),
                    "parent_project_id": p.get("parent_project_id"),
                    "hex_color": p.get("hex_color"),
                    "is_archived": p.get("is_archived", False),
                },
            }
        return res

    async def update_project(
        self,
        token: str,
        project_id: int,
        title: Optional[str] = None,
        description: Optional[str] = None,
        is_archived: Optional[bool] = None,
        hex_color: Optional[str] = None,
    ) -> Dict[str, Any]:
        """Updates an existing project in Vikunja via POST /projects/{id}."""
        pid = validate_positive_id(project_id, "project_id")
        payload: Dict[str, Any] = {}
        if title is not None:
            payload["title"] = title.strip()
        if description is not None:
            payload["description"] = description.strip()
        if is_archived is not None:
            payload["is_archived"] = is_archived
        if hex_color is not None:
            payload["hex_color"] = validate_hex_color(hex_color)

        res = await self._request("POST", f"/projects/{pid}", token, json_data=payload)
        if res.get("status") == "success" and isinstance(res.get("data"), dict):
            p = res["data"]
            return {
                "status": "success",
                "message": f"Project #{pid} updated successfully.",
                "project": {
                    "id": p.get("id"),
                    "title": p.get("title"),
                    "description": p.get("description"),
                    "is_archived": p.get("is_archived", False),
                    "hex_color": p.get("hex_color"),
                },
            }
        return res

    async def delete_project(self, token: str, project_id: int) -> Dict[str, Any]:
        """Permanently deletes a project and all its tasks via DELETE /projects/{id}."""
        pid = validate_positive_id(project_id, "project_id")
        res = await self._request("DELETE", f"/projects/{pid}", token)
        if res.get("status") == "success":
            return {
                "status": "success",
                "message": f"Project #{pid} was successfully deleted.",
                "project_id": pid,
            }
        return res

    async def list_all_tasks(
        self,
        token: str,
        page: int = 1,
        per_page: int = 50,
        search: Optional[str] = None,
        sort_by: Optional[str] = None,
        order_by: Optional[str] = None,
        filter_str: Optional[str] = None,
    ) -> Dict[str, Any]:
        """Lists tasks across all accessible projects with search, filter, and pagination support."""
        params: Dict[str, Any] = {
            "page": max(1, int(page)),
            "per_page": validate_per_page(per_page),
        }
        if search:
            params["s"] = search.strip()
        if sort_by:
            params["sort_by"] = sort_by.strip()
        if order_by:
            params["order_by"] = "desc" if order_by.lower() == "desc" else "asc"
        if filter_str:
            params["filter"] = filter_str.strip()

        res = await self._request("GET", "/tasks", token, params=params)
        if res.get("status") == "success" and isinstance(res.get("data"), list):
            tasks = res["data"]
            proj_res = await self.list_projects(token)
            proj_map = {}
            if proj_res.get("status") == "success" and isinstance(proj_res.get("projects"), list):
                proj_map = {p.get("id"): p.get("title") for p in proj_res["projects"] if p.get("id")}

            formatted = []
            for t in tasks:
                pid = t.get("project_id")
                formatted.append({
                    "id": t.get("id"),
                    "project_id": pid,
                    "project_title": proj_map.get(pid, f"Project #{pid}" if pid else "Unknown"),
                    "title": t.get("title"),
                    "description": t.get("description"),
                    "done": t.get("done", False),
                    "due_date": t.get("due_date"),
                    "priority": t.get("priority", 0),
                    "hex_color": t.get("hex_color", ""),
                    "percent_done": t.get("percent_done", 0.0),
                    "assignees": [
                        {"id": a.get("id"), "username": a.get("username"), "name": a.get("name")}
                        for a in (t.get("assignees") or [])
                    ],
                    "labels": [
                        {"id": l.get("id"), "title": l.get("title"), "hex_color": l.get("hex_color")}
                        for l in (t.get("labels") or [])
                    ],
                })
            return {
                "status": "success",
                "count": len(formatted),
                "page": params["page"],
                "per_page": params["per_page"],
                "tasks": formatted,
            }
        return res

    async def update_task(
        self,
        token: str,
        task_id: int,
        title: Optional[str] = None,
        description: Optional[str] = None,
        done: Optional[bool] = None,
        priority: Optional[int] = None,
        due_date: Optional[str] = None,
        hex_color: Optional[str] = None,
        percent_done: Optional[float] = None,
    ) -> Dict[str, Any]:
        """Updates multiple attributes of an existing task via POST /tasks/{id}."""
        tid = validate_positive_id(task_id, "task_id")
        payload: Dict[str, Any] = {}
        if title is not None:
            payload["title"] = title.strip()
        if description is not None:
            payload["description"] = description.strip()
        if done is not None:
            payload["done"] = bool(done)
        if priority is not None:
            payload["priority"] = validate_priority(priority)
        if due_date is not None:
            payload["due_date"] = due_date.strip()
        if hex_color is not None:
            payload["hex_color"] = validate_hex_color(hex_color)
        if percent_done is not None:
            payload["percent_done"] = max(0.0, min(1.0, float(percent_done)))

        res = await self._request("POST", f"/tasks/{tid}", token, json_data=payload)
        if res.get("status") == "success" and isinstance(res.get("data"), dict):
            task = res["data"]
            return {
                "status": "success",
                "message": f"Task #{tid} updated successfully.",
                "task": task,
            }
        return res

    async def unassign_task(self, token: str, task_id: int, target_user: str) -> Dict[str, Any]:
        """Removes a user from a task's assignees via DELETE /tasks/{id}/assignees/{user_id}."""
        tid = validate_positive_id(task_id, "task_id")
        user_id_to_remove: Optional[int] = None

        if str(target_user).strip().isdigit():
            user_id_to_remove = int(target_user)
        else:
            user_res = await self._request("GET", "/users", token, params={"s": target_user})
            if user_res.get("status") == "success" and isinstance(user_res.get("data"), list):
                users = user_res["data"]
                matched = next(
                    (u for u in users if u.get("username", "").lower() == str(target_user).lower()),
                    users[0] if users else None,
                )
                if matched:
                    user_id_to_remove = matched.get("id")
                else:
                    return {
                        "status": "error",
                        "code": "USER_NOT_FOUND",
                        "message": f"Vikunja user '{target_user}' could not be found to unassign.",
                    }
            else:
                return {
                    "status": "error",
                    "code": "USER_LOOKUP_FAILED",
                    "message": f"Failed to search for user '{target_user}': {user_res.get('message')}",
                }

        res = await self._request("DELETE", f"/tasks/{tid}/assignees/{user_id_to_remove}", token)
        if res.get("status") == "success":
            return {
                "status": "success",
                "message": f"Unassigned user '{target_user}' (ID: {user_id_to_remove}) from task #{tid}.",
                "task_id": tid,
                "unassigned_user_id": user_id_to_remove,
            }
        return res

    async def bulk_create_tasks(
        self,
        token: str,
        project_id: int,
        tasks: List[Dict[str, Any]],
    ) -> Dict[str, Any]:
        """Creates multiple tasks in a project with granular success/failure tracking (1-50 tasks)."""
        pid = validate_positive_id(project_id, "project_id")
        if not tasks:
            return {"status": "error", "code": "EMPTY_TASKS", "message": "At least one task must be provided."}
        if len(tasks) > 50:
            return {"status": "error", "code": "MAX_TASKS_EXCEEDED", "message": "Cannot create more than 50 tasks in a single bulk request."}

        created: List[Dict[str, Any]] = []
        failed: List[Dict[str, Any]] = []

        for item in tasks:
            title = item.get("title", "").strip()
            if not title:
                failed.append({"item": item, "error": "Task title cannot be empty"})
                continue

            payload: Dict[str, Any] = {
                "title": title,
                "description": item.get("description", "").strip(),
            }
            if item.get("due_date"):
                payload["due_date"] = item["due_date"].strip()
            if item.get("priority") is not None:
                try:
                    payload["priority"] = validate_priority(item["priority"])
                except ValueError as ve:
                    failed.append({"title": title, "error": str(ve)})
                    continue
            if item.get("done") is not None:
                payload["done"] = bool(item["done"])

            res = await self._request("PUT", f"/projects/{pid}/tasks", token, json_data=payload)
            if res.get("status") == "success" and isinstance(res.get("data"), dict):
                t = res["data"]
                created.append({"id": t.get("id"), "title": t.get("title")})
            else:
                failed.append({"title": title, "error": res.get("message", "Failed to create task")})

        return {
            "status": "success",
            "project_id": pid,
            "created_count": len(created),
            "failed_count": len(failed),
            "created_tasks": created,
            "failed_tasks": failed,
        }

    # =========================================================================
    # Labels
    # =========================================================================
    async def list_labels(self, token: str) -> Dict[str, Any]:
        """Lists all labels visible to the user."""
        res = await self._request("GET", "/labels", token)
        if res.get("status") == "success" and isinstance(res.get("data"), list):
            return {"status": "success", "labels": res["data"]}
        return res

    async def create_label(
        self,
        token: str,
        title: str,
        hex_color: Optional[str] = None,
        description: str = "",
    ) -> Dict[str, Any]:
        """Creates a new label via PUT /labels."""
        payload: Dict[str, Any] = {
            "title": title.strip(),
            "description": description.strip(),
        }
        if hex_color:
            payload["hex_color"] = validate_hex_color(hex_color)

        res = await self._request("PUT", "/labels", token, json_data=payload)
        if res.get("status") == "success" and isinstance(res.get("data"), dict):
            lbl = res["data"]
            return {
                "status": "success",
                "message": f"Label '{lbl.get('title')}' created successfully.",
                "label": lbl,
            }
        return res

    async def add_label_to_task(self, token: str, task_id: int, label_id: int) -> Dict[str, Any]:
        """Attaches a label to a task via PUT /tasks/{id}/labels."""
        tid = validate_positive_id(task_id, "task_id")
        lid = validate_positive_id(label_id, "label_id")
        res = await self._request("PUT", f"/tasks/{tid}/labels", token, json_data={"label_id": lid})
        if res.get("status") == "success":
            return {
                "status": "success",
                "message": f"Label #{lid} added to task #{tid}.",
                "task_id": tid,
                "label_id": lid,
            }
        return res

    async def remove_label_from_task(self, token: str, task_id: int, label_id: int) -> Dict[str, Any]:
        """Detaches a label from a task via DELETE /tasks/{id}/labels/{label_id}."""
        tid = validate_positive_id(task_id, "task_id")
        lid = validate_positive_id(label_id, "label_id")
        res = await self._request("DELETE", f"/tasks/{tid}/labels/{lid}", token)
        if res.get("status") == "success":
            return {
                "status": "success",
                "message": f"Label #{lid} removed from task #{tid}.",
                "task_id": tid,
                "label_id": lid,
            }
        return res
