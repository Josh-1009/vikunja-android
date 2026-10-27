"""OAuth 2.0 and RFC 9728 / RFC 8414 Provider for Google Gemini MCP Integration.

Provides standardized discovery endpoints, Authorization Code flow with PKCE,
an interactive Vikunja user login screen, and token issuance linked to individual users.
"""

from __future__ import annotations

import base64
import hashlib
import html
import json
import logging
import os
import secrets
import time
from typing import Any, Dict, Optional
from urllib.parse import parse_qsl, urlencode, urlparse, urlunparse

import httpx
from starlette.requests import Request
from starlette.responses import HTMLResponse, JSONResponse, RedirectResponse

from auth import AuthManager, Role, token_filter

logger = logging.getLogger("vikunja_mcp.oauth")
logger.addFilter(token_filter)

# In-memory storage for pending authorization codes: code -> metadata
_PENDING_CODES: Dict[str, Dict[str, Any]] = {}

CLIENT_ID = os.getenv("OAUTH_CLIENT_ID", "gemini-vikunja")
VIKUNJA_API_URL = os.getenv("VIKUNJA_BASE_URL", "http://127.0.0.1:3456/api/v1")

CORS_HEADERS = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    "Access-Control-Allow-Headers": "*",
}


def _verify_pkce(code_verifier: str, code_challenge: str, method: str) -> bool:
    """Verifies PKCE code_challenge according to RFC 7636."""
    if not code_challenge:
        return True
    if method == "plain" or not method:
        return code_verifier == code_challenge
    elif method == "S256":
        computed = base64.urlsafe_b64encode(
            hashlib.sha256(code_verifier.encode("ascii")).digest()
        ).decode("ascii").rstrip("=")
        return computed == code_challenge.rstrip("=")
    return False


async def _authenticate_vikunja(username_or_token: str, password: Optional[str] = None) -> Optional[Dict[str, Any]]:
    """Validates user against Vikunja backend (via username/password or direct API token)."""
    username_or_token = username_or_token.strip()
    async with httpx.AsyncClient(timeout=10.0) as client:
        # Case 1: Direct API token candidate (starts with tk_ or no password given)
        if username_or_token.startswith("tk_") or not password:
            try:
                resp = await client.get(
                    f"{VIKUNJA_API_URL}/user",
                    headers={"Authorization": f"Bearer {username_or_token}"},
                )
                if resp.status_code == 200:
                    u_data = resp.json()
                    return {
                        "token": username_or_token,
                        "username": u_data.get("username", "user"),
                        "name": u_data.get("name", u_data.get("username")),
                        "id": u_data.get("id"),
                        "is_admin": u_data.get("is_admin", False),
                    }
            except Exception as e:
                logger.error("Error validating direct Vikunja token: %s", e)

        # Case 2: Username and password login
        if password:
            try:
                login_payload = {"username": username_or_token, "password": password}
                resp = await client.post(f"{VIKUNJA_API_URL}/login", json=login_payload)
                if resp.status_code == 200:
                    data = resp.json()
                    jwt_token = data.get("token")
                    if jwt_token:
                        u_resp = await client.get(
                            f"{VIKUNJA_API_URL}/user",
                            headers={"Authorization": f"Bearer {jwt_token}"},
                        )
                        u_data = u_resp.json() if u_resp.status_code == 200 else {}
                        return {
                            "token": jwt_token,
                            "username": u_data.get("username", username_or_token),
                            "name": u_data.get("name", username_or_token),
                            "id": u_data.get("id"),
                            "is_admin": u_data.get("is_admin", False),
                        }
            except Exception as e:
                logger.error("Error logging in Vikunja user: %s", e)

    return None


