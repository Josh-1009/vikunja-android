package cloud.codella.vikunja.core.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.securityDataStore: DataStore<Preferences> by preferencesDataStore(name = "vikunja_security_prefs")

/**
 * Manages biometric lock preferences and auto-lock timeout configurations.
 */
class SecurityPreferencesManager(private val context: Context) {

    companion object {
        val KEY_BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val KEY_LOCK_TIMEOUT_SECONDS = intPreferencesKey("lock_timeout_seconds")
        val KEY_LAST_BACKGROUND_TIMESTAMP = longPreferencesKey("last_background_timestamp")
    }

    val isBiometricEnabled: Flow<Boolean> = context.securityDataStore.data.map { prefs ->
        prefs[KEY_BIOMETRIC_ENABLED] ?: false
    }

    val lockTimeoutSeconds: Flow<Int> = context.securityDataStore.data.map { prefs ->
        prefs[KEY_LOCK_TIMEOUT_SECONDS] ?: 0
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.securityDataStore.edit { it[KEY_BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun setLockTimeoutSeconds(seconds: Int) {
        context.securityDataStore.edit { it[KEY_LOCK_TIMEOUT_SECONDS] = seconds }
    }

    suspend fun setLastBackgroundTimestamp(timestamp: Long) {
        context.securityDataStore.edit { it[KEY_LAST_BACKGROUND_TIMESTAMP] = timestamp }
    }

    suspend fun getLastBackgroundTimestamp(): Long {
        return context.securityDataStore.data.first()[KEY_LAST_BACKGROUND_TIMESTAMP] ?: 0L
    }
}
