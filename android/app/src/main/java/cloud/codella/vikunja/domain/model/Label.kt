package cloud.codella.vikunja.domain.model

data class Label(
    val id: Long,
    val title: String,
    val description: String = "",
    val hexColor: String = ""
)
