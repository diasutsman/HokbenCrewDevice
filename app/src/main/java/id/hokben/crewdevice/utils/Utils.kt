package id.hokben.crewdevice.utils

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.os.Build

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
        map["POCO"] = "Infinix"
        map["Infinix"] = "POCO"
        val result = map[deviceId] ?: return ""
        return result
    }
}
