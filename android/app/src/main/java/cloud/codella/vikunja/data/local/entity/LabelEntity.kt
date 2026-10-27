package cloud.codella.vikunja.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "labels")
data class LabelEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val description: String = "",
    val hexColor: String = ""
)
