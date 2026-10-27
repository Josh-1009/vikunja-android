package cloud.codella.vikunja.presentation.security

import android.view.Window
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import cloud.codella.vikunja.core.security.SecurityPreferencesManager
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppLockManagerTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val securityPreferencesManager = mockk<SecurityPreferencesManager>(relaxed = true)
    private val mockActivity = mockk<FragmentActivity>(relaxed = true)
    private val mockWindow = mockk<Window>(relaxed = true)
    private val mockLifecycleOwner = mockk<LifecycleOwner>(relaxed = true)

    private lateinit var appLockManager: AppLockManager

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any<String>()) } returns 0
        every { mockActivity.window } returns mockWindow

        appLockManager = AppLockManager(
            securityPreferencesManager = securityPreferencesManager,
            scope = testScope
        )
    }

    @Test
    fun `initial state is unlocked`() {
        assertFalse(appLockManager.isAppLocked.value)
    }

    @Test
    fun `lock sets isAppLocked to true and applies FLAG_SECURE`() {
        appLockManager.lock(mockActivity)

        assertTrue(appLockManager.isAppLocked.value)
        verify {
            mockWindow.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }
    }

    @Test
    fun `unlock sets isAppLocked to false and clears FLAG_SECURE`() {
        appLockManager.lock(mockActivity)
        assertTrue(appLockManager.isAppLocked.value)

        appLockManager.unlock(mockActivity)

        assertFalse(appLockManager.isAppLocked.value)
        verify { mockWindow.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    @Test
    fun `onStart does not lock if biometric is disabled`() = runTest {
        every { securityPreferencesManager.isBiometricEnabled } returns flowOf(false)

        appLockManager.onStart(mockLifecycleOwner)

        assertFalse(appLockManager.isAppLocked.value)
    }

    @Test
    fun `onStart locks if biometric enabled and timeout expired`() = runTest {
        every { securityPreferencesManager.isBiometricEnabled } returns flowOf(true)
        every { securityPreferencesManager.lockTimeoutSeconds } returns flowOf(0) // Immediate

        appLockManager.onStop(mockActivity)
        appLockManager.onStart(mockActivity)

        assertTrue(appLockManager.isAppLocked.value)
        verify {
            mockWindow.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }
    }

    @Test
    fun `onStart does not lock if biometric enabled but timeout not reached`() = runTest {
        every { securityPreferencesManager.isBiometricEnabled } returns flowOf(true)
        every { securityPreferencesManager.lockTimeoutSeconds } returns flowOf(300) // 5 minutes

        appLockManager.onStop(mockActivity)
        appLockManager.onStart(mockLifecycleOwner)

        assertFalse(appLockManager.isAppLocked.value)
    }

    @Test
    fun `onStart does not lock if lastBackgroundTimeMillis is 0L (e g after unlock)`() = runTest {
        every { securityPreferencesManager.isBiometricEnabled } returns flowOf(true)
        every { securityPreferencesManager.lockTimeoutSeconds } returns flowOf(0)

        appLockManager.unlock(mockActivity)
        appLockManager.onStart(mockActivity)

        assertFalse(appLockManager.isAppLocked.value)
    }

    @Test
    fun `checkInitialLockState locks if biometric is enabled`() = runTest {
        every { securityPreferencesManager.isBiometricEnabled } returns flowOf(true)

        appLockManager.checkInitialLockState()

        assertTrue(appLockManager.isAppLocked.value)
    }

    @Test
    fun `checkInitialLockState does not lock if biometric is disabled`() = runTest {
        every { securityPreferencesManager.isBiometricEnabled } returns flowOf(false)

        appLockManager.checkInitialLockState()

        assertFalse(appLockManager.isAppLocked.value)
    }

    @Test
    fun `onStop ignores background timestamp when isAuthenticating is true`() = runTest {
        appLockManager.isAuthenticating = true

        appLockManager.onStop(mockActivity)

        coVerify(exactly = 0) { securityPreferencesManager.setLastBackgroundTimestamp(any()) }
    }

    @Test
    fun `unlock resets isAuthenticating and clears background timestamp`() = runTest {
        appLockManager.isAuthenticating = true
        appLockManager.lock(mockActivity)

        appLockManager.unlock(mockActivity)

        assertFalse(appLockManager.isAuthenticating)
        assertFalse(appLockManager.isAppLocked.value)
        coVerify { securityPreferencesManager.setLastBackgroundTimestamp(0L) }
    }
}
