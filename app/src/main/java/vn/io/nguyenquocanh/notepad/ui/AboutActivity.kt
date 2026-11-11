package vn.io.nguyenquocanh.notepad.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import vn.io.nguyenquocanh.notepad.BuildConfig
import vn.io.nguyenquocanh.notepad.R
import vn.io.nguyenquocanh.notepad.databinding.ActivityAboutBinding
import vn.io.nguyenquocanh.notepad.util.applySystemInsets

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val b = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.root.applySystemInsets()
        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        b.version.text = getString(R.string.version_format, BuildConfig.VERSION_NAME)
        b.btnGithub.setOnClickListener { open(getString(R.string.url_github)) }
        b.btnWebsite.setOnClickListener { open(getString(R.string.url_website)) }
        b.btnSource.setOnClickListener { open(getString(R.string.url_repo)) }
        b.btnTerms.setOnClickListener { LegalActivity.start(this, LegalActivity.TYPE_TERMS) }
        b.btnPrivacy.setOnClickListener { LegalActivity.start(this, LegalActivity.TYPE_PRIVACY) }
    }

    private fun open(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(this, R.string.open_link_failed, Toast.LENGTH_SHORT).show() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
