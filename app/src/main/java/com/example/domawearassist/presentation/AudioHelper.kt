package com.example.domawearassist.presentation

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.content.pm.PackageManager

class AudioHelper(private val context: Context) {

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun audioOutputAvailable(type: Int): Boolean {

        if (!context.packageManager.hasSystemFeature(
                PackageManager.FEATURE_AUDIO_OUTPUT)) {
            return false
        }

        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .any { it.type == type }
    }
}
