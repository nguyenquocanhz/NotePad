package vn.io.nguyenquocanh.notepad.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import vn.io.nguyenquocanh.notepad.R
import vn.io.nguyenquocanh.notepad.databinding.ActivityLegalBinding
import vn.io.nguyenquocanh.notepad.util.applySystemInsets

/** Màn hình Điều khoản sử dụng / Chính sách quyền riêng tư. */
class LegalActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val b = ActivityLegalBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.root.applySystemInsets()
        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val privacy = intent.getStringExtra(EXTRA_TYPE) == TYPE_PRIVACY
        setTitle(if (privacy) R.string.privacy_title else R.string.terms_title)
        b.body.text = getText(if (privacy) R.string.privacy_body else R.string.terms_body)
        b.updated.text = getString(R.string.legal_updated, getString(R.string.legal_date))
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        private const val EXTRA_TYPE = "type"
        const val TYPE_TERMS = "terms"
        const val TYPE_PRIVACY = "privacy"

        fun start(context: Context, type: String) {
            context.startActivity(Intent(context, LegalActivity::class.java).putExtra(EXTRA_TYPE, type))
        }
    }
}
