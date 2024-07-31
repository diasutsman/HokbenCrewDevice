package com.shivam.androidwebrtc.webrtc

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import com.shivam.androidwebrtc.utils.DataModel
import com.shivam.androidwebrtc.utils.DataModelType
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnection.Observer
import org.webrtc.PeerConnectionFactory
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer
import org.webrtc.VideoTrack
import javax.inject.Inject

class WebrtcClient @Inject constructor(
    private val context: Context, private val gson: Gson
) {

    private lateinit var username: String
    private lateinit var observer: Observer
    private lateinit var localSurfaceView: SurfaceViewRenderer
    var listener: Listener? = null
    private var permissionIntent: Intent? = null

    private var peerConnection: PeerConnection? = null
    private val eglBaseContext = EglBase.create().eglBaseContext
    private val peerConnectionFactory by lazy { createPeerConnectionFactory() }
    private val mediaConstraint = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
    }
    private val iceServer = listOf(
        PeerConnection.IceServer(
            "turn:openrelay.metered.ca:443?transport=tcp", "openrelayproject", "openrelayproject"
        )
    )

    private var screenCapturer: VideoCapturer? = null
    private val localVideoSource by lazy { peerConnectionFactory.createVideoSource(false) }
    private val localTrackId = "local_track"
    private val localStreamId = "local_stream"
    private var localVideoTrack: VideoTrack? = null
    private var localStream: MediaStream? = null


    init {
        initPeerConnectionFactory(context)
    }

    fun initializeWebrtcClient(
        username: String, view: SurfaceViewRenderer, observer: Observer
    ) {

        this.username = username
        this.observer = observer
        peerConnection = createPeerConnection(observer)
        initSurfaceView(view)
    }

    fun setPermissionIntent(intent: Intent) {
        this.permissionIntent = intent
    }

    private fun initSurfaceView(view: SurfaceViewRenderer) {
        this.localSurfaceView = view
        view.run {
            setMirror(false)
            setEnableHardwareScaler(true)
            init(eglBaseContext, null)
        }
    }

    fun startScreenCapturing(view: SurfaceViewRenderer) {
        Log.e("NotError", "startScreenCapturing: SurfaceViewRenderer id: ${view.id}")
        val displayMetrics = DisplayMetrics()
        val windowsManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowsManager.defaultDisplay.getMetrics(displayMetrics)

        val screenWidthPixels = displayMetrics.widthPixels
        val screenHeightPixels = displayMetrics.heightPixels

        val surfaceTextureHelper = SurfaceTextureHelper.create(
            Thread.currentThread().name, eglBaseContext
        )

        screenCapturer = createScreenCapturer()
        screenCapturer!!.initialize(
            surfaceTextureHelper,
            context,
            localVideoSource.capturerObserver
        )
        screenCapturer!!.startCapture(screenWidthPixels, screenHeightPixels, 30)

        localVideoTrack =
            peerConnectionFactory.createVideoTrack(localTrackId + "_video", localVideoSource)
//        localVideoTrack?.addSink(view) # this should not be added since, the phone should not add the screen share of it self
        localStream = peerConnectionFactory.createLocalMediaStream(localStreamId)
        localStream?.addTrack(localVideoTrack)
        peerConnection?.addStream(localStream)

    }

    private fun createScreenCapturer(): VideoCapturer {
        return ScreenCapturerAndroid(permissionIntent,
            @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
            object : MediaProjection.Callback() {
                override fun onStop() {
                    super.onStop()
                    Log.d("TAG", "onStop: stopped screen casting permission")
                }
            })
    }

    private fun initPeerConnectionFactory(application: Context) {
        val options = PeerConnectionFactory.InitializationOptions.builder(application)
            .setEnableInternalTracer(true).setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
    }

    private fun createPeerConnectionFactory(): PeerConnectionFactory {
        return PeerConnectionFactory.builder().setVideoDecoderFactory(
            DefaultVideoDecoderFactory(eglBaseContext)
        ).setVideoEncoderFactory(
            DefaultVideoEncoderFactory(
                eglBaseContext, true, true
            )
        ).setOptions(PeerConnectionFactory.Options().apply {
            disableEncryption = false
            disableNetworkMonitor = false
        }).createPeerConnectionFactory()
    }

    private fun createPeerConnection(observer: Observer): PeerConnection? {
        return peerConnectionFactory.createPeerConnection(
            iceServer, observer
        )
    }

    fun call(target: String) {
        peerConnection?.createOffer(object : MySdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                super.onCreateSuccess(desc)
                peerConnection?.setLocalDescription(object : MySdpObserver() {
                    override fun onSetSuccess() {
                        super.onSetSuccess()
                        listener?.onTransferEventToSocket(
                            DataModel(
                                type = DataModelType.Offer, username, target, desc?.description
                            )
                        )
                    }
                }, desc)
            }
        }, mediaConstraint)
    }

    fun answer(target: String) {
        peerConnection?.createAnswer(object : MySdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                super.onCreateSuccess(desc)
                peerConnection?.setLocalDescription(object : MySdpObserver() {
                    override fun onSetSuccess() {
                        super.onSetSuccess()
                        listener?.onTransferEventToSocket(
                            DataModel(
                                type = DataModelType.Answer,
                                username = username,
                                target = target,
                                data = desc?.description
                            )
                        )
                    }
                }, desc)
            }
        }, mediaConstraint)
    }

    fun onRemoteSessionReceived(sessionDescription: SessionDescription) {

        peerConnection?.setRemoteDescription(MySdpObserver(), sessionDescription)
    }

    fun addIceCandidate(iceCandidate: IceCandidate) {

        peerConnection?.addIceCandidate(iceCandidate)

    }

    fun sendIceCandidate(candidate: IceCandidate, target: String) {
        addIceCandidate(candidate)
        listener?.onTransferEventToSocket(
            DataModel(
                type = DataModelType.IceCandidates,
                username = username,
                target = target,
                data = gson.toJson(candidate)
            )
        )
    }

    fun closeConnection() {
        try {
            screenCapturer?.stopCapture()
            screenCapturer?.dispose()
            localStream?.dispose()
            peerConnection?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun restart() {
        closeConnection()
        localSurfaceView.let {
            it.clearImage()
            it.release()
            initializeWebrtcClient(username, it, observer)
        }
    }


    interface Listener {
        fun onTransferEventToSocket(data: DataModel)
    }
}