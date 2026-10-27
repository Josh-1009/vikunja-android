# Vikunja Model Context Protocol (MCP) Server with Multi-User RBAC

A production-ready Model Context Protocol (MCP) server for [Vikunja](https://vikunja.io/) with strict **Multi-User & Multi-Tenant Role-Based Access Control (RBAC)**.

Built with Python and the official `mcp` SDK (FastMCP / MCPServer), `httpx`, and `pydantic`. It enables Gemini, Claude, and other LLMs to interact with Vikunja tasks and projects without ever leaking tokens across sessions or permitting unauthorized actions across tenant boundaries.

---

## Architecture & Security Model

```
+-------------------------------------------------------------+
|                     Gemini / MCP Client                     |
+-------------------------------------------------------------+
                               |
                               | JSON-RPC (stdio or SSE)
                               | Tool call with `user_id`
                               v
+-------------------------------------------------------------+
|                     Vikunja MCP Server                      |
|                                                             |
|  1. Context & User Resolution (users.json)                 |
|     - Validates user identity                               |
|     - Fails fast with {"status": "error", "code": "UNAUTHORIZED"}
|                                                             |
|  2. Pre-Execution RBAC Checker                             |
|     - Enforces Role: viewer | contributor | admin          |
|     - Fails locally with {"status": "forbidden", ...}      |
|                                                             |
|  3. Token Redaction Engine                                  |
|     - Filters all log output to ensure no bearer tokens leak|
+-------------------------------------------------------------+
                               |
                               | HTTP (Scoped User Bearer Token)
                               v
+-------------------------------------------------------------+
|                      Vikunja REST API                       |
|                   (https://tasks.example.com)               |
+-------------------------------------------------------------+
```

---

## Role-Based Access Control (RBAC) Matrix

Every tool invocation requires a `user_id`. The server performs an explicit **pre-execution permission check** before making any external network requests to Vikunja:

| Tool | Action | Viewer | Contributor | Admin | Local Pre-Check Failure |
| :--- | :--- | :---: | :---: | :---: | :--- |
| `list_accessible_projects` | Read projects | ✅ | ✅ | ✅ | `UNAUTHORIZED` if user not found |
| `get_project_tasks` | Read tasks | ✅ | ✅ | ✅ | `UNAUTHORIZED` if user not found |
| `get_task_details` | Read task | ✅ | ✅ | ✅ | `UNAUTHORIZED` if user not found |
| `list_labels` | Read labels | ✅ | ✅ | ✅ | `UNAUTHORIZED` if user not found |
| `create_task` | Create new task | ❌ | ✅ | ✅ | `{"status": "forbidden", "message": "User role does not have write permissions."}` |
| `update_task_status` | Mark done / undone | ❌ | ✅ | ✅ | `{"status": "forbidden", "message": "User role does not have write permissions."}` |
| `assign_task` | Assign user to task | ❌ | ✅ | ✅ | `{"status": "forbidden", "message": "User role does not have write permissions."}` |
| `delete_task` | Delete task | ❌ | ❌ | ✅ | `{"status": "forbidden", "message": "User role 'contributor' does not have permission for action 'delete_task'."}` |

### Error Handling Guarantees
- **Unregistered or Missing User:** Fails fast immediately:
  ```json
  {
    "status": "error",
    "code": "UNAUTHORIZED",
    "message": "User 'unknown_user' is not authorized or not found."
  }
  ```
- **Permission Violation (e.g. Viewer calling `create_task`):** Blocked locally before network dispatch:
  ```json
  {
    "status": "forbidden",
    "message": "User role does not have write permissions."
  }
  ```
- **Vikunja API Errors:** Handled and translated cleanly:
  - `401 Unauthorized` -> `{"status": "error", "code": "VIKUNJA_UNAUTHORIZED", ...}`
  - `403 Forbidden` -> `{"status": "error", "code": "VIKUNJA_FORBIDDEN", ...}`
  - `404 Not Found` -> `{"status": "error", "code": "NOT_FOUND", ...}`
  - `429 Rate Limited` -> `{"status": "error", "code": "RATE_LIMITED", ...}`

---

## File Structure

```
├── auth.py                  # Identity mapping, RBAC engine & token redaction filter
├── vikunja_client.py        # Asynchronous HTTP client for Vikunja REST API (httpx)
├── server.py                # MCP FastMCP server with registered tools & CLI runner
├── users.example.json       # Template mapping user IDs to tokens and roles
├── users.json               # Active user configurations (gitignored in production)
├── requirements.txt         # Dependencies (mcp, httpx, python-dotenv, pydantic)
├── .env.example             # Example environment variables
├── tests/
│   └── test_server_and_rbac.py # Full unit & integration test suite (16 tests)
├── DEPLOYMENT.md            # Self-hosted VPS Docker deployment instructions for Vikunja
└── README.md                # Server documentation
```

---

## Prerequisites & Installation

### 1. Requirements
- Python 3.10+ (tested through Python 3.14)
- Running Vikunja instance (v0.24+)

### 2. Setup Virtual Environment
```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

---

## Configuration

### 1. Environment Variables (`.env`)
Copy `.env.example` or create `.env`:
```ini
# Root API endpoint for Vikunja (ends with /api/v1)
VIKUNJA_API_URL=https://tasks.yourcompany.com/api/v1

# Path to the JSON configuration file containing user credentials and roles
USERS_CONFIG_PATH=users.json

# Default transport ('stdio', 'sse', or 'streamable-http')
MCP_TRANSPORT=stdio

# Host and port when running in SSE/HTTP mode
MCP_SERVER_HOST=0.0.0.0
MCP_SERVER_PORT=8000

# Logging level (DEBUG, INFO, WARNING, ERROR)
LOG_LEVEL=INFO
```

### 2. User Credentials Mapping (`users.json`)
Copy `users.example.json` to `users.json`:
```bash
cp users.example.json users.json
```

Configure your team members, their respective Vikunja Bearer API tokens, and their permission tier:
```json
{
  "users": {
    "admin_alice": {
      "token": "vk_token_alice_secret_admin_12345",
      "role": "admin",
      "name": "Alice Admin",
      "email": "alice@example.com"
    },
    "contributor_bob": {
      "token": "vk_token_bob_secret_contrib_67890",
      "role": "contributor",
      "name": "Bob Contributor",
      "email": "bob@example.com"
    },
    "viewer_charlie": {
      "token": "vk_token_charlie_secret_viewer_54321",
      "role": "viewer",
      "name": "Charlie Viewer",
      "email": "charlie@example.com"
    }
  }
}
```

> **Security Note:** How to generate a Vikunja API token:
> 1. Log in to your Vikunja web interface.
> 2. Go to **Settings** -> **API Tokens**.
> 3. Click **Add Token**, choose desired permissions, and copy the Bearer token string into `users.json`.

---

## Running the Server

### Option A: Standard I/O Mode (Recommended for Desktop / CLI LLMs)
Runs over `stdin` and `stdout` using JSON-RPC. Diagnostics and logs are strictly sent to `stderr`:
```bash
python3 server.py --transport stdio
```

### Option B: Server-Sent Events (SSE) Mode (For Remote & Web Gateways)
Runs an HTTP server with an `/sse` endpoint and `/messages/` handler:
```bash
python3 server.py --transport sse --host 0.0.0.0 --port 8000
```
- SSE endpoint: `http://localhost:8000/sse`
- Message endpoint: `http://localhost:8000/messages/`

---

## Connecting to Gemini / MCP Clients

### 1. Gemini CLI / Desktop MCP Configuration
Add the Vikunja MCP server configuration to your MCP settings file (e.g., `gemini.json`, `claude_desktop_config.json`, or VS Code MCP settings):

#### Standard I/O (stdio)
```json
{
  "mcpServers": {
    "vikunja": {
      "command": "/home/josh/Drive_D/SAAS/Vikunja/.venv/bin/python3",
      "args": [
        "/home/josh/Drive_D/SAAS/Vikunja/server.py",
        "--transport",
        "stdio"
      ],
      "env": {
        "VIKUNJA_API_URL": "https://tasks.yourdomain.com/api/v1",
        "USERS_CONFIG_PATH": "/home/josh/Drive_D/SAAS/Vikunja/users.json",
        "LOG_LEVEL": "INFO"
      }
    }
  }
}
```

#### Remote SSE Gateway
```json
{
  "mcpServers": {
    "vikunja": {
      "url": "http://localhost:8000/sse",
      "transport": "sse"
    }
  }
}
```

---

## MCP Tools Reference

### 1. `list_accessible_projects(user_id: str)`
- **Description:** Returns only the projects visible to this user's Vikunja account.
- **Allowed Roles:** `viewer`, `contributor`, `admin`
- **Output:**
  ```json
  {
    "status": "success",
    "projects": [
      {
        "id": 1,
        "title": "Website Redesign",
        "description": "Revamp main website",
        "is_archived": false,
        "is_favorite": true
      }
    ]
  }
  ```

### 2. `get_project_tasks(user_id: str, project_id: int, filter_done: bool = False)`
- **Description:** Lists tasks for a specific project with optional done filter.
- **Allowed Roles:** `viewer`, `contributor`, `admin`
- **Parameters:**
  - `user_id` (str): Requesting user identifier.
  - `project_id` (int): ID of project.
  - `filter_done` (bool, default `False`): Returns only completed tasks if True, or open tasks if False.

### 3. `get_task_details(user_id: str, task_id: int)`
- **Description:** Fetches full details for a task by ID.
- **Allowed Roles:** `viewer`, `contributor`, `admin`

### 4. `create_task(user_id: str, project_id: int, title: str, description: str = "", due_date: str = None)`
- **Description:** Creates a task in the target project.
- **Allowed Roles:** `contributor`, `admin` (Blocked for `viewer`)
- **Parameters:**
  - `user_id` (str): Requesting user identifier.
  - `project_id` (int): Target project ID.
  - `title` (str): Task summary/name.
  - `description` (str, optional): Task notes or markdown text.
  - `due_date` (str, optional): ISO 8601 timestamp string (e.g. `2026-11-01T15:00:00Z`).

### 5. `update_task_status(user_id: str, task_id: int, done: bool)`
- **Description:** Marks a task completed or open.
- **Allowed Roles:** `contributor`, `admin` (Blocked for `viewer`)

### 6. `assign_task(user_id: str, task_id: int, target_user: str)`
- **Description:** Assigns a user to a task by user ID or username.
- **Allowed Roles:** `contributor`, `admin` (Blocked for `viewer`)
- **Parameters:**
  - `target_user` (str): Either numeric user ID (e.g. `"42"`) or username (e.g. `"alex"`).

### 7. `delete_task(user_id: str, task_id: int)`
- **Description:** Permanently deletes a task.
- **Allowed Roles:** `admin` (Blocked for `viewer` and `contributor`)

### 8. `list_labels(user_id: str)`
- **Description:** Lists labels visible to the authenticated user.
- **Allowed Roles:** `viewer`, `contributor`, `admin`

---

## Running Automated Tests

Run the comprehensive unit and integration test suite:
```bash
source .venv/bin/activate
python3 -m unittest discover -s tests -v
```

All 16 tests verify:
- Fast rejection of unknown users with `UNAUTHORIZED`.
- Local blocking of `viewer` roles attempting write operations (`create_task`, `delete_task`, `assign_task`, `update_task_status`).
- Local blocking of `contributor` roles attempting admin operations (`delete_task`).
- Proper execution of allowed operations for `contributor` and `admin`.
- Redaction of Bearer tokens from log files and stderr streams.
- Accurate mapping of Vikunja API error codes (401, 403, 404, 429).

---

## Deployment & Production Hosting

If you also need to host or maintain the Vikunja backend (PostgreSQL + Caddy Docker Compose setup), refer to [DEPLOYMENT.md](DEPLOYMENT.md) for full VPS configuration and backup instructions.
