# Vikunja Project Guidelines for Gemini

## MCP Tools Integration
This workspace is equipped with the `vikunja` MCP server. You can manage tasks, projects, and assignments directly through the following tools:

- `list_accessible_projects(user_id)`: Fetches visible projects with role metadata.
- `get_project_details(user_id, project_id)`: Retrieves full project metadata and permissions.
- `create_project(user_id, title, description, parent_project_id, hex_color)`: Creates a new project.
- `update_project(user_id, project_id, title, description, is_archived, hex_color)`: Updates project details.
- `delete_project(user_id, project_id)`: Deletes a project (admin only).
- `list_all_tasks(user_id, page, per_page, search, sort_by, order_by, filter_str)`: Lists tasks across all projects with filters.
- `get_project_tasks(user_id, project_id, filter_done)`: Lists tasks for a specific project.
- `get_task_details(user_id, task_id)`: Retrieves full task details.
- `create_task(user_id, project_id, title, description, due_date)`: Creates a new task.
- `update_task(user_id, task_id, title, description, done, priority, due_date, hex_color)`: Full task update.
- `update_task_status(user_id, task_id, done)`: Marks a task done or undone.
- `bulk_create_tasks(user_id, project_id, tasks)`: Creates 1-50 tasks at once.
- `assign_task(user_id, task_id, target_user)`: Assigns a user to a task.
- `unassign_task(user_id, task_id, target_user)`: Unassigns a user from a task.
- `delete_task(user_id, task_id)`: Deletes a task.
- `list_labels(user_id)`: Lists all task labels.
- `create_label(user_id, title, hex_color, description)`: Creates a new label.
- `add_label_to_task(user_id, task_id, label_id)`: Attaches a label to a task.
- `remove_label_from_task(user_id, task_id, label_id)`: Detaches a label from a task.

## User Context Configuration
- When executing tasks on behalf of the user, check `users.json` for configured user IDs.
- Default to `Ghost` (or `admin_user`) unless instructed otherwise by the user.
