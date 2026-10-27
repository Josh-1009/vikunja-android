"""Comprehensive test suite for Vikunja MCP Server and Per-Project Contextual RBAC.

Validates authentication, per-project dynamic roles (Owner/Admin in personal projects,
Contributor in team projects, Guest/Viewer in shared projects), and token redaction.
"""

from __future__ import annotations

import json
import logging
import os
import unittest
from unittest.mock import AsyncMock, MagicMock, patch
import httpx

from auth import Action, AuthManager, Role, TokenRedactionFilter, UserProfile
from vikunja_client import VikunjaClient
import server


class TestPerProjectContextualRBAC(unittest.TestCase):
    """Tests that permissions are scoped to specific projects."""

    def setUp(self):
        # Configure test user who is Admin on Project 1, Contributor on Project 2, and Guest on Project 3
        self.test_users = {
            "users": {
                "josh": {
                    "token": "secret_josh_token_123",
                    "role": "viewer",  # Default global fallback
                    "project_roles": {
                        "1": "admin",        # Josh's personal project -> Full Control
                        "2": "contributor",  # Shared collaborative project -> Edit / Create
                        "3": "viewer",       # Read-only guest project -> View only
                    },
                },
                "guest_user": {
                    "token": "secret_guest_token_456",
                    "role": "viewer",
                },
            }
        }
        self.config_path = "/tmp/test_contextual_users.json"
        with open(self.config_path, "w", encoding="utf-8") as f:
            json.dump(self.test_users, f)

        self.auth_mgr = AuthManager(config_path=self.config_path)

    def tearDown(self):
        if os.path.exists(self.config_path):
            os.remove(self.config_path)

    def test_personal_project_admin_privileges(self):
        """User has full admin control in their personal project (Project 1)."""
        # Can create tasks
        res_create = self.auth_mgr.authenticate_and_authorize(
            user_id="josh",
            action=Action.CREATE_TASK,
            project_id=1,
        )
        self.assertTrue(res_create.is_authorized)
        self.assertEqual(res_create.effective_role, Role.ADMIN)

        # Can delete any task
        res_del = self.auth_mgr.authenticate_and_authorize(
            user_id="josh",
            action=Action.DELETE_TASK,
            project_id=1,
            is_task_creator=False,  # Even if not creator, admin can delete
        )
        self.assertTrue(res_del.is_authorized)

    def test_team_project_contributor_privileges(self):
        """User can create and edit in team project (Project 2), but delete only own tasks."""
        # Can create tasks
        res_create = self.auth_mgr.authenticate_and_authorize(
            user_id="josh",
            action=Action.CREATE_TASK,
            project_id=2,
        )
        self.assertTrue(res_create.is_authorized)
        self.assertEqual(res_create.effective_role, Role.CONTRIBUTOR)

        # Can delete tasks they created
        res_del_own = self.auth_mgr.authenticate_and_authorize(
            user_id="josh",
            action=Action.DELETE_TASK,
            project_id=2,
            is_task_creator=True,
        )
        self.assertTrue(res_del_own.is_authorized)

        # CANNOT delete tasks created by others in team project
        res_del_other = self.auth_mgr.authenticate_and_authorize(
            user_id="josh",
            action=Action.DELETE_TASK,
            project_id=2,
            is_task_creator=False,
        )
        self.assertFalse(res_del_other.is_authorized)
        self.assertEqual(res_del_other.error_response.get("status"), "forbidden")
        self.assertIn("Contributors can only delete tasks they created", res_del_other.error_response.get("message"))

    def test_guest_project_read_only(self):
        """User is strictly read-only in guest project (Project 3)."""
        # Allowed to read
        res_read = self.auth_mgr.authenticate_and_authorize(
            user_id="josh",
            action=Action.GET_TASKS,
            project_id=3,
        )
        self.assertTrue(res_read.is_authorized)
        self.assertEqual(res_read.effective_role, Role.VIEWER)

        # Blocked from creating tasks
        res_create = self.auth_mgr.authenticate_and_authorize(
            user_id="josh",
            action=Action.CREATE_TASK,
            project_id=3,
        )
        self.assertFalse(res_create.is_authorized)
        self.assertEqual(res_create.error_response.get("status"), "forbidden")
        self.assertIn("viewer", res_create.error_response.get("message"))

        # Blocked from deleting tasks
        res_del = self.auth_mgr.authenticate_and_authorize(
            user_id="josh",
            action=Action.DELETE_TASK,
            project_id=3,
        )
        self.assertFalse(res_del.is_authorized)
        self.assertEqual(res_del.error_response.get("status"), "forbidden")


