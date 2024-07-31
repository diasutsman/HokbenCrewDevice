package com.shivam.androidwebrtc.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import javax.inject.Inject

class WebrtcServiceRepository @Inject constructor(
    private val context: Context,
) {

    @RequiresApi(Build.VERSION_CODES.O)
    fun startIntent(username: String) {
        val thread = Thread {
            val startIntent = Intent(context, WebrtcService::class.java)
            startIntent.action = "StartIntent"
            startIntent.putExtra("username", username)


            context.startForegroundService(startIntent)

        }
        thread.start()
    }


    @SuppressLint("NewApi")
    fun requestConnection(target: String) {
        val thread = Thread {
            val startIntent = Intent(context, WebrtcService::class.java)
            startIntent.action = "RequestConnectionIntent"
            startIntent.putExtra("target", target)


            context.startForegroundService(startIntent)

        }
        thread.start()
    }

    @SuppressLint("NewApi")
    fun acceptCAll(target: String) {
        val thread = Thread {
            val startIntent = Intent(context, WebrtcService::class.java)
            startIntent.action = "AcceptCallIntent"
            startIntent.putExtra("target", target)

            context.startForegroundService(startIntent)

        }
        thread.start()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun endCallIntent() {
        val thread = Thread {
            val startIntent = Intent(context, WebrtcService::class.java)
            startIntent.action = "EndCallIntent"

            context.startForegroundService(startIntent)

        }
        thread.start()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun stopIntent() {
        val thread = Thread {

            val startIntent = Intent(context, WebrtcService::class.java)
            startIntent.action = "StopIntent"

            context.startForegroundService(startIntent)

        }
        thread.start()
    }

}

