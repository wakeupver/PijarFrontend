package com.wakeupver.pijar

import android.app.Application
import android.os.StrictMode
import com.wakeupver.pijar.data.Repository

class PijarApp : Application() {
    lateinit var repo: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        // Beberapa template Daijishō meneruskan file:// ke emulator; jangan lempar FileUriExposedException.
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
        repo = Repository(this)
    }
}
