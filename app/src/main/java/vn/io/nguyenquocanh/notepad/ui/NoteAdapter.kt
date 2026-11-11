package vn.io.nguyenquocanh.notepad.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.format.DateUtils
import android.util.LruCache
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import vn.io.nguyenquocanh.notepad.R
import vn.io.nguyenquocanh.notepad.data.Note
import vn.io.nguyenquocanh.notepad.data.NoteRepository
import vn.io.nguyenquocanh.notepad.databinding.ItemNoteBinding

class NoteAdapter(
    private val onClick: (Note) -> Unit,
    private val onLongClick: (Note) -> Unit
) : ListAdapter<Note, NoteAdapter.VH>(Diff) {

    class VH(val b: ItemNoteBinding) : RecyclerView.ViewHolder(b.root)

    private val thumbs = object : LruCache<String, Bitmap>(THUMB_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val note = getItem(position)
        val ctx = holder.itemView.context
        with(holder.b) {
            title.text = note.title.ifBlank { ctx.getString(R.string.untitled) }
            content.text = note.content
            content.isVisible = note.content.isNotBlank()
            date.text = DateUtils.getRelativeTimeSpanString(
                note.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
            )
            val bmp = if (note.hasDrawing) thumbnail(ctx, note) else null
            thumb.setImageBitmap(bmp)
            thumb.isVisible = bmp != null
            root.setOnClickListener { onClick(note) }
            root.setOnLongClickListener {
                onLongClick(note)
                true
            }
        }
    }

    private fun thumbnail(ctx: Context, note: Note): Bitmap? {
        val key = "${note.id}_${note.updatedAt}"
        thumbs.get(key)?.let { return it }
        val file = NoteRepository.get(ctx).drawingFile(note.id)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= THUMB_WIDTH) sample *= 2
        val bmp = BitmapFactory.decodeFile(
            file.path, BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return null
        thumbs.put(key, bmp)
        return bmp
    }

    private object Diff : DiffUtil.ItemCallback<Note>() {
        override fun areItemsTheSame(oldItem: Note, newItem: Note) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Note, newItem: Note) = oldItem == newItem
    }

    companion object {
        private const val THUMB_WIDTH = 360
        private const val THUMB_CACHE_BYTES = 16 * 1024 * 1024
    }
}
