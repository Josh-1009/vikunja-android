"""Lightweight App Update Server for Vikunja Android Client.

Provides:
- GET /api/v1/app/check-update: Version checks, changelogs, and APK download links.
- GET /downloads/vikunja-app.apk: Static APK downloads for in-app self-updates.
"""

from __future__ import annotations

import json
import os
from pathlib import Path
from http.server import HTTPServer, SimpleHTTPRequestHandler
import urllib.parse

# Default release metadata
UPDATE_INFO = {
    "latest_version_name": os.getenv("APP_LATEST_VERSION_NAME", "1.1.0"),
    "latest_version_code": int(os.getenv("APP_LATEST_VERSION_CODE", "2")),
    "download_url": os.getenv(
        "APP_DOWNLOAD_URL",
        "https://github.com/Josh-1009/vikunja-android/releases/latest/download/app-release.apk"
    ),
    "changelog": os.getenv(
        "APP_CHANGELOG",
        "• Added custom date & time selection for rescheduling\n"
        "• Direct Google Calendar integration\n"
        "• Fixed HTML/markdown tag display in task descriptions\n"
        "• Optimized list rendering and eliminated scroll lag\n"
        "• Fixed full-screen and heads-up pop-up reminders\n"
        "• In-app self-update channel"
    ),
    "mandatory": os.getenv("APP_UPDATE_MANDATORY", "false").lower() == "true",
}

class AppUpdateRequestHandler(SimpleHTTPRequestHandler):
    """Handles /api/v1/app/check-update and static APK download requests."""

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path

        if path in ("/api/v1/app/check-update", "/api/v1/app/check-update/"):
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(json.dumps(UPDATE_INFO, indent=2).encode("utf-8"))
            return

        # Serve static APKs from android/app/build/outputs/apk/release/ if requested
        if path.startswith("/downloads/"):
            filename = Path(path).name
            apk_dir = Path(__file__).parent / "android" / "app" / "build" / "outputs" / "apk" / "release"
            apk_path = apk_dir / filename

            if not apk_path.exists():
                # Fallback to any .apk in release folder
                apks = list(apk_dir.glob("*.apk")) if apk_dir.exists() else []
                if apks:
                    apk_path = apks[0]

            if apk_path.exists():
                self.send_response(200)
                self.send_header("Content-Type", "application/vnd.android.package-archive")
                self.send_header("Content-Length", str(apk_path.stat().st_size))
                self.send_header("Content-Disposition", f'attachment; filename="{filename}"')
                self.end_headers()
                with open(apk_path, "rb") as f:
                    self.wfile.write(f.read())
                return

        super().do_GET()


def run(host: str = "0.0.0.0", port: int = 8088):
    server = HTTPServer((host, port), AppUpdateRequestHandler)
    print(f"App Update Server listening on http://{host}:{port}/api/v1/app/check-update")
    server.serve_forever()


if __name__ == "__main__":
    port = int(os.getenv("UPDATE_SERVER_PORT", "8088"))
    run(port=port)
