package uz.reaktivombor

import android.app.Application
import uz.reaktivombor.notify.ExpiryNotifications

class ReaktivApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ExpiryNotifications.ensureChannel(this)
        ExpiryNotifications.schedule(this)
    }
}