class TestDynamicVikunjaPermissionMapping(unittest.TestCase):
    """Tests automatic resolution from Vikunja's live max_permission API value."""

    def setUp(self):
        self.auth_mgr = AuthManager(config_path="/tmp/nonexistent.json")
        self.auth_mgr._users = {
            "dynamic_user": UserProfile(
                user_id="dynamic_user",
                token="dyn_token",
                role=Role.VIEWER,
            )
        }

    def test_resolves_admin_from_vikunja(self):
        """max_permission=2 maps to Admin."""
        res = self.auth_mgr.authenticate_and_authorize(
            user_id="dynamic_user",
            action=Action.CREATE_TASK,
            project_id=10,
            vikunja_max_permission=2,
        )
        self.assertTrue(res.is_authorized)
        self.assertEqual(res.effective_role, Role.ADMIN)

    def test_resolves_contributor_from_vikunja(self):
        """max_permission=1 maps to Contributor."""
        res = self.auth_mgr.authenticate_and_authorize(
            user_id="dynamic_user",
            action=Action.CREATE_TASK,
            project_id=10,
            vikunja_max_permission=1,
        )
        self.assertTrue(res.is_authorized)
        self.assertEqual(res.effective_role, Role.CONTRIBUTOR)

    def test_resolves_viewer_from_vikunja(self):
        """max_permission=0 maps to Viewer / Guest."""
        res = self.auth_mgr.authenticate_and_authorize(
            user_id="dynamic_user",
            action=Action.CREATE_TASK,
            project_id=10,
            vikunja_max_permission=0,
        )
        self.assertFalse(res.is_authorized)
        self.assertEqual(res.effective_role, Role.VIEWER)


class TestTokenRedaction(unittest.TestCase):
    """Ensures Bearer tokens and sensitive keys are sanitized in logs."""

    def test_redaction(self):
        filter_instance = TokenRedactionFilter(sensitive_tokens={"super_secret_token_abc"})
        record = logging.LogRecord(
            name="test",
            level=logging.INFO,
            pathname=__file__,
            lineno=10,
            msg="Request with Bearer eyJhbGciOiJIUzI1NiJ9 and token='super_secret_token_abc'",
            args=(),
            exc_info=None,
        )

        filter_instance.filter(record)
        self.assertNotIn("super_secret_token_abc", record.msg)
        self.assertNotIn("eyJhbGciOiJIUzI1NiJ9", record.msg)
        self.assertIn("Bearer [REDACTED_TOKEN]", record.msg)


class TestServerToolsPerProject(unittest.IsolatedAsyncioTestCase):
    """End-to-end tests exercising server.py tool functions with per-project roles."""

    def setUp(self):
        self.test_users = {
            "users": {
                "josh": {
                    "token": "secret_josh_tok",
                    "role": "viewer",
                    "project_roles": {
                        "1": "admin",
                        "2": "viewer",
                    },
                }
            }
        }
        self.config_path = "/tmp/test_mcp_proj_users.json"
        with open(self.config_path, "w", encoding="utf-8") as f:
            json.dump(self.test_users, f)

        server.auth_manager = AuthManager(config_path=self.config_path)

    def tearDown(self):
        if os.path.exists(self.config_path):
            os.remove(self.config_path)

    async def test_create_task_allowed_in_own_project(self):
        """Josh is admin on project 1 -> create_task succeeds."""
        with patch.object(server.vikunja_client, "create_task", new_callable=AsyncMock) as mock_create:
            mock_create.return_value = {
                "status": "success",
                "task": {"id": 1, "title": "My Task"},
            }
            res = await server.create_task(
                user_id="josh",
                project_id=1,
                title="My Task",
            )
            self.assertEqual(res.get("status"), "success")
            mock_create.assert_called_once()

    async def test_create_task_blocked_in_guest_project(self):
        """Josh is viewer/guest on project 2 -> create_task is blocked locally."""
        with patch.object(server.vikunja_client, "create_task", new_callable=AsyncMock) as mock_create:
            res = await server.create_task(
                user_id="josh",
                project_id=2,
                title="Guest Task",
            )
            self.assertEqual(res.get("status"), "forbidden")
            self.assertIn("viewer", res.get("message"))
            mock_create.assert_not_called()


if __name__ == "__main__":
    unittest.main()
