package vn.io.nguyenquocanh.notepad.ui

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import vn.io.nguyenquocanh.notepad.R
import vn.io.nguyenquocanh.notepad.data.Note
import vn.io.nguyenquocanh.notepad.data.NoteRepository
import vn.io.nguyenquocanh.notepad.databinding.ActivityEditorBinding
import vn.io.nguyenquocanh.notepad.util.applySystemInsets
import java.text.DateFormat
import java.util.Date

class EditorActivity : AppCompatActivity() {

    private lateinit var b: ActivityEditorBinding
    private val repo by lazy { NoteRepository.get(this) }
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    private var noteId = 0L
    private var saved: Note? = null
    private var deleted = false
    private var drawMode = false
    private val swatchViews = ArrayList<Pair<View, Int>>()

    private val colorPrimary by lazy { ContextCompat.getColor(this, R.color.brand_primary) }
    private val colorOnPrimary by lazy { ContextCompat.getColor(this, R.color.brand_on_primary) }
    private val colorText by lazy { ContextCompat.getColor(this, R.color.oneui_text) }
    private val colorTextSecondary by lazy { ContextCompat.getColor(this, R.color.oneui_text_secondary) }
    private val colorChip by lazy { ContextCompat.getColor(this, R.color.oneui_chip) }
    private val colorCard by lazy { ContextCompat.getColor(this, R.color.oneui_card) }
    private val colorOutline by lazy { ContextCompat.getColor(this, R.color.oneui_outline) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        b = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.root.applySystemInsets(includeIme = true)
        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        noteId = savedInstanceState?.getLong(STATE_ID) ?: intent.getLongExtra(EXTRA_ID, 0L)
        val existing = if (noteId != 0L) repo.get(noteId) else null
        if (noteId == 0L) noteId = System.currentTimeMillis()
        saved = existing

        if (existing != null && savedInstanceState == null) {
            b.title.setText(existing.title)
            b.content.setText(existing.content)
        }
        if (existing == null && intent.action == Intent.ACTION_SEND) {
            b.content.setText(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
            b.title.setText(intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty())
        }
        b.date.text = formatDate(existing?.updatedAt ?: System.currentTimeMillis())

        if (existing?.hasDrawing == true) {
            val file = repo.drawingFile(noteId)
            if (file.exists()) {
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inMutable = true })
                    ?.let { b.drawing.setBaseBitmap(it) }
            }
        }

        setupDrawingTools()
        b.segText.setOnClickListener { showMode(false) }
        b.segDraw.setOnClickListener { showMode(true) }
        b.actionDraw.setOnClickListener { showMode(true) }
        b.actionShare.setOnClickListener { share() }
        b.actionDelete.setOnClickListener { confirmDelete() }