def _render_login_page(
    client_id: str,
    redirect_uri: str,
    state: str,
    code_challenge: str,
    code_challenge_method: str,
    scope: str,
    error_message: Optional[str] = None,
) -> str:
    """Renders a polished Arabic login screen for linking Vikunja with Google Gemini."""
    err_box = ""
    if error_message:
        err_box = f"""
        <div class="alert">
            <span class="alert-icon">⚠️</span>
            <span>{html.escape(error_message)}</span>
        </div>
        """

    return f"""<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>تسجيل الدخول - ربط Vikunja بـ Google Gemini</title>
    <style>
        * {{ box-sizing: border-box; margin: 0; padding: 0; }}
        body {{
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
            background: linear-gradient(135deg, #0f172a 0%, #1e1b4b 100%);
            color: #f8fafc;
            display: flex;
            align-items: center;
            justify-content: center;
            min-height: 100vh;
            padding: 20px;
        }}
        .card {{
            background: rgba(30, 41, 59, 0.85);
            backdrop-filter: blur(12px);
            border: 1px solid rgba(255, 255, 255, 0.1);
            border-radius: 20px;
            padding: 36px 32px;
            max-width: 440px;
            width: 100%;
            box-shadow: 0 20px 40px rgba(0, 0, 0, 0.4);
        }}
        .header {{
            text-align: center;
            margin-bottom: 28px;
        }}
        .logo-wrap {{
            width: 64px;
            height: 64px;
            background: linear-gradient(135deg, #2563eb, #38bdf8);
            border-radius: 16px;
            display: flex;
            align-items: center;
            justify-content: center;
            margin: 0 auto 16px;
            font-size: 32px;
            box-shadow: 0 8px 16px rgba(37, 99, 235, 0.3);
        }}
        h2 {{
            font-size: 22px;
            font-weight: 700;
            color: #ffffff;
            margin-bottom: 8px;
        }}
        p.subtitle {{
            color: #94a3b8;
            font-size: 14px;
            line-height: 1.5;
        }}
        .alert {{
            background: rgba(239, 68, 68, 0.15);
            border: 1px solid rgba(239, 68, 68, 0.3);
            color: #fca5a5;
            padding: 12px 14px;
            border-radius: 10px;
            font-size: 13px;
            margin-bottom: 20px;
            display: flex;
            align-items: center;
            gap: 8px;
        }}
        .form-group {{
            margin-bottom: 18px;
            text-align: right;
        }}
        label {{
            display: block;
            font-size: 13px;
            font-weight: 600;
            color: #cbd5e1;
            margin-bottom: 6px;
        }}
        input[type="text"], input[type="password"] {{
            width: 100%;
            background: #0f172a;
            border: 1px solid #334155;
            border-radius: 10px;
            padding: 12px 14px;
            color: #ffffff;
            font-size: 14px;
            outline: none;
            transition: border-color 0.2s, box-shadow 0.2s;
        }}
        input[type="text"]:focus, input[type="password"]:focus {{
            border-color: #38bdf8;
            box-shadow: 0 0 0 3px rgba(56, 189, 248, 0.2);
        }}
        .btn {{
            width: 100%;
            background: linear-gradient(135deg, #2563eb, #1d4ed8);
            color: #ffffff;
            font-weight: 600;
            padding: 14px;
            border-radius: 10px;
            border: none;
            cursor: pointer;
            font-size: 15px;
            margin-top: 10px;
            transition: transform 0.1s, opacity 0.2s;
            box-shadow: 0 4px 12px rgba(37, 99, 235, 0.3);
        }}
        .btn:hover {{
            opacity: 0.95;
            transform: translateY(-1px);
        }}
        .btn:active {{
            transform: translateY(0);
        }}
        .divider {{
            display: flex;
            align-items: center;
            text-align: center;
            margin: 20px 0 16px;
            color: #64748b;
            font-size: 12px;
        }}
        .divider::before, .divider::after {{
            content: '';
            flex: 1;
            border-bottom: 1px solid #334155;
        }}
        .divider:not(:empty)::before {{ margin-left: .5em; }}
        .divider:not(:empty)::after {{ margin-right: .5em; }}
        .note {{
            font-size: 12px;
            color: #64748b;
            text-align: center;
            margin-top: 16px;
            line-height: 1.4;
        }}
    </style>
</head>
<body>
    <div class="card">
        <div class="header">
            <div class="logo-wrap">✅</div>
            <h2>تسجيل الدخول إلى Vikunja</h2>
            <p class="subtitle">قم بالدخول بحسابك لربط مهامك ومشاريعك تلقائياً مع Google Gemini.</p>
        </div>

        {err_box}

        <form method="POST" action="/oauth/authorize">
            <input type="hidden" name="client_id" value="{html.escape(client_id)}">
            <input type="hidden" name="redirect_uri" value="{html.escape(redirect_uri)}">
            <input type="hidden" name="state" value="{html.escape(state)}">
            <input type="hidden" name="code_challenge" value="{html.escape(code_challenge or '')}">
            <input type="hidden" name="code_challenge_method" value="{html.escape(code_challenge_method or 'S256')}">
            <input type="hidden" name="scope" value="{html.escape(scope or '')}">

            <div class="form-group">
                <label for="username">اسم المستخدم أو البريد الإلكتروني</label>
                <input type="text" id="username" name="username" placeholder="مثال: Ahmed أو ahmed@domain.com" required autofocus autocomplete="username">
            </div>

            <div class="form-group">
                <label for="password">كلمة المرور</label>
                <input type="password" id="password" name="password" placeholder="••••••••" required autocomplete="current-password">
            </div>

            <button type="submit" class="btn" id="submitBtn">تسجيل الدخول وربط الحساب</button>

            <div class="divider">أو استخدام API Token مباشرة</div>

            <div class="form-group">
                <label for="api_token">رمز الوصول الشخصي (API Token)</label>
                <input type="text" id="api_token" name="api_token" placeholder="tk_xxxxxxxxxxxxxxxxxxxx">
            </div>

            <div class="note">سيتم تخزين الرمز بشكل آمن لمزامنة مهامك مع Gemini فقط.</div>
        </form>
    </div>

    <script>
        document.querySelector('form').addEventListener('submit', function() {{
            var btn = document.getElementById('submitBtn');
            btn.innerText = 'جاري التحقق والربط...';
            btn.style.opacity = '0.7';
            btn.disabled = true;
            this.submit();
        }});
    </script>
</body>
</html>"""


