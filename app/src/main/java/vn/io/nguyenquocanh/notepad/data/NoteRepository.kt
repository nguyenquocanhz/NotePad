package vn.io.nguyenquocanh.notepad.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Lưu ghi chú cục bộ dạng JSON trong filesDir, bản vẽ lưu PNG riêng theo id.
 */
class NoteRepository private constructor(private val dir: File) {

    private val file = File(dir, "notes.json")
    private var cache: MutableList<Note>? = null

    @Synchronized
    fun getAll(): List<Note> = load().sortedByDescending { it.updatedAt }

    @Synchronized
    fun get(id: Long): Note? = load().firstOrNull { it.id == id }

    @Synchronized
    fun save(note: Note) {
        val list = load()
        val index = list.indexOfFirst { it.id == note.id }
        if (index >= 0) list[index] = note else list.add(note)
        persist(list)
    }

    @Synchronized
    fun delete(id: Long) {
        val list = load()
        list.removeAll { it.id == id }
        drawingFile(id).delete()
        persist(list)
    }

    fun drawingFile(id: Long): File = File(dir, "drawing_$id.png")

    private fun load(): MutableList<Note> {
        cache?.let { return it }
        val result = mutableListOf<Note>()
        if (file.exists()) {
            runCatching {
                val arr = JSONArray(file.readText())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    result.add(
                        Note(
                            id = o.getLong("id"),
                            title = o.optString("title"),
                            content = o.optString("content"),
                            updatedAt = o.optLong("updatedAt"),
                            hasDrawing = o.optBoolean("hasDrawing")
                        )
                    )
                }
            }
        }
        cache = result
        return result
    }

    private fun persist(list: List<Note>) {
        val arr = JSONArray()
        list.forEach { n ->
            arr.put(
                JSONObject()
                    .put("id", n.id)
                    .put("title", n.title)
                    .put("content", n.content)
                    .put("updatedAt", n.updatedAt)
                    .put("hasDrawing", n.hasDrawing)
            )
        }
        val json = arr.toString()
        val tmp = File(dir, "notes.json.tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(file)) {
            file.writeText(json)
            tmp.delete()
        }
    }

    companion object {
        @Volatile
        private var instance: NoteRepository? = null

        fun get(context: Context): NoteRepository =
            instance ?: synchronized(this) {
                instance ?: NoteRepository(context.applicationContext.filesDir).also { instance = it }
            }
    }
}