        val startDraw = intent.getBooleanExtra(EXTRA_DRAW, false) ||
            (existing != null && existing.hasDrawing && existing.content.isBlank())
        showMode(startDraw)
        if (existing == null && !startDraw && savedInstanceState == null) b.content.requestFocus()
    }

    private fun formatDate(time: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(time))

    private fun showMode(draw: Boolean) {
        drawMode = draw
        b.textPane.isVisible = !draw
        b.drawPane.isVisible = draw
        styleSegment(b.segText, !draw)
        styleSegment(b.segDraw, draw)
        if (draw) {
            currentFocus?.clearFocus()
            WindowCompat.getInsetsController(window, b.root).hide(WindowInsetsCompat.Type.ime())
        }
        invalidateOptionsMenu()
    }

    private fun styleSegment(view: TextView, selected: Boolean) {
        view.isSelected = selected
        view.background = if (selected) ContextCompat.getDrawable(this, R.drawable.bg_pill_thumb) else null
        view.setTextColor(if (selected) colorText else colorTextSecondary)
        view.typeface = Typeface.create(if (selected) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
    }

    private fun setupDrawingTools() {
        val d = b.drawing
        val density = resources.displayMetrics.density

        d.penWidth = b.widthSlider.value * density
        b.widthSlider.addOnChangeListener { _, value, _ -> d.penWidth = value * density }

        b.toolPen.setOnClickListener { setEraser(false) }
        b.toolEraser.setOnClickListener { setEraser(true) }
        setEraser(false)

        d.stylusOnly = prefs.getBoolean(KEY_STYLUS_ONLY, false)
        b.stylusBadge.isVisible = d.stylusOnly

        b.undo.setOnClickListener { d.undo() }
        b.redo.setOnClickListener { d.redo() }
        b.clear.setOnClickListener {
            if (!d.hasContent()) return@setOnClickListener
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clear_title)
                .setMessage(R.string.clear_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.clear) { _, _ -> d.clear() }
                .show()
        }
        d.onStateChanged = { updateUndoRedo() }
        updateUndoRedo()
        buildSwatches()
    }

    private fun setEraser(eraser: Boolean) {
        b.drawing.eraserMode = eraser
        styleTool(b.toolPen, !eraser)
        styleTool(b.toolEraser, eraser)
    }

    private fun styleTool(button: ImageButton, selected: Boolean) {
        button.isSelected = selected
        ViewCompat.setBackgroundTintList(button, ColorStateList.valueOf(if (selected) colorPrimary else colorChip))
        ImageViewCompat.setImageTintList(button, ColorStateList.valueOf(if (selected) colorOnPrimary else colorText))
    }

    private fun setToolEnabled(button: ImageButton, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else 0.35f
    }

    private fun updateUndoRedo() {
        setToolEnabled(b.undo, b.drawing.canUndo)
        setToolEnabled(b.redo, b.drawing.canRedo)
        setToolEnabled(b.clear, b.drawing.hasContent())
    }

    private fun buildSwatches() {
        val density = resources.displayMetrics.density
        val size = (30 * density).toInt()
        val margin = (12 * density).toInt()
        PEN_COLORS.forEachIndexed { index, color ->
            val v = View(this)
            v.layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = margin }
            v.contentDescription = getString(R.string.pen_color)
            v.isClickable = true
            v.isFocusable = true
            v.setOnClickListener { selectSwatch(index) }
            b.swatches.addView(v)
            swatchViews.add(v to color)
        }
        selectSwatch(0)
    }

    private fun selectSwatch(selected: Int) {
        val density = resources.displayMetrics.density
        swatchViews.forEachIndexed { i, (view, color) ->
            val isSel = i == selected
            view.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                if (isSel) {
                    setStroke((3 * density).toInt(), colorCard)
                } else {
                    setStroke(density.toInt().coerceAtLeast(1), colorOutline)
                }
            }
            view.foreground = if (isSel) {
                GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setStroke((2 * density).toInt(), colorPrimary)
                }
            } else {
                null
            }
            view.scaleX = if (isSel) 1.15f else 1f
            view.scaleY = if (isSel) 1.15f else 1f
        }
        b.drawing.penColor = swatchViews[selected].second
        setEraser(false)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_editor, menu)
        menu.findItem(R.id.action_stylus_only).apply {
            isChecked = b.drawing.stylusOnly
            isVisible = drawMode
        }
        menu.findItem(R.id.action_share).isVisible = drawMode
        menu.findItem(R.id.action_delete).isVisible = drawMode
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            finish(); true
        }
        R.id.action_stylus_only -> {
            val enabled = !item.isChecked
            item.isChecked = enabled
            b.drawing.stylusOnly = enabled
            b.stylusBadge.isVisible = enabled
            prefs.edit { putBoolean(KEY_STYLUS_ONLY, enabled) }
            Toast.makeText(
                this,
                if (enabled) R.string.stylus_only_on else R.string.stylus_only_off,
                Toast.LENGTH_SHORT
            ).show()
            true
        }
        R.id.action_share -> {
            share(); true
        }
        R.id.action_delete -> {
            confirmDelete(); true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun share() {
        val text = listOf(b.title.text?.toString().orEmpty(), b.content.text?.toString().orEmpty())
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
        if (text.isBlank()) {
            Toast.makeText(this, R.string.nothing_to_share, Toast.LENGTH_SHORT).show()
            return
        }
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(send, getString(R.string.share)))
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_title)
            .setMessage(R.string.delete_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                deleted = true
                repo.delete(noteId)
                Toast.makeText(this, R.string.deleted, Toast.LENGTH_SHORT).show()
                finish()
            }
            .show()
    }

    override fun onPause() {
        super.onPause()
        if (!deleted) persist()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_ID, noteId)
    }

    private fun persist() {
        val title = b.title.text?.toString().orEmpty().trim()
        val content = b.content.text?.toString().orEmpty()
        val d = b.drawing
        val hasDrawing = d.hasContent()

        if (title.isEmpty() && content.isBlank() && !hasDrawing) {
            if (saved != null) {
                repo.delete(noteId)
                saved = null
            }
            return
        }

        val drawingChanged = d.hasUnsavedChanges
        if (drawingChanged) {
            val file = repo.drawingFile(noteId)
            val bmp = d.exportBitmap()
            if (bmp != null) {
                file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } else {
                file.delete()
            }
            d.markSaved()
        }

        val prev = saved
        if (prev != null && !drawingChanged && prev.title == title &&
            prev.content == content && prev.hasDrawing == hasDrawing
        ) return

        val note = Note(noteId, title, content, System.currentTimeMillis(), hasDrawing)
        repo.save(note)
        saved = note
    }

    companion object {
        private const val EXTRA_ID = "note_id"
        private const val EXTRA_DRAW = "draw_mode"
        private const val STATE_ID = "state_id"
        private const val PREFS = "editor"
        private const val KEY_STYLUS_ONLY = "stylus_only"

        private val PEN_COLORS = intArrayOf(
            0xFF212121.toInt(),
            0xFF1565C0.toInt(),
            0xFFD32F2F.toInt(),
            0xFF2E7D32.toInt(),
            0xFFEF6C00.toInt(),
            0xFF6A1B9A.toInt()
        )

        fun intent(context: Context, id: Long, draw: Boolean): Intent =
            Intent(context, EditorActivity::class.java)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_DRAW, draw)
    }
}
