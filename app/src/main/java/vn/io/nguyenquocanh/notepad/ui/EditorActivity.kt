package vn.io.nguyenquocanh.notepad.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import vn.io.nguyenquocanh.notepad.R
import vn.io.nguyenquocanh.notepad.data.Note
import vn.io.nguyenquocanh.notepad.data.NoteRepository
import vn.io.nguyenquocanh.notepad.databinding.ActivityEditorBinding
import vn.io.nguyenquocanh.notepad.util.applySystemInsets

class EditorActivity : AppCompatActivity() {

    private lateinit var b: ActivityEditorBinding
    private val repo by lazy { NoteRepository.get(this) }
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    private var noteId = 0L
    private var saved: Note? = null
    private var deleted = false
    private var drawMode = false
    private val swatchViews = ArrayList<Pair<View, Int>>()

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
        if (existing?.hasDrawing == true) {
            val file = repo.drawingFile(noteId)
            if (file.exists()) {
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inMutable = true })
                    ?.let { b.drawing.setBaseBitmap(it) }
            }
        }

        setupDrawingTools()
        val startDraw = intent.getBooleanExtra(EXTRA_DRAW, false) ||
            (existing != null && existing.hasDrawing && existing.content.isBlank())
        setupModeToggle(startDraw)
        if (existing == null && !startDraw && savedInstanceState == null) b.content.requestFocus()
    }

    private fun setupModeToggle(startDraw: Boolean) {
        b.modeToggle.check(if (startDraw) R.id.mode_draw else R.id.mode_text)
        showMode(startDraw)
        b.modeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) showMode(checkedId == R.id.mode_draw)
        }
    }

    private fun showMode(draw: Boolean) {
        drawMode = draw
        b.textPane.isVisible = !draw
        b.drawPane.isVisible = draw
        if (draw) {
            currentFocus?.clearFocus()
            WindowCompat.getInsetsController(window, b.root).hide(WindowInsetsCompat.Type.ime())
        }
        invalidateOptionsMenu()
    }

    private fun setupDrawingTools() {
        val d = b.drawing
        val density = resources.displayMetrics.density

        d.penWidth = b.widthSlider.value * density
        b.widthSlider.addOnChangeListener { _, value, _ -> d.penWidth = value * density }

        b.toolToggle.check(R.id.tool_pen)
        b.toolToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) d.eraserMode = checkedId == R.id.tool_eraser
        }

        d.stylusOnly = prefs.getBoolean(KEY_STYLUS_ONLY, false)

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

    private fun updateUndoRedo() {
        b.undo.isEnabled = b.drawing.canUndo
        b.redo.isEnabled = b.drawing.canRedo
        b.clear.isEnabled = b.drawing.hasContent()
    }

    private fun buildSwatches() {
        val density = resources.displayMetrics.density
        val size = (30 * density).toInt()
        val margin = (8 * density).toInt()
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
        val outline = MaterialColors.getColor(b.root, com.google.android.material.R.attr.colorOutline)
        val primary = MaterialColors.getColor(b.root, androidx.appcompat.R.attr.colorPrimary)
        swatchViews.forEachIndexed { i, (view, color) ->
            val isSel = i == selected
            view.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                setStroke(((if (isSel) 3f else 1f) * density).toInt(), if (isSel) primary else outline)
            }
            view.scaleX = if (isSel) 1.15f else 1f
            view.scaleY = if (isSel) 1.15f else 1f
        }
        b.drawing.penColor = swatchViews[selected].second
        b.toolToggle.check(R.id.tool_pen)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_editor, menu)
        menu.findItem(R.id.action_stylus_only).apply {
            isChecked = b.drawing.stylusOnly
            isVisible = drawMode
        }
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

        val drawingChanged = d.isDirty
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
