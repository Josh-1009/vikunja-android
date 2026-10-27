package cloud.codella.vikunja.domain.model

data class Project(
    val id: Long,
    val title: String,
    val description: String = "",
    val hexColor: String = "",
    val ownerId: Long = 0L,
    val parentProjectId: Long? = null,
    val isArchived: Boolean = false,
    val isFavorite: Boolean = false,
    val position: Double = 0.0,
    val updated: String = ""
)
