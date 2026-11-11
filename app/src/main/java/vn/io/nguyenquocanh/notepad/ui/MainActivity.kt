package vn.io.nguyenquocanh.notepad.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.io.nguyenquocanh.notepad.R
import vn.io.nguyenquocanh.notepad.data.Note
import vn.io.nguyenquocanh.notepad.data.NoteRepository
import vn.io.nguyenquocanh.notepad.databinding.ActivityMainBinding
import vn.io.nguyenquocanh.notepad.util.OneUi
import vn.io.nguyenquocanh.notepad.util.applySystemInsets

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private val repo by lazy { NoteRepository.get(this) }
    private val adapter = NoteAdapter(
        onClick = { openEditor(it.id, draw = false) },
        onLongClick = { confirmDelete(it) }
    )
    private var allNotes: List<Note> = emptyList()
    private var query = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.root.applySystemInsets()
        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        OneUi.setupExpandedAppBar(this, b.appBar, b.collapsing, b.header, b.toolbarTitle)

        b.recycler.layoutManager =
            StaggeredGridLayoutManager(spanCount(), StaggeredGridLayoutManager.VERTICAL)
        b.recycler.adapter = adapter

        b.fab.setOnClickListener { openEditor(0L, draw = false) }
        b.fab.setOnLongClickListener {
            openEditor(0L, draw = true)
            true
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun spanCount(): Int {
        val w = resources.configuration.screenWidthDp
        return when {
            w >= 840 -> 4
            w >= 600 -> 3
            else -> 2
        }
    }

    private fun refresh() {
        lifecycleScope.launch {
            allNotes = withContext(Dispatchers.IO) { repo.getAll() }
            b.largeSubtitle.text = getString(R.string.notes_count, allNotes.size)
            applyFilter()
        }
    }

    private fun applyFilter() {
        val q = query.trim()
        val list = if (q.isEmpty()) allNotes else allNotes.filter {
            it.title.contains(q, ignoreCase = true) || it.content.contains(q, ignoreCase = true)
        }
        adapter.submitList(list)
        b.empty.isVisible = list.isEmpty()
        b.emptyText.setText(if (q.isEmpty()) R.string.empty_notes else R.string.empty_search)
        b.emptyHint.isVisible = q.isEmpty()
    }

    private fun openEditor(id: Long, draw: Boolean) {
        startActivity(EditorActivity.intent(this, id, draw))
    }

    private fun confirmDelete(note: Note) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_title)
            .setMessage(R.string.delete_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { repo.delete(note.id) }
                    refresh()
                    Snackbar.make(b.root, R.string.deleted, Snackbar.LENGTH_SHORT)
                        .setAnchorView(b.fab)
                        .show()
                }
            }
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val searchItem = menu.findItem(R.id.action_search)
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                b.appBar.setExpanded(false, true)
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean = true
        })
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = getString(R.string.search_hint)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(text: String?): Boolean {
                searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(text: String?): Boolean {
                query = text.orEmpty()
                applyFilter()
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_about -> {
            startActivity(Intent(this, AboutActivity::class.java)); true
        }
        R.id.action_terms -> {
            LegalActivity.start(this, LegalActivity.TYPE_TERMS); true
        }
        R.id.action_privacy -> {
            LegalActivity.start(this, LegalActivity.TYPE_PRIVACY); true
        }
        else -> super.onOptionsItemSelected(item)
    }
}
