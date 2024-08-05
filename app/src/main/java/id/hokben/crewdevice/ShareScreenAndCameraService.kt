package id.hokben.crewdevice

import android.app.Service
import android.content.Intent
import android.os.IBinder

class ShareScreenAndCameraService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        return START_STICKY
    }
    override fun onBind(p0: Intent?): IBinder? {
        return null
    }
}