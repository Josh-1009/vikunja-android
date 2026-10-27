"""Unit tests for new tools and security hardening features."""

import asyncio
import unittest
from auth import Action, AuthManager, Role, UserProfile
from vikunja_client import (
    RateLimiter,
    validate_hex_color,
    validate_per_page,
    validate_positive_id,
    validate_priority,
)


class TestSecurityAndNewTools(unittest.TestCase):
    def test_input_validation(self):
        # Positive ID
        self.assertEqual(validate_positive_id(42), 42)
        self.assertEqual(validate_positive_id("100"), 100)
        with self.assertRaises(ValueError):
            validate_positive_id(-5)
        with self.assertRaises(ValueError):
            validate_positive_id("abc")

        # Hex Color
        self.assertEqual(validate_hex_color("#ff4136"), "#ff4136")
        self.assertEqual(validate_hex_color("#FFFFFF"), "#FFFFFF")
        self.assertIsNone(validate_hex_color(None))
        with self.assertRaises(ValueError):
            validate_hex_color("blue")
        with self.assertRaises(ValueError):
            validate_hex_color("#12345")

        # Priority
        self.assertEqual(validate_priority(0), 0)
        self.assertEqual(validate_priority(4), 4)
        self.assertIsNone(validate_priority(None))
        with self.assertRaises(ValueError):
            validate_priority(5)
        with self.assertRaises(ValueError):
            validate_priority(-1)

        # Pagination clamping
        self.assertEqual(validate_per_page(50), 50)
        self.assertEqual(validate_per_page(500), 200)
        self.assertEqual(validate_per_page(0), 1)

    def test_rate_limiter(self):
        limiter = RateLimiter(requests_per_minute=120)
        async def run_acquire():
            await limiter.acquire()
            await limiter.acquire()
        asyncio.run(run_acquire())

    def test_rbac_new_actions(self):
        auth_mgr = AuthManager(config_path="/tmp/test_nonexistent_users.json")
        auth_mgr._users["alice"] = UserProfile(
            user_id="alice",
            token="alice_token",
            role=Role.VIEWER,
            project_roles={"10": Role.ADMIN, "20": Role.CONTRIBUTOR},
        )

        # Viewer on project 30 cannot create project or labels
        res = auth_mgr.authenticate_and_authorize("alice", Action.CREATE_PROJECT)
        self.assertFalse(res.is_authorized)

        # Alice is Admin on project 10 -> can update and delete project 10
        res_del = auth_mgr.authenticate_and_authorize("alice", Action.DELETE_PROJECT, project_id=10)
        self.assertTrue(res_del.is_authorized)

        # Alice is Contributor on project 20 -> cannot delete project 20
        res_del_contrib = auth_mgr.authenticate_and_authorize("alice", Action.DELETE_PROJECT, project_id=20)
        self.assertFalse(res_del_contrib.is_authorized)

        # Alice is Contributor on project 20 -> can create task and add label
        res_label = auth_mgr.authenticate_and_authorize("alice", Action.ADD_LABEL, project_id=20)
        self.assertTrue(res_label.is_authorized)


if __name__ == "__main__":
    unittest.main()
