package com.shivam.androidwebrtc.utils;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.provider.Settings;

import java.util.HashMap;
import java.util.Map;

public class Utils {
    @SuppressLint("HardwareIds")
    public static String getUsername(ContentResolver contentResolver) {
        return getUsername(contentResolver, false);
    }

    @SuppressLint("HardwareIds")
    public static String getUsername(ContentResolver contentResolver, Boolean forOtherDevice) {
        final String deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID);
        if (!forOtherDevice) {
            return deviceId;
        }

        Map<String, String> map = new HashMap<>();
        map.put("9c4c9267c277fed0", "c358430e6696cdee");
        map.put("c358430e6696cdee", "9c4c9267c277fed0");
        String result = map.get(deviceId);
        if (result == null) {
            return "";
        }
        return result;
    }
}
