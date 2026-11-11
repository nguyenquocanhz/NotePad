package vn.io.nguyenquocanh.notepad.util

import android.content.res.Configuration
import android.util.TypedValue
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import kotlin.math.max

/**
 * Thanh tiêu đề mở rộng kiểu One UI: tiêu đề lớn nằm ở vùng xem phía trên,
 * thu gọn về toolbar khi cuộn. Màn hình ngang dùng toolbar thường.
 */
object OneUi {

    private const val EXPANDED_RATIO = 0.36f

    fun setupExpandedAppBar(
        activity: AppCompatActivity,
        appBar: AppBarLayout,
        collapsing: CollapsingToolbarLayout,
        header: View,
        collapsedTitle: View
    ) {
        val res = activity.resources
        val tv = TypedValue()
        val toolbarHeight =
            if (activity.theme.resolveAttribute(androidx.appcompat.R.attr.actionBarSize, tv, true)) {
                TypedValue.complexToDimensionPixelSize(tv.data, res.displayMetrics)
            } else {
                (56 * res.displayMetrics.density).toInt()
            }

        if (res.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            val lp = collapsing.layoutParams
            lp.height = toolbarHeight
            collapsing.layoutParams = lp
            header.visibility = View.GONE
            collapsedTitle.alpha = 1f
            return
        }

        val expanded = max((res.displayMetrics.heightPixels * EXPANDED_RATIO).toInt(), toolbarHeight * 3)
        val lp = collapsing.layoutParams
        lp.height = expanded
        collapsing.layoutParams = lp
        collapsedTitle.alpha = 0f

        appBar.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { bar, offset ->
            val range = bar.totalScrollRange
            val fraction = if (range == 0) 0f else -offset / range.toFloat()
            header.alpha = (1f - fraction * 1.6f).coerceIn(0f, 1f)
            collapsedTitle.alpha = ((fraction - 0.6f) / 0.4f).coerceIn(0f, 1f)
        })
    }
}
