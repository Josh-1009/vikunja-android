"""Standalone Gemini Agent CLI for Vikunja MCP.

Allows running an interactive Gemini session that directly calls Vikunja MCP tools.
"""

from __future__ import annotations

import asyncio
import json
import os
import sys
from dotenv import load_dotenv

load_dotenv()

from server import (
    assign_task,
    create_task,
    delete_task,
    get_project_tasks,
    get_task_details,
    list_accessible_projects,
    list_labels,
    update_task_status,
)

TOOL_MAP = {
    "list_accessible_projects": list_accessible_projects,
    "get_project_tasks": get_project_tasks,
    "get_task_details": get_task_details,
    "create_task": create_task,
    "update_task_status": update_task_status,
    "assign_task": assign_task,
    "delete_task": delete_task,
    "list_labels": list_labels,
}


async def execute_tool(tool_name: str, **kwargs):
    if tool_name not in TOOL_MAP:
        return {"error": f"Tool '{tool_name}' not found"}
    func = TOOL_MAP[tool_name]
    return await func(**kwargs)


def main():
    print("=" * 60)
    print("Vikunja Gemini MCP Test Agent")
    print("=" * 60)
    print("Ready to execute Vikunja actions directly against production API.")
    print("Commands:")
    print("  1: List projects")
    print("  2: List tasks for project")
    print("  3: Create task")
    print("  4: Complete task")
    print("  q: Exit")
    print("-" * 60)

    user_id = input("Enter user_id [default: admin_user]: ").strip() or "admin_user"

    while True:
        choice = input("\nSelect action (1/2/3/4/q): ").strip()
        if choice == "q":
            break
        elif choice == "1":
            res = asyncio.run(execute_tool("list_accessible_projects", user_id=user_id))
            print(json.dumps(res, indent=2, ensure_ascii=False))
        elif choice == "2":
            pid = int(input("Enter project_id: ").strip())
            res = asyncio.run(execute_tool("get_project_tasks", user_id=user_id, project_id=pid))
            print(json.dumps(res, indent=2, ensure_ascii=False))
        elif choice == "3":
            pid = int(input("Enter project_id: ").strip())
            title = input("Enter task title: ").strip()
            desc = input("Enter description (optional): ").strip()
            res = asyncio.run(execute_tool("create_task", user_id=user_id, project_id=pid, title=title, description=desc))
            print(json.dumps(res, indent=2, ensure_ascii=False))
        elif choice == "4":
            tid = int(input("Enter task_id: ").strip())
            res = asyncio.run(execute_tool("update_task_status", user_id=user_id, task_id=tid, done=True))
            print(json.dumps(res, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
