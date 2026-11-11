package vn.io.nguyenquocanh.notepad.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Đệm root view theo system bars (và bàn phím nếu cần) khi chạy edge-to-edge. */
fun View.applySystemInsets(includeIme: Boolean = false) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        var types = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        if (includeIme) types = types or WindowInsetsCompat.Type.ime()
        val i = insets.getInsets(types)
        v.setPadding(i.left, i.top, i.right, i.bottom)
        WindowInsetsCompat.CONSUMED
    }
}
