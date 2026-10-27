package cloud.codella.vikunja.ui.screens.share

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import cloud.codella.vikunja.VikunjaApp
import cloud.codella.vikunja.domain.model.Task
import cloud.codella.vikunja.ui.theme.VikunjaTheme
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

class ShareTargetActivity : ComponentActivity() {

    companion object {
        private val URL_PATTERN = Pattern.compile("https?://\\S+")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as? VikunjaApp
        if (app == null || !app.tokenManager.isLoggedIn()) {
            Toast.makeText(this, "Please log in to Vikunja first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val (initialTitle, initialDescription, imageUris) = parseShareIntent(intent)

        setContent {
            val themeMode by app.preferencesManager.themeModeFlow.collectAsState(initial = "system")
            val projects by app.cleanProjectRepository.getProjectsFlow().collectAsState(initial = emptyList())

            VikunjaTheme(themeMode = themeMode) {
                ShareTargetDialog(
                    initialTitle = initialTitle,
                    initialDescription = initialDescription,
                    imageUris = imageUris,
                    projects = projects,
                    onSave = { title, description, projectId, priority ->
                        window.decorView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        lifecycleScope.launch {
                            try {
                                val newTask = Task(
                                    id = 0L,
                                    title = title,
                                    description = description,
                                    projectId = projectId,
                                    priority = priority
                                )
                                app.cleanTaskRepository.createTask(newTask)
                                cloud.codella.vikunja.data.work.SyncWorkScheduler.triggerExpeditedPush(applicationContext)
                                Toast.makeText(
                                    this@ShareTargetActivity,
                                    "Saved task to Vikunja!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } catch (e: Exception) {
                                Toast.makeText(
                                    this@ShareTargetActivity,
                                    "Failed to save task: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } finally {
                                finish()
                            }
                        }
                    },
                    onDismiss = {
                        finish()
                    }
                )
            }
        }
    }

    private fun parseShareIntent(intent: Intent?): Triple<String, String, List<Uri>> {
        if (intent == null) return Triple("", "", emptyList())

        var title = ""
        var description = ""
        val imageUris = mutableListOf<Uri>()

        val extraSubject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        val extraText = intent.getStringExtra(Intent.EXTRA_TEXT)

        when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("image/") == true) {
                    val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri
                    }
                    if (streamUri != null) {
                        imageUris.add(streamUri)
                    }
                }

                if (!extraText.isNullOrBlank()) {
                    val matcher = URL_PATTERN.matcher(extraText)
                    if (matcher.find()) {
                        val extractedUrl = matcher.group()
                        val textWithoutUrl = extraText.replace(extractedUrl, "").trim()

                        title = when {
                            !extraSubject.isNullOrBlank() -> extraSubject.trim()
                            textWithoutUrl.isNotBlank() -> textWithoutUrl
                            else -> {
                                val uri = runCatching { Uri.parse(extractedUrl) }.getOrNull()
                                uri?.host?.removePrefix("www.")?.let { "Link from $it" } ?: "Shared Link"
                            }
                        }
                        description = extractedUrl
                    } else {
                        title = extraSubject?.takeIf { it.isNotBlank() } ?: extraText.take(60).trim()
                        description = if (extraText != title) extraText else ""
                    }
                } else if (!extraSubject.isNullOrBlank()) {
                    title = extraSubject.trim()
                } else if (imageUris.isNotEmpty()) {
                    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM dd, HH:mm"))
                    title = "Shared Photo ($timestamp)"
                }
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                if (intent.type?.startsWith("image/") == true) {
                    val streamUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)?.filterIsInstance<Uri>()
                    }
                    if (!streamUris.isNullOrEmpty()) {
                        imageUris.addAll(streamUris)
                    }
                    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM dd, HH:mm"))
                    title = "Shared Photos (${imageUris.size}) - $timestamp"
                }
            }
        }

        return Triple(title, description, imageUris)
    }
}
