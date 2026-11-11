package vn.io.nguyenquocanh.notepad.data

data class Note(
    val id: Long,
    val title: String,
    val content: String,
    val updatedAt: Long,
    val hasDrawing: Boolean
)
