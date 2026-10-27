package cloud.codella.vikunja.presentation.security

import android.app.Activity
import android.util.Log
import android.view.WindowManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import cloud.codella.vikunja.core.security.SecurityPreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Manages the smart lock lifecycle, background timeout calculations, and Window Privacy (FLAG_SECURE).
 */
class AppLockManager(
    private val securityPreferencesManager: SecurityPreferencesManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) : DefaultLifecycleObserver {

    companion object {
        private const val TAG = "AppLockManager"
    }

    private val _isAppLocked = MutableStateFlow(false)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    var isAuthenticating: Boolean = false
    private var lastBackgroundTimeMillis: Long = 0L

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        if (isAuthenticating) {
            Log.d(TAG, "onStop triggered by system BiometricPrompt/Credential dialog — ignoring background timer")
            return
        }
        lastBackgroundTimeMillis = System.currentTimeMillis()
        Log.d(TAG, "App stopped/backgrounded at $lastBackgroundTimeMillis")
        scope.launch {
            securityPreferencesManager.setLastBackgroundTimestamp(lastBackgroundTimeMillis)
            val isBioEnabled = securityPreferencesManager.isBiometricEnabled.first()
            if (isBioEnabled && owner is Activity) {
                // Blank/blur recent multitasking preview
                owner.window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
                )
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        scope.launch {
            val isBioEnabled = securityPreferencesManager.isBiometricEnabled.first()
            if (!isBioEnabled) {
                _isAppLocked.value = false
                if (owner is Activity) {
                    owner.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                return@launch
            }

            // If we were in the middle of authenticating, or if lastBackgroundTimeMillis is 0, DO NOT LOCK!
            if (isAuthenticating || lastBackgroundTimeMillis == 0L) {
                Log.d(TAG, "onStart: skipped lock because app was not backgrounded (lastBackground=0, authenticating=$isAuthenticating)")
                return@launch
            }

            val timeoutSec = securityPreferencesManager.lockTimeoutSeconds.first()
            val now = System.currentTimeMillis()
            val elapsedSeconds = (now - lastBackgroundTimeMillis) / 1000L
            Log.d(TAG, "App foregrounded: elapsed=${elapsedSeconds}s, timeout=${timeoutSec}s")

            if (elapsedSeconds >= timeoutSec) {
                _isAppLocked.value = true
                if (owner is Activity) {
                    owner.window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE
                    )
                }
            }
        }
    }

    fun checkInitialLockState() {
        scope.launch {
            if (securityPreferencesManager.isBiometricEnabled.first()) {
                _isAppLocked.value = true
            }
        }
    }

    fun unlock(activity: Activity? = null) {
        isAuthenticating = false
        _isAppLocked.value = false
        lastBackgroundTimeMillis = 0L
        scope.launch {
            securityPreferencesManager.setLastBackgroundTimestamp(0L)
        }
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        Log.d(TAG, "App successfully unlocked, lock cleared")
    }

    fun lock(activity: Activity? = null) {
        isAuthenticating = false
        _isAppLocked.value = true
        activity?.window?.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        Log.d(TAG, "App locked, FLAG_SECURE applied")
    }
}
