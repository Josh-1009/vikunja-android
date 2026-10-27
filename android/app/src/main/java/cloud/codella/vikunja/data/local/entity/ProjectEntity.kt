package cloud.codella.vikunja.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: Long,
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
