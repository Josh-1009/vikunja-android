package cloud.codella.vikunja.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vikunja_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        val KEY_SERVER_URL = stringPreferencesKey("server_url")
        val KEY_ALLOW_SELF_SIGNED = booleanPreferencesKey("allow_self_signed")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_LANGUAGE = stringPreferencesKey("language")
        val KEY_SYNC_INTERVAL = intPreferencesKey("sync_interval")
        val KEY_PERSISTENT_SERVICE = booleanPreferencesKey("persistent_service")
        val KEY_ACTIVE_USER_ID = longPreferencesKey("active_user_id")
        val KEY_ACTIVE_USERNAME = stringPreferencesKey("active_username")
        val KEY_DAILY_SUMMARY_TIME = stringPreferencesKey("daily_summary_time")
        val KEY_EXACT_ALARM_ENABLED = booleanPreferencesKey("exact_alarm_enabled")
        val KEY_POPUP_REMINDER_ENABLED = booleanPreferencesKey("popup_reminder_enabled")
        val KEY_PERSISTENT_NOTIF_ENABLED = booleanPreferencesKey("persistent_notif_enabled")
        val KEY_PERSISTENT_MIN_PRIORITY = intPreferencesKey("persistent_min_priority")
        val KEY_NAGGING_ALARM_ENABLED = booleanPreferencesKey("nagging_alarm_enabled")
        val KEY_MORNING_BRIEFING_ENABLED = booleanPreferencesKey("morning_briefing_enabled")
        val KEY_EVENING_REVIEW_ENABLED = booleanPreferencesKey("evening_review_enabled")
    }

    val naggingAlarmEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_NAGGING_ALARM_ENABLED] ?: true
    }

    val morningBriefingEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_MORNING_BRIEFING_ENABLED] ?: true
    }

    val eveningReviewEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_EVENING_REVIEW_ENABLED] ?: true
    }

    suspend fun setNaggingAlarmEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NAGGING_ALARM_ENABLED] = enabled }
    }

    suspend fun setMorningBriefingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_MORNING_BRIEFING_ENABLED] = enabled }
    }

    suspend fun setEveningReviewEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_EVENING_REVIEW_ENABLED] = enabled }
    }

    val popupReminderEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_POPUP_REMINDER_ENABLED] ?: true
    }

    val persistentNotifEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_PERSISTENT_NOTIF_ENABLED] ?: true
    }

    val persistentMinPriorityFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_PERSISTENT_MIN_PRIORITY] ?: 0 // 0=All, 1=Low+, 3=Medium+, 5=Urgent only
    }

    suspend fun setPopupReminderEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_POPUP_REMINDER_ENABLED] = enabled }
    }

    suspend fun setPersistentNotifEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PERSISTENT_NOTIF_ENABLED] = enabled }
    }

    suspend fun setPersistentMinPriority(minPriority: Int) {
        context.dataStore.edit { it[KEY_PERSISTENT_MIN_PRIORITY] = minPriority }
    }

    val serverUrlFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SERVER_URL] ?: "https://tasks.codella.cloud"
    }

    val allowSelfSignedFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ALLOW_SELF_SIGNED] ?: false
    }

    val themeModeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE] ?: "system"
    }

    val languageFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_LANGUAGE] ?: "system"
    }

    val syncIntervalFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_SYNC_INTERVAL] ?: 15
    }

    val persistentServiceFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_PERSISTENT_SERVICE] ?: false
    }

    val activeUserIdFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_USER_ID] ?: 0L
    }

    val activeUsernameFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_USERNAME] ?: ""
    }

    val dailySummaryTimeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DAILY_SUMMARY_TIME] ?: "09:00"
    }

    suspend fun setServerUrl(url: String) {
        context.dataStore.edit { it[KEY_SERVER_URL] = url.trimEnd('/') }
    }

    suspend fun setAllowSelfSigned(allow: Boolean) {
        context.dataStore.edit { it[KEY_ALLOW_SELF_SIGNED] = allow }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode }
    }

    suspend fun setLanguage(lang: String) {
        context.dataStore.edit { it[KEY_LANGUAGE] = lang }
    }

    suspend fun setSyncInterval(minutes: Int) {
        context.dataStore.edit { it[KEY_SYNC_INTERVAL] = minutes }
    }

    suspend fun setPersistentService(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PERSISTENT_SERVICE] = enabled }
    }

    suspend fun setActiveUser(id: Long, username: String) {
        context.dataStore.edit {
            it[KEY_ACTIVE_USER_ID] = id
            it[KEY_ACTIVE_USERNAME] = username
        }
    }

    suspend fun clearUser() {
        context.dataStore.edit {
            it.remove(KEY_ACTIVE_USER_ID)
            it.remove(KEY_ACTIVE_USERNAME)
        }
    }
}
