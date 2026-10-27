package cloud.codella.vikunja.core.security

import android.app.KeyguardManager
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import io.mockk.*
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class BiometricAuthManagerTest {

    private val context = mockk<Context>(relaxed = true)
    private val keyguardManager = mockk<KeyguardManager>(relaxed = true)
    private val biometricManager = mockk<BiometricManager>(relaxed = true)
    private lateinit var authManager: BiometricAuthManager

    @Before
    fun setUp() {
        mockkStatic(BiometricManager::class)
        every { BiometricManager.from(context) } returns biometricManager
        every { context.getSystemService(Context.KEYGUARD_SERVICE) } returns keyguardManager
        authManager = BiometricAuthManager(context)
    }

    @Test
    fun `canAuthenticate returns AVAILABLE when BIOMETRIC_STRONG is available`() {
        every { biometricManager.canAuthenticate(BIOMETRIC_STRONG) } returns BiometricManager.BIOMETRIC_SUCCESS
        every { biometricManager.canAuthenticate(BIOMETRIC_WEAK) } returns BiometricManager.BIOMETRIC_SUCCESS
        every { keyguardManager.isDeviceSecure } returns true

        val status = authManager.canAuthenticate()

        assertEquals(BiometricStatus.AVAILABLE, status)
    }

    @Test
    fun `canAuthenticate returns AVAILABLE when only BIOMETRIC_WEAK is available (e g Android 10 OEM sensor)`() {
        every { biometricManager.canAuthenticate(BIOMETRIC_STRONG) } returns BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
        every { biometricManager.canAuthenticate(BIOMETRIC_WEAK) } returns BiometricManager.BIOMETRIC_SUCCESS
        every { keyguardManager.isDeviceSecure } returns false

        val status = authManager.canAuthenticate()

        assertEquals(BiometricStatus.AVAILABLE, status)
    }

    @Test
    fun `canAuthenticate returns AVAILABLE when device has PIN or pattern even if no biometric hardware`() {
        every { biometricManager.canAuthenticate(BIOMETRIC_STRONG) } returns BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
        every { biometricManager.canAuthenticate(BIOMETRIC_WEAK) } returns BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
        every { keyguardManager.isDeviceSecure } returns true

        val status = authManager.canAuthenticate()

        assertEquals(BiometricStatus.AVAILABLE, status)
    }

    @Test
    fun `canAuthenticate returns NOT_ENROLLED when neither biometrics nor device lock are set`() {
        every { biometricManager.canAuthenticate(BIOMETRIC_STRONG) } returns BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
        every { biometricManager.canAuthenticate(BIOMETRIC_WEAK) } returns BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
        every { keyguardManager.isDeviceSecure } returns false

        val status = authManager.canAuthenticate()

        assertEquals(BiometricStatus.NOT_ENROLLED, status)
    }

    @Test
    fun `canAuthenticate returns NO_HARDWARE when no biometrics and device lock is not set`() {
        every { biometricManager.canAuthenticate(BIOMETRIC_STRONG) } returns BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
        every { biometricManager.canAuthenticate(BIOMETRIC_WEAK) } returns BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
        every { keyguardManager.isDeviceSecure } returns false

        val status = authManager.canAuthenticate()

        assertEquals(BiometricStatus.NO_HARDWARE, status)
    }
}
