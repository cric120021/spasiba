package app.spasiba.nativeapp2.data.model

data class Song(
    val id: String,
    val title: String,
    val author: String,
    val thumbnail: String,
    val durationSeconds: Int = 0,
    val streamUrl: String? = null,
    val isLiked: Boolean = false,
    val lyrics: String? = null,
    val views: Long = 0L
) {
    val durationFormatted: String
        get() {
            val mins = durationSeconds / 60
            val secs = durationSeconds % 60
            return String.format("%d:%02d", mins, secs)
        }
}

data class CategoryChip(
    val id: String,
    val label: String,
    val params: String
)