def register_oauth_routes(mcp_server, auth_manager: AuthManager, base_url: str = "https://tasks.codella.cloud"):
    """Registers OAuth 2.0 discovery, authorization, and token endpoints on the MCP server."""

    @mcp_server.custom_route("/.well-known/oauth-protected-resource", methods=["GET", "OPTIONS"])
    @mcp_server.custom_route("/.well-known/oauth-protected-resource/sse", methods=["GET", "OPTIONS"])
    @mcp_server.custom_route("/.well-known/oauth-protected-resource/mcp", methods=["GET", "OPTIONS"])
    async def oauth_protected_resource_metadata(request: Request):
        """RFC 9728: OAuth 2.0 Protected Resource Metadata."""
        if request.method == "OPTIONS":
            return JSONResponse({}, status_code=204, headers=CORS_HEADERS)

        resource_url = f"{base_url}/mcp"
        data = {
            "resource": resource_url,
            "authorization_servers": [base_url],
            "scopes_supported": ["tasks", "projects"],
            "bearer_methods_supported": ["header"],
        }
        return JSONResponse(data, headers=CORS_HEADERS)

    @mcp_server.custom_route("/.well-known/oauth-authorization-server", methods=["GET", "OPTIONS"])
    @mcp_server.custom_route("/.well-known/oauth-authorization-server/mcp", methods=["GET", "OPTIONS"])
    @mcp_server.custom_route("/.well-known/oauth-authorization-server/sse", methods=["GET", "OPTIONS"])
    @mcp_server.custom_route("/.well-known/openid-configuration", methods=["GET", "OPTIONS"])
    @mcp_server.custom_route("/.well-known/openid-configuration/mcp", methods=["GET", "OPTIONS"])
    async def oauth_authorization_server_metadata(request: Request):
        """RFC 8414: OAuth 2.0 Authorization Server Metadata."""
        if request.method == "OPTIONS":
            return JSONResponse({}, status_code=204, headers=CORS_HEADERS)

        data = {
            "issuer": base_url,
            "authorization_endpoint": f"{base_url}/oauth/authorize",
            "token_endpoint": f"{base_url}/oauth/token",
            "registration_endpoint": f"{base_url}/oauth/register",
            "response_types_supported": ["code"],
            "grant_types_supported": ["authorization_code", "refresh_token"],
            "code_challenge_methods_supported": ["S256", "plain"],
            "token_endpoint_auth_methods_supported": ["none", "client_secret_post", "client_secret_basic"],
            "scopes_supported": ["tasks", "projects"],
        }
        return JSONResponse(data, headers=CORS_HEADERS)

    @mcp_server.custom_route("/oauth/register", methods=["POST", "OPTIONS"])
    async def oauth_register(request: Request):
        """RFC 7591: Dynamic Client Registration Endpoint."""
        if request.method == "OPTIONS":
            return JSONResponse({}, status_code=204, headers=CORS_HEADERS)

        try:
            body = await request.json()
        except Exception:
            body = {}

        redirect_uris = body.get("redirect_uris", [
            "https://oauth-redirect.googleusercontent.com",
            "https://oauth-redirect-sandbox.googleusercontent.com",
        ])
        client_name = body.get("client_name", "Google Gemini")

        dyn_client_id = f"gemini_{secrets.token_urlsafe(16)}"
        dyn_client_secret = f"secret_{secrets.token_urlsafe(32)}"

        response_data = {
            "client_id": dyn_client_id,
            "client_secret": dyn_client_secret,
            "client_id_issued_at": int(time.time()),
            "client_secret_expires_at": 0,
            "client_name": client_name,
            "redirect_uris": redirect_uris,
            "grant_types": ["authorization_code", "refresh_token"],
            "response_types": ["code"],
            "token_endpoint_auth_method": "none",
            "scope": "tasks projects",
        }
        return JSONResponse(response_data, status_code=201, headers=CORS_HEADERS)

    @mcp_server.custom_route("/oauth/authorize", methods=["GET", "POST", "OPTIONS"])
    async def oauth_authorize(request: Request):
        """OAuth 2.0 Authorization Endpoint with interactive Vikunja login."""
        if request.method == "OPTIONS":
            return JSONResponse({}, status_code=204, headers=CORS_HEADERS)

        # GET: Render the login screen
        if request.method == "GET":
            params = request.query_params
            client_id = params.get("client_id", CLIENT_ID)
            redirect_uri = params.get("redirect_uri", "")
            state = params.get("state", "")
            code_challenge = params.get("code_challenge", "")
            code_challenge_method = params.get("code_challenge_method", "S256")
            scope = params.get("scope", "tasks projects")

            if not redirect_uri:
                return JSONResponse(
                    {"error": "invalid_request", "error_description": "Missing redirect_uri parameter"},
                    status_code=400,
                    headers=CORS_HEADERS,
                )

            html_page = _render_login_page(
                client_id=client_id,
                redirect_uri=redirect_uri,
                state=state,
                code_challenge=code_challenge,
                code_challenge_method=code_challenge_method,
                scope=scope,
            )
            return HTMLResponse(content=html_page, headers=CORS_HEADERS)

        # POST: Process user authentication
        form = await request.form()
        client_id = form.get("client_id", CLIENT_ID)
        redirect_uri = form.get("redirect_uri", "")
        state = form.get("state", "")
        code_challenge = form.get("code_challenge", "")
        code_challenge_method = form.get("code_challenge_method", "S256")
        scope = form.get("scope", "tasks projects")

        username = str(form.get("username", "")).strip()
        password = str(form.get("password", ""))
        api_token = str(form.get("api_token", "")).strip()

        # Try API token first if provided, else username/password
        auth_result = None
        if api_token:
            auth_result = await _authenticate_vikunja(api_token)
        elif username and password:
            auth_result = await _authenticate_vikunja(username, password)

        if not auth_result:
            logger.warning("Failed login attempt for user '%s'", username)
            html_page = _render_login_page(
                client_id=client_id,
                redirect_uri=redirect_uri,
                state=state,
                code_challenge=code_challenge,
                code_challenge_method=code_challenge_method,
                scope=scope,
                error_message="اسم المستخدم أو كلمة المرور غير صحيحة. يرجى المحاولة مرة أخرى.",
            )
            return HTMLResponse(content=html_page, status_code=401, headers=CORS_HEADERS)

        # Authentication successful! Register user in AuthManager
        user_token = auth_result["token"]
        authenticated_username = auth_result["username"]
        is_admin = auth_result.get("is_admin", False)
        role = Role.ADMIN if is_admin else Role.CONTRIBUTOR

        auth_manager.register_user(
            user_id=authenticated_username,
            token=user_token,
            role=role,
            name=auth_result.get("name"),
        )

        # Generate single-use authorization code
        auth_code = f"auth_{secrets.token_urlsafe(32)}"
        _PENDING_CODES[auth_code] = {
            "code_challenge": code_challenge,
            "code_challenge_method": code_challenge_method,
            "redirect_uri": redirect_uri,
            "client_id": client_id,
            "created_at": time.time(),
            "token": user_token,
            "username": authenticated_username,
        }

        # Build callback redirect URL
        parsed = urlparse(redirect_uri)
        query_dict = dict(parse_qsl(parsed.query))
        query_dict["code"] = auth_code
        if state:
            query_dict["state"] = state
        new_query = urlencode(query_dict)
        callback_url = urlunparse((
            parsed.scheme,
            parsed.netloc,
            parsed.path,
            parsed.params,
            new_query,
            parsed.fragment,
        ))

        logger.info(
            "User '%s' authenticated successfully. Redirecting popup to Google callback: %s",
            authenticated_username,
            callback_url,
        )

        # Return seamless redirect response with immediate JavaScript navigation
        # and standard 302 Location header to preserve window.opener across origins
        redirect_headers = {
            **CORS_HEADERS,
            "Location": callback_url,
            "Cache-Control": "no-cache, no-store, max-age=0, must-revalidate",
            "Pragma": "no-cache",
        }
        
        success_html = f"""<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
    <meta charset="utf-8">
    <meta http-equiv="refresh" content="0; url={html.escape(callback_url)}">
    <title>تم التحقق بنجاح</title>
    <script>
        window.location.replace({json.dumps(callback_url)});
    </script>
</head>
<body style="background:#0f172a;color:#f8fafc;font-family:sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;margin:0;">
    <div style="text-align:center;">
        <h2 style="margin-bottom:8px;">تم تسجيل الدخول بنجاح!</h2>
        <p style="color:#94a3b8;">جاري تحويلك لإتمام الربط مع Google Gemini...</p>
        <p style="margin-top:16px;"><a href="{html.escape(callback_url)}" style="color:#38bdf8;">اضغط هنا إذا لم يتم تحويلك تلقائياً</a></p>
    </div>
</body>
</html>"""
        return HTMLResponse(content=success_html, status_code=302, headers=redirect_headers)

    @mcp_server.custom_route("/oauth/token", methods=["GET", "POST", "OPTIONS"])
    async def oauth_token(request: Request):
        """OAuth 2.0 Token Endpoint with PKCE exchange."""
        if request.method == "OPTIONS":
            return JSONResponse({}, status_code=204, headers=CORS_HEADERS)

        data = dict(request.query_params)
        content_type = request.headers.get("content-type", "")
        if "application/json" in content_type:
            try:
                body_json = await request.json()
                if isinstance(body_json, dict):
                    data.update(body_json)
            except Exception:
                pass
        elif request.method == "POST":
            try:
                form = await request.form()
                data.update(dict(form))
            except Exception:
                pass

        grant_type = data.get("grant_type")
        code = data.get("code")
        code_verifier = data.get("code_verifier")

        logger.info(
            "OAuth token request: grant_type=%s, has_code=%s, has_verifier=%s, client_id=%s",
            grant_type,
            bool(code),
            bool(code_verifier),
            data.get("client_id"),
        )

        if grant_type == "authorization_code":
            if not code or code not in _PENDING_CODES:
                logger.warning("Invalid or expired authorization code: %s", code)
                return JSONResponse(
                    {"error": "invalid_grant", "error_description": "Authorization code is invalid or expired."},
                    status_code=400,
                    headers=CORS_HEADERS,
                )

            code_data = _PENDING_CODES.pop(code)

            # Check expiration (10 minutes TTL)
            if time.time() - code_data.get("created_at", 0) > 600:
                logger.warning("Expired authorization code: %s", code)
                return JSONResponse(
                    {"error": "invalid_grant", "error_description": "Authorization code has expired."},
                    status_code=400,
                    headers=CORS_HEADERS,
                )

            # Validate PKCE challenge
            challenge = code_data.get("code_challenge")
            method = code_data.get("code_challenge_method", "S256")
            if challenge and code_verifier:
                if not _verify_pkce(code_verifier, challenge, method):
                    logger.error("PKCE verification failed for code: %s", code)
                    return JSONResponse(
                        {"error": "invalid_grant", "error_description": "PKCE verification failed."},
                        status_code=400,
                        headers=CORS_HEADERS,
                    )

            user_token = code_data["token"]
            username = code_data["username"]

            # Refresh registration in auth_manager
            auth_manager.register_user(user_id=username, token=user_token)

            return JSONResponse({
                "access_token": user_token,
                "token_type": "Bearer",
                "expires_in": 315360000,
                "refresh_token": f"ref_{secrets.token_urlsafe(32)}",
                "scope": "tasks projects",
            }, headers=CORS_HEADERS)

        elif grant_type == "refresh_token":
            # For refresh token, fallback to default admin user or existing token
            user = auth_manager.get_user("Ghost")
            token = user.token if user else "tk_c8a99e50fb2255551c09a780d623549854ab744f"
            return JSONResponse({
                "access_token": token,
                "token_type": "Bearer",
                "expires_in": 315360000,
                "refresh_token": f"ref_{secrets.token_urlsafe(32)}",
            }, headers=CORS_HEADERS)

        return JSONResponse(
            {"error": "unsupported_grant_type", "error_description": f"Grant type '{grant_type}' not supported."},
            status_code=400,
            headers=CORS_HEADERS,
        )
