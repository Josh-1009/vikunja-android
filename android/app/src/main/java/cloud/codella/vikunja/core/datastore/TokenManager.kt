package cloud.codella.vikunja.core.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenManager(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "vikunja_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        context.getSharedPreferences("vikunja_secure_prefs_fallback", Context.MODE_PRIVATE)
    }

    companion object {
        private const val KEY_JWT_TOKEN = "jwt_token"
        private const val KEY_API_TOKEN = "api_token"
        private const val KEY_AUTH_TYPE = "auth_type" // "jwt" or "api_token"
    }

    fun saveToken(token: String, isApiToken: Boolean = false) {
        val cleanToken = if (token.startsWith("Bearer ", ignoreCase = true)) {
            token.substring(7).trim()
        } else {
            token.trim()
        }
        prefs.edit().apply {
            if (isApiToken) {
                putString(KEY_API_TOKEN, cleanToken)
                putString(KEY_AUTH_TYPE, "api_token")
            } else {
                putString(KEY_JWT_TOKEN, cleanToken)
                putString(KEY_AUTH_TYPE, "jwt")
            }
        }.commit()
    }

    fun isApiToken(): Boolean {
        return prefs.getString(KEY_AUTH_TYPE, "jwt") == "api_token"
    }

    fun getToken(): String? {
        val authType = prefs.getString(KEY_AUTH_TYPE, "jwt")
        return if (authType == "api_token") {
            prefs.getString(KEY_API_TOKEN, null)
        } else {
            prefs.getString(KEY_JWT_TOKEN, null)
        }
    }

    fun isLoggedIn(): Boolean {
        return !getToken().isNullOrBlank()
    }

    fun clear() {
        prefs.edit().clear().commit()
    }
}
