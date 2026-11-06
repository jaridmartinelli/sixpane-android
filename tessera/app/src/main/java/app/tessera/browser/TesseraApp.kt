package app.tessera.browser

import android.app.Application

/** Process-wide owner of the sessions, so they outlive Activity recreation. */
class TesseraApp : Application() {
    lateinit var store: Store
        private set
    lateinit var hub: SessionHub
        private set

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        hub = SessionHub(this, store)
    }
}
