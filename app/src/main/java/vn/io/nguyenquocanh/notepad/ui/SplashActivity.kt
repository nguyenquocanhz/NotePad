package vn.io.nguyenquocanh.notepad.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vn.io.nguyenquocanh.notepad.BuildConfig
import vn.io.nguyenquocanh.notepad.R
import vn.io.nguyenquocanh.notepad.databinding.ActivitySplashBinding
import vn.io.nguyenquocanh.notepad.util.applySystemInsets

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val b = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.root.applySystemInsets()

        b.version.text = getString(R.string.version_format, BuildConfig.VERSION_NAME)
        b.content.alpha = 0f
        b.content.scaleX = 0.9f
        b.content.scaleY = 0.9f
        b.content.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(450).start()

        lifecycleScope.launch {
            delay(SPLASH_DELAY)
            startActivity(Intent(this@SplashActivity, MainActivity::class.java))
            finish()
        }
    }

    companion object {
        private const val SPLASH_DELAY = 1300L
    }
}
