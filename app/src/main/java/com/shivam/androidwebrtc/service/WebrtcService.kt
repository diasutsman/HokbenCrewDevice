package com.shivam.androidwebrtc.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.shivam.androidwebrtc.repository.MainRepository
import com.shivam.androidwebrtc.socket.SocketClient
import com.shivam.androidwebrtc.webrtc.WebrtcClient
import com.google.gson.Gson
import com.myhexaville.androidwebrtc.R
import com.shivam.androidwebrtc.utils.Utils
import org.webrtc.MediaStream
import org.webrtc.SurfaceViewRenderer

class WebrtcService : Service(), MainRepository.Listener {


    companion object {
        var screenPermissionIntent: Intent? = null
        var surfaceView: SurfaceViewRenderer? = null
        var listener: MainRepository.Listener? = null
    }

    private var _mainRepository: MainRepository? = null
    private val mainRepository get() = _mainRepository as MainRepository


    private lateinit var notificationManager: NotificationManager
    private lateinit var username: String

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(
            NotificationManager::class.java
        )
        _mainRepository =
            MainRepository(SocketClient(Gson()), WebrtcClient(applicationContext, Gson()), Gson())
        mainRepository.listener = this
        this.username = Utils.getUsername(contentResolver)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            when (intent.action) {
                "StartIntent" -> {
                    mainRepository.init(username, surfaceView!!)
                    startServiceWithNotification()
                }

                "StopIntent" -> {
                    stopMyService()
                }

                "EndCallIntent" -> {
                    mainRepository.sendCallEndedToOtherPeer()
                    mainRepository.onDestroy()
                    stopMyService()
                }

                "AcceptCallIntent" -> {
                    val target = intent.getStringExtra("target")
                    target?.let {
                        mainRepository.startCall(it)
                    }
                }

                "RequestConnectionIntent" -> {
                    Log.e("NotError", "WebrtcService@onStartCommand intent: RequestConnectionIntent")
                    val target = intent.getStringExtra("target")
                    target?.let {
                        mainRepository.setPermissionIntentToWebrtcClient(screenPermissionIntent!!)
                        mainRepository.startScreenCapturing(surfaceView!!)
                        mainRepository.sendScreenShareConnection(it)
                    }
                }
            }
        }

        return START_STICKY
    }

    private fun stopMyService() {
        mainRepository.onDestroy()
        stopSelf()
        notificationManager.cancelAll()
    }

    private fun startServiceWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                "channel1", "foreground", NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(notificationChannel)
            val notification = NotificationCompat.Builder(this, "channel1")
                .setSmallIcon(R.mipmap.ic_launcher)

            startForeground(1, notification.build())
        }

    }

    override fun onConnectionRequestReceived(target: String) {
        listener?.onConnectionRequestReceived(target)
    }

    override fun onConnectionRequestReceived() {
        listener?.onConnectionRequestReceived()
    }

    override fun onConnectionConnected() {
        listener?.onConnectionConnected()
    }

    override fun onCallEndReceived() {
        listener?.onCallEndReceived()
        stopMyService()
    }

    override fun onRemoteStreamAdded(stream: MediaStream) {
        listener?.onRemoteStreamAdded(stream)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}