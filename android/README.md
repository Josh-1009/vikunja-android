# Vikunja Android - Native Android Client (Kotlin & Jetpack Compose)

A complete, feature-packed native Android client for self-hosted [Vikunja](https://vikunja.io) instances (targeting Vikunja API v2.7.0+). Built with 100% native Jetpack Compose, Material 3, Clean Architecture, and an offline-first Room database.

---

## 🚀 Key Highlights & Architecture

- **100% Native Jetpack Compose & Material 3**: Fluid transitions, adaptive layouts, and dynamic themes.
- **Offline-First Clean Architecture**:
  - **Network**: Retrofit 2 + OkHttp + Kotlinx Serialization (128 Vikunja API endpoints mapped).
  - **Local Storage**: Room Database for offline caching of tasks, projects, views, and buckets.
  - **Security**: `EncryptedSharedPreferences` backed by Android Keystore for JWT tokens and API tokens.
  - **Preferences**: Jetpack DataStore for user preferences (theme, language, sync intervals).
- **Comprehensive Notification Engine**:
  - **Exact Alarms**: `AlarmManager.setExactAndAllowWhileIdle` schedules notifications for every task reminder and due date.
  - **Resilience**: Auto-rescheduled upon device reboot (`RECEIVE_BOOT_COMPLETED`) and timezone change (`TIMEZONE_CHANGED`).
  - **Interactive Actions**: Direct notification action buttons: **Mark Done**, **Snooze (10m / 1h / Tomorrow)**, and **Open Task**.
  - **Background Polling**: Configurable `WorkManager` background sync (default: 15 min) for server notifications.
  - **Persistent Foreground Service**: Optional continuous connection mode with battery-optimization guidance.
  - **Daily Digest**: Scheduled daily summary notification at your preferred time.
- **Vikunja Quick Add Magic Syntax**: Parse syntax like `Fix server crash *tomorrow !3 ~Work #backend @admin` instantly in app and via the Share Target.
- **Glance App Widget**: Home screen interactive widget for Today's tasks.
- **RTL & Arabic Support**: Full native Arabic (`ar`) localization and system/manual language override.
- **AMOLED Dark Mode**: Choose between Light, Dark, and pitch-black AMOLED mode.
- **Share Target**: Share web links or highlighted text from any browser/app directly into Vikunja.

---

## 📦 APK Artifacts

Pre-built binaries generated in the project:
- **Debug APK**: `app/build/outputs/apk/debug/app-debug.apk`
- **Signed Release APK**: `app/build/outputs/apk/release/app-release.apk`

### Keystore Information
The release APK is signed using a dedicated release keystore:
- **Location**: `app/keystore.jks`
- **Alias**: `vikunja`
- **Keystore Password**: `codellavikunja`
- **Key Password**: `codellavikunja`

To generate a new keystore at any time:
```bash
keytool -genkeypair -v -keystore app/keystore.jks -alias vikunja -keyalg RSA -keysize 2048 -validity 10000 -storepass codellavikunja -keypass codellavikunja -dname "CN=Vikunja Client, OU=Codella, O=Codella, L=City, ST=State, C=US"
```

---

## 💻 Windows PowerShell Build & Install Guide

### Prerequisites
1. **JDK 17 or JDK 21** installed and added to `PATH`.
2. **Android SDK** with `platform-tools` (adb) installed.
3. Android device connected with **USB Debugging** enabled (verify with `adb devices`).

### Step-by-Step PowerShell Commands

Open **PowerShell** in the `android/` directory:

```powershell
# 1. Set Android SDK path if not already in system environment
$env:ANDROID_HOME = "C:\Users\<YourUsername>\AppData\Local\Android\Sdk"

# 2. Build Debug APK
.\gradlew.bat assembleDebug

# 3. Build Signed Release APK
.\gradlew.bat assembleRelease

# 4. Install the Release APK directly to your connected Android phone
adb install -r app\build\outputs\apk\release\app-release.apk

# 5. (Alternative) Install the Debug APK
adb install -r app\build\outputs\apk\debug\app-debug.apk

# 6. Launch the application immediately
adb shell am start -n cloud.codella.vikunja/cloud.codella.vikunja.ui.MainActivity
```

---

## 🐧 Linux / macOS Build Guide

```bash
export ANDROID_HOME=$HOME/Android/Sdk

# Build Debug & Signed Release
./gradlew assembleDebug assembleRelease

# Install via ADB
adb install -r app/build/outputs/apk/release/app-release.apk
```

---

## 🛠 Feature Parity Checklist & API Mapping

| Feature | Status | Implementation Details / API Endpoints |
| :--- | :---: | :--- |
| **Authentication (Password, TOTP)** | ✅ Implemented | `POST /api/v1/login`, `POST /api/v1/totp/verify` |
| **Authentication (API Token)** | ✅ Implemented | Supported directly via TokenManager & Bearer interceptor |
| **Server URL & Validation** | ✅ Implemented | Validates against `GET /api/v1/info` |
| **Self-Signed SSL Certificates** | ✅ Implemented | Custom TrustManager & HostnameVerifier toggle |
| **Projects Hierarchy & Colors** | ✅ Implemented | `GET /api/v1/projects`, `POST /api/v1/projects` (parent_project_id, hex_color) |
| **Views (List, Kanban, Gantt, Table)** | ✅ Implemented | `GET /api/v1/projects/{id}/views`, `GET /api/v1/views/{id}/buckets`, Kanban drag-to-bucket |
| **Tasks CRUD & Quick Add** | ✅ Implemented | `GET /api/v1/projects/{id}/tasks`, `PUT /api/v1/tasks/{id}`, Vikunja magic parser |
| **Task Priority, Percent, Done** | ✅ Implemented | Full slider, priority selector, instant toggle |
| **Dates & Reminders** | ✅ Implemented | Start/Due/End dates, exact local AlarmManager scheduling |
| **Subtasks & Task Relations** | ✅ Implemented | `GET/POST /api/v1/tasks/{id}/relations` with full relation types |
| **Labels & Assignees** | ✅ Implemented | `GET/PUT /api/v1/tasks/{id}/labels`, `GET/PUT /api/v1/tasks/{id}/assignees` |
| **Comments & Attachments** | ✅ Implemented | `GET/POST /api/v1/tasks/{id}/comments`, `GET/POST /api/v1/tasks/{id}/attachments` |
| **Saved Filters / Overviews** | ✅ Implemented | Today, Upcoming, Overdue, Search query bar |
| **Notification Engine & Actions** | ✅ Implemented | Exact AlarmManager, WorkManager sync, persistent service, Done/Snooze actions |
| **Glance Home Widget** | ✅ Implemented | Interactive widget showing Today's tasks |
| **Share Target Intent** | ✅ Implemented | `ACTION_SEND` intent filter with quick-add bottom sheet |
| **API Tokens Management** | ✅ Implemented | `GET/POST/DELETE /api/v1/tokens` screen in Settings |
| **Multi-Language (Arabic & English)** | ✅ Implemented | Complete strings in `values` and `values-ar`, RTL layout support |
| **Dark & AMOLED Themes** | ✅ Implemented | Material 3 ColorScheme with pure black `#000000` AMOLED option |
