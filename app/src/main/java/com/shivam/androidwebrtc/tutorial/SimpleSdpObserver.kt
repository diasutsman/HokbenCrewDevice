package com.shivam.androidwebrtc.tutorial

import org.webrtc2.SdpObserver
import org.webrtc2.SessionDescription

internal open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(sessionDescription: SessionDescription) {
    }

    override fun onSetSuccess() {
    }

    override fun onCreateFailure(s: String) {
    }

    override fun onSetFailure(s: String) {
    }
}
