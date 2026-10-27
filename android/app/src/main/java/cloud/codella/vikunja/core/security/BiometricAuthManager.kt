package cloud.codella.vikunja.core.security

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

enum class BiometricStatus {
    AVAILABLE,
    NOT_ENROLLED,
    NO_HARDWARE,
    UNAVAILABLE
}

/**
 * Handles biometric hardware validation and prompt execution with Device PIN / Pattern fallback.
 */
class BiometricAuthManager(private val context: Context) {

    companion object {
        private const val TAG = "BiometricAuthManager"
    }

    /**
     * Checks whether biometric hardware or device credential authentication is available on the device.
     */
    fun canAuthenticate(): BiometricStatus {
        val biometricManager = BiometricManager.from(context)
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isDeviceSecure = keyguardManager?.isDeviceSecure == true

        // Check both Strong and Weak biometrics for broad Android 10+ hardware support
        val canBioStrong = biometricManager.canAuthenticate(BIOMETRIC_STRONG)
        val canBioWeak = biometricManager.canAuthenticate(BIOMETRIC_WEAK)

        val hasBiometrics = canBioStrong == BiometricManager.BIOMETRIC_SUCCESS || 
                            canBioWeak == BiometricManager.BIOMETRIC_SUCCESS

        return when {
            hasBiometrics -> BiometricStatus.AVAILABLE
            isDeviceSecure -> BiometricStatus.AVAILABLE // PIN/Pattern fallback is available!
            canBioStrong == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED || 
            canBioWeak == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NOT_ENROLLED
            canBioStrong == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE && !isDeviceSecure -> BiometricStatus.NO_HARDWARE
            else -> BiometricStatus.UNAVAILABLE
        }
    }

    /**
     * Launches the system BiometricPrompt with PIN / Pattern fallback.
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "Unlock Vikunja",
        subtitle: String = "Confirm biometric or device credential to continue",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            promptInfoBuilder.setAllowedAuthenticators(
                BIOMETRIC_STRONG or BIOMETRIC_WEAK or DEVICE_CREDENTIAL
            )
        } else {
            @Suppress("DEPRECATION")
            promptInfoBuilder.setDeviceCredentialAllowed(true)
        }
        val promptInfo = promptInfoBuilder.build()

        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Log.d(TAG, "Biometric authentication succeeded")
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Log.w(TAG, "Biometric authentication error ($errorCode): $errString")
                    onError(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Log.w(TAG, "Biometric authentication failed (fingerprint/face not recognized)")
                }
            }
        )

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Exception while launching BiometricPrompt: ${e.message}", e)
            onError(e.message ?: "Authentication initialization error")
        }
    }
}
