package cloud.codella.vikunja.core.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttpid: okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val latestVersionName: String,
    val latestVersionCode: Int,
    val downloadUrl: String,
    val changelog: String,
    val isMandatory: Boolean = false,
    val isUpdateAvailable: Boolean = false
)

object AppUpdateManager {

    private val httpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Checks the companion server or Vikunja server endpoint for app updates.
     * Expected JSON response:
     * {
     *   "latest_version_name": "1.1.0",
     *   "latest_version_code": 2,
     *   "download_url": "https://server.domain/downloads/vikunja-app.apk",
     *   "changelog": "- New features\n- Bug fixes",
     *   "mandatory": false
     * }
     */
    suspend fun checkServerUpdate(
        serverUrl: String,
        currentVersionCode: Int
    ): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val base = if (serverUrl.startsWith("http://") || serverUrl.startsWith("https://")) {
                serverUrl.trimEnd('/')
            } else {
                "https://${serverUrl.trimEnd('/')}"
            }
            val checkUrl = "$base/api/v1/app/check-update?current_code=$currentVersionCode"

            val request = Request.Builder().url(checkUrl).build()
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Server returned HTTP ${response.code}"))
            }

            val body = response.body?.string().orEmpty()
            val json = JSONObject(body)

            val latestCode = json.optInt("latest_version_code", currentVersionCode)
            val latestName = json.optString("latest_version_name", "Unknown")
            val downloadUrl = json.optString("download_url", "")
            val changelog = json.optString("changelog", "No release notes provided.")
            val mandatory = json.optBoolean("mandatory", false)

            val isAvailable = latestCode > currentVersionCode && downloadUrl.isNotBlank()

            Result.success(
                AppUpdateInfo(
                    latestVersionName = latestName,
                    latestVersionCode = latestCode,
                    downloadUrl = downloadUrl,
                    changelog = changelog,
                    isMandatory = mandatory,
                    isUpdateAvailable = isAvailable
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Checks GitHub Releases API for latest published release APK.
     */
    suspend fun checkGitHubRelease(
        repo: String = "Josh-1009/vikunja-android",
        currentVersionName: String
    ): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val apiUrl = "https://api.github.com/repos/$repo/releases/latest"
            val request = Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "VikunjaAndroidApp")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("GitHub API HTTP ${response.code}"))
            }

            val json = JSONObject(response.body?.string().orEmpty())
            val tagName = json.optString("tag_name", "").removePrefix("v").trim()
            val changelog = json.optString("body", "Latest release from GitHub.")

            var downloadUrl = ""
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        downloadUrl = asset.optString("browser_download_url", "")
                        break
                    }
                }
            }

            val isNewer = compareVersions(tagName, currentVersionName) > 0

            Result.success(
                AppUpdateInfo(
                    latestVersionName = tagName,
                    latestVersionCode = 0,
                    downloadUrl = downloadUrl,
                    changelog = changelog,
                    isMandatory = false,
                    isUpdateAvailable = isNewer && downloadUrl.isNotBlank()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Compares semantic versions (e.g. "1.1.0" vs "1.0.0").
     */
    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").mapNotNull { it.toIntOrNull() }
        val parts2 = v2.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(parts1.size, parts2.size)

        for (i in 0 until maxLen) {
            val num1 = parts1.getOrElse(i) { 0 }
            val num2 = parts2.getOrElse(i) { 0 }
            if (num1 != num2) return num1.compareTo(num2)
        }
        return 0
    }

    /**
     * Downloads APK using Android's DownloadManager and triggers package install upon completion.
     */
    fun startDownloadAndInstall(context: Context, downloadUrl: String, versionName: String) {
        try {
            val uri = Uri.parse(downloadUrl)
            val fileName = "vikunja-v$versionName.apk"

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(uri).apply {
                setTitle("Vikunja Update v$versionName")
                setDescription("Downloading latest update...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
                setMimeType("application/vnd.android.package-archive")
            }

            val downloadId = downloadManager.enqueue(request)
            Toast.makeText(context, "Download started...", Toast.LENGTH_SHORT).show()

            // Listen for download completion
            val onCompleteReceiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                    if (id == downloadId) {
                        try {
                            context.unregisterReceiver(this)
                        } catch (_: Exception) {}

                        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
                        if (file.exists()) {
                            installApk(context, file)
                        }
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    onCompleteReceiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_EXPORTED
                )
            } else {
                context.registerReceiver(
                    onCompleteReceiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                )
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Update failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun installApk(context: Context, file: File) {
        try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open installer: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
