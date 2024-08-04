package id.hokben.crewdevice.utils

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.os.Build
import id.hokben.crewdevice.BuildConfig

object Utils {
    @SuppressLint("HardwareIds")
    fun getUsername(contentResolver: ContentResolver?): String {
        return getUsername(contentResolver, false)
    }

    @SuppressLint("HardwareIds")
    fun getUsername(contentResolver: ContentResolver?, forOtherDevice: Boolean?): String {
//        final String deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID);
        val deviceId = Build.BRAND
        if (!forOtherDevice!!) {
            return deviceId
        }

        val map: MutableMap<String, String> = HashMap()
        map[BuildConfig.CLIENT_DEVICE] = BuildConfig.CREW_DEVICE
        map[BuildConfig.CREW_DEVICE] = BuildConfig.CLIENT_DEVICE
        val result = map[deviceId] ?: return ""
        return result
    }
}
