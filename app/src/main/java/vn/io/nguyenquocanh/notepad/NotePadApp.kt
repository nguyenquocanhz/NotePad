package vn.io.nguyenquocanh.notepad

import android.app.Application
import com.google.android.material.color.DynamicColors

class NotePadApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
