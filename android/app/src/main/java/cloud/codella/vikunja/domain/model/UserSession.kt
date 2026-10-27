package cloud.codella.vikunja.domain.model

data class UserSession(
    val userId: Long,
    val username: String,
    val token: String,
    val isApiToken: Boolean,
    val serverUrl: String,
    val allowSelfSigned: Boolean = false
)
