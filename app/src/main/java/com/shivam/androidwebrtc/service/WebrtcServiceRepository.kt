package com.shivam.androidwebrtc.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.shivam.androidwebrtc.service.WebrtcService

class WebrtcServiceRepository(
    private val context: Context
) {

    fun startIntent() {
        val thread = Thread {
            val startIntent = Intent(context, WebrtcService::class.java)
            startIntent.action = "StartIntent"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(startIntent)
            } else {
                context.startService(startIntent)
            }
        }
        thread.start()
    }

    fun requestConnection(target: String) {
        Log.e("NotError", "WebrtcServiceRepository@requestConnection")
        val thread = Thread {
            val startIntent = Intent(context, WebrtcService::class.java)
            startIntent.action = "RequestConnectionIntent"
            startIntent.putExtra("target", target)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(startIntent)
            } else {
                context.startService(startIntent)
            }
        }
        thread.start()
    }

    fun acceptCAll() {
        val thread = Thread {
            val startIntent = Intent(context, WebrtcService::class.java)
            startIntent.action = "AcceptCallIntent"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(startIntent)
            } else {
                context.startService(startIntent)
            }
        }
        thread.start()
    }

    fun endCallIntent() {
        val thread = Thread {
            val startIntent = Intent(context, WebrtcService::class.java)
            startIntent.action = "EndCallIntent"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(startIntent)
            } else {
                context.startService(startIntent)
            }
        }
        thread.start()
    }

    fun stopIntent() {
        val thread = Thread {

            val startIntent = Intent(context, WebrtcService::class.java)
            startIntent.action = "StopIntent"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(startIntent)
            } else {
                context.startService(startIntent)
            }
        }
        thread.start()
    }

}

