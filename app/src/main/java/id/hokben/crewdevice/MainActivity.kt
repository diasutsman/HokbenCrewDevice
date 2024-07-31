package id.hokben.crewdevice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import dagger.hilt.android.AndroidEntryPoint
import id.hokben.crewdevice.databinding.ActivitySamplePeerConnectionBinding
import id.hokben.crewdevice.repository.MainRepository
import id.hokben.crewdevice.service.WebrtcService.Companion.listener
import id.hokben.crewdevice.service.WebrtcService.Companion.screenPermissionIntent
import id.hokben.crewdevice.service.WebrtcService.Companion.surfaceView
import id.hokben.crewdevice.service.WebrtcServiceRepository
import id.hokben.crewdevice.utils.Utils
import id.hokben.crewdevice.webrtc.SimpleSdpObserver
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONException
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import java.net.URISyntaxException
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : AppCompatActivity(), MainRepository.Listener {
    private var socket: Socket? = null
    private var isInitiator = false
    private var isChannelReady = false
    private var isStarted = false

    private val localStreamId = "ARDAMS"

    @Inject
    lateinit var webrtcServiceRepository: WebrtcServiceRepository

    var audioConstraints: MediaConstraints? = null
    var videoConstraints: MediaConstraints? = null
    var sdpConstraints: MediaConstraints? = null
    var videoSource: VideoSource? = null
    var localVideoTrack: VideoTrack? = null
    var audioSource: AudioSource? = null
    var localAudioTrack: AudioTrack? = null
    var surfaceTextureHelper: SurfaceTextureHelper? = null

    private lateinit var binding: ActivitySamplePeerConnectionBinding;
    private var peerConnection: PeerConnection? = null
    private var rootEglBase: EglBase? = null
    private val peerConnectionFactory: PeerConnectionFactory by lazy { createPeerConnectionFactory() }
    private var videoTrackFromCamera: VideoTrack? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_sample_peer_connection)
        setSupportActionBar(binding.toolbar)

        startVideoCapture()
        initShareScreen()
    }


    @SuppressLint("NewApi")
    private fun initShareScreen() {
        Log.e("NotError", "MainActivity@initShareScreen")

        surfaceView = binding.surfaceViewShareScreen
        listener = this
        webrtcServiceRepository.startIntent(Utils.getUsername(contentResolver))
        startScreenCapture()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun onDestroy() {
        if (socket != null) {
            sendMessage("bye")
            socket!!.disconnect()
        }
        super.onDestroy()
    }

    @AfterPermissionGranted(RC_CALL)
    private fun startVideoCapture() {
        val perms = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (EasyPermissions.hasPermissions(this, *perms)) {
            connectToSignallingServer()

            initializeSurfaceViews()

            initializePeerConnectionFactory()

            createVideoTrackFromCameraAndShowIt()

            initializePeerConnections()

            startStreamingVideo()
        } else {
            EasyPermissions.requestPermissions(this, "Need some permissions", RC_CALL, *perms)
        }
    }

    private fun connectToSignallingServer() {
        try {
            // For me this was "http://192.168.1.220:3000";
            // $ hostname -I
            val URL =
                BuildConfig.SIGNALING_SERVER_URL // "https://calm-badlands-59575.herokuapp.com/"; //
            Log.e(TAG, "REPLACE ME: IO Socket:$URL")
            socket = IO.socket(URL)

            socket?.on(Socket.EVENT_CONNECT, Emitter.Listener { args: Array<Any?>? ->
                Log.d(TAG, "connectToSignallingServer: connect")
                socket?.emit("create or join", "cuarto")
            })?.on("ipaddr") { args: Array<Any?>? ->
                Log.d(TAG, "connectToSignallingServer: ipaddr")
            }?.on("created") { args: Array<Any?>? ->
                Log.d(TAG, "connectToSignallingServer: created")
                isInitiator = true
            }?.on("full") { args: Array<Any?>? ->
                Log.d(TAG, "connectToSignallingServer: full")
            }?.on("join") { args: Array<Any?>? ->
                Log.d(TAG, "connectToSignallingServer: join")
                Log.d(TAG, "connectToSignallingServer: Another peer made a request to join room")
                Log.d(TAG, "connectToSignallingServer: This peer is the initiator of room")
                isChannelReady = true
            }?.on("joined") { args: Array<Any?>? ->
                Log.d(TAG, "connectToSignallingServer: joined")
                isChannelReady = true
            }?.on("log") { args: Array<Any> ->
                for (arg in args) {
                    Log.d(TAG, "connectToSignallingServer: $arg")
                }
            }?.on("message") { args: Array<Any?>? ->
                Log.d(TAG, "connectToSignallingServer: got a message")
            }?.on("message") { args: Array<Any> ->
                try {
                    if (args[0] is String) {
                        val message = args[0] as String
                        if (message == "got user media") {
                            maybeStart()
                        }
                    } else {
                        val message = args[0] as JSONObject
                        Log.d(TAG, "connectToSignallingServer: got message $message")
                        if (message.getString("type") == "offer") {
                            Log.d(
                                TAG,
                                "connectToSignallingServer: received an offer $isInitiator $isStarted"
                            )
                            if (!isInitiator && !isStarted) {
                                maybeStart()
                            }
                            peerConnection!!.setRemoteDescription(
                                SimpleSdpObserver(),
                                SessionDescription(
                                    SessionDescription.Type.OFFER,
                                    message.getString("sdp")
                                )
                            )
                            doAnswer()
                        } else if (message.getString("type") == "answer" && isStarted) {
                            peerConnection!!.setRemoteDescription(
                                SimpleSdpObserver(),
                                SessionDescription(
                                    SessionDescription.Type.ANSWER,
                                    message.getString("sdp")
                                )
                            )
                        } else if (message.getString("type") == "candidate" && isStarted) {
                            Log.d(TAG, "connectToSignallingServer: receiving candidates")
                            val candidate = IceCandidate(
                                message.getString("id"),
                                message.getInt("label"),
                                message.getString("candidate")
                            )
                            peerConnection!!.addIceCandidate(candidate)
                        }
                        /*else if (message === 'bye' && isStarted) {
                        handleRemoteHangup();
                    }*/
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }?.on(Socket.EVENT_DISCONNECT) { args: Array<Any?>? ->
                Log.d(TAG, "connectToSignallingServer: disconnect")
            }
            socket?.connect()
        } catch (e: URISyntaxException) {
            e.printStackTrace()
        }
    }

    //MirtDPM4
    private fun doAnswer() {
        peerConnection!!.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                peerConnection!!.setLocalDescription(SimpleSdpObserver(), sessionDescription)
                val message = JSONObject()
                try {
                    message.put("type", "answer")
                    message.put("sdp", sessionDescription.description)
                    sendMessage(message)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
        }, MediaConstraints())
    }

    private fun maybeStart() {
        Log.d(TAG, "maybeStart: $isStarted $isChannelReady")
        if (!isStarted && isChannelReady) {
            isStarted = true
            if (isInitiator) {
                doCall()
            }
        }
    }

    private fun doCall() {
        val sdpMediaConstraints = MediaConstraints()

        sdpMediaConstraints.mandatory.add(
            MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true")
        )
        sdpMediaConstraints.mandatory.add(
            MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true")
        )
        peerConnection!!.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                Log.d(TAG, "onCreateSuccess: ")
                peerConnection!!.setLocalDescription(SimpleSdpObserver(), sessionDescription)
                val message = JSONObject()
                try {
                    message.put("type", "offer")
                    message.put("sdp", sessionDescription.description)
                    sendMessage(message)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
        }, sdpMediaConstraints)
    }

    private fun sendMessage(message: Any) {
        socket!!.emit("message", message)
    }

    private fun initializeSurfaceViews() {
        rootEglBase = EglBase.create()

        //        binding.surfaceView.init(rootEglBase.getEglBaseContext(), null);
//        binding.surfaceView.setEnableHardwareScaler(true);
//        binding.surfaceView.setMirror(true);
        binding!!.surfaceShareCamera.init(rootEglBase?.eglBaseContext, null)
        binding!!.surfaceShareCamera.setEnableHardwareScaler(true)
        binding!!.surfaceShareCamera.setMirror(true)

        //add one more
    }

    private fun initializePeerConnectionFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(application)
            .setEnableInternalTracer(true).setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
    }

    private fun createVideoTrackFromCameraAndShowIt() {
        audioConstraints = MediaConstraints()
        val videoCapturer = createVideoCapturer()
        val surfaceTextureHelper = SurfaceTextureHelper.create(
            Thread.currentThread().name, rootEglBase?.eglBaseContext
        )

        videoSource = peerConnectionFactory.createVideoSource(false)
        videoCapturer?.initialize(surfaceTextureHelper, application, videoSource?.capturerObserver)

        videoCapturer!!.startCapture(VIDEO_RESOLUTION_WIDTH, VIDEO_RESOLUTION_HEIGHT, FPS)

        videoTrackFromCamera = peerConnectionFactory!!.createVideoTrack(VIDEO_TRACK_ID, videoSource)
        videoTrackFromCamera?.setEnabled(true)

        //        videoTrackFromCamera.addRenderer(new VideoRenderer(binding.surfaceView));

        //create an AudioSource instance
        audioSource = peerConnectionFactory!!.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory!!.createAudioTrack("101", audioSource)


    }

    private fun initializePeerConnections() {
        peerConnection = createPeerConnection()
    }

    /**
     * The One who send the video stream
     */
    private fun startStreamingVideo() {
        val mediaStream = peerConnectionFactory!!.createLocalMediaStream(localStreamId)
        mediaStream.addTrack(videoTrackFromCamera)
        mediaStream.addTrack(localAudioTrack)
        peerConnection!!.addStream(mediaStream)
        Log.e("NotError", "MainActivity@startStreamingVideo")
        sendMessage("got user media")
    }

    private fun startScreenCapture() {
        Log.e("NotError", "MainActivity@startScreenCapture")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        val mediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        startActivityForResult(
            mediaProjectionManager.createScreenCaptureIntent(), capturePermissionRequestCode
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.e("NotError", "MainActivity@onActivityResult")
        if (requestCode != capturePermissionRequestCode) {
            return
        }

        screenPermissionIntent = data
        webrtcServiceRepository!!.requestConnection(
            Utils.getUsername(
                contentResolver, true
            )
        )
    }

    private fun createPeerConnectionFactory(): PeerConnectionFactory {
        return PeerConnectionFactory.builder().setVideoDecoderFactory(
            DefaultVideoDecoderFactory(rootEglBase?.eglBaseContext)
        ).setVideoEncoderFactory(
            DefaultVideoEncoderFactory(
                rootEglBase?.eglBaseContext, true, true
            )
        ).setOptions(PeerConnectionFactory.Options().apply {
            disableEncryption = false
            disableNetworkMonitor = false
        }).createPeerConnectionFactory()
    }


    private fun createPeerConnection(): PeerConnection? {
        val iceServers = ArrayList<PeerConnection.IceServer>()
        val url = "stun:stun.l.google.com:19302"
        iceServers.add(PeerConnection.IceServer(url))
        val pcObserver: PeerConnection.Observer = object : PeerConnection.Observer {
            override fun onSignalingChange(signalingState: PeerConnection.SignalingState) {
                Log.d(TAG, "onSignalingChange: ")
            }

            override fun onIceConnectionChange(iceConnectionState: PeerConnection.IceConnectionState) {
                Log.d(TAG, "onIceConnectionChange: ")
            }

            override fun onIceConnectionReceivingChange(b: Boolean) {
                Log.d(TAG, "onIceConnectionReceivingChange: ")
            }

            override fun onIceGatheringChange(iceGatheringState: PeerConnection.IceGatheringState) {
                Log.d(TAG, "onIceGatheringChange: ")
            }

            override fun onIceCandidate(iceCandidate: IceCandidate) {
                Log.d(TAG, "onIceCandidate: ")
                val message = JSONObject()

                try {
                    message.put("type", "candidate")
                    message.put("label", iceCandidate.sdpMLineIndex)
                    message.put("id", iceCandidate.sdpMid)
                    message.put("candidate", iceCandidate.sdp)

                    Log.d(TAG, "onIceCandidate: sending candidate $message")
                    sendMessage(message)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }

            override fun onIceCandidatesRemoved(iceCandidates: Array<IceCandidate>) {
                Log.d(TAG, "onIceCandidatesRemoved: ")
            }

            override fun onAddStream(mediaStream: MediaStream) {
                Log.d(TAG, "onAddStream: " + mediaStream.videoTracks.size)
                val remoteVideoTrack = mediaStream.videoTracks[0]
                val remoteAudioTrack = mediaStream.audioTracks[0]
                remoteAudioTrack.setEnabled(true)
                remoteVideoTrack.setEnabled(true)
                remoteVideoTrack.addSink(binding.surfaceShareCamera)
            }

            override fun onRemoveStream(mediaStream: MediaStream) {
                Log.d(TAG, "onRemoveStream: ")
            }

            override fun onDataChannel(dataChannel: DataChannel) {
                Log.d(TAG, "onDataChannel: ")
            }

            override fun onRenegotiationNeeded() {
                Log.d(TAG, "onRenegotiationNeeded: ")
            }

            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {

            }
        }

        return peerConnectionFactory.createPeerConnection(
            iceServers, pcObserver
        )
//        val iceServers = ArrayList<PeerConnection.IceServer>()
//        val URL = "stun:stun.l.google.com:19302"
//        iceServers.add(PeerConnection.IceServer(URL))
//
//        val rtcConfig = RTCConfiguration(iceServers)
//        val pcConstraints = MediaConstraints()
//
//        val pcObserver: PeerConnection.Observer = object : PeerConnection.Observer {
//            override fun onSignalingChange(signalingState: PeerConnection.SignalingState) {
//                Log.d(TAG, "onSignalingChange: ")
//            }
//
//            override fun onIceConnectionChange(iceConnectionState: IceConnectionState) {
//                Log.d(TAG, "onIceConnectionChange: ")
//            }
//
//            override fun onIceConnectionReceivingChange(b: Boolean) {
//                Log.d(TAG, "onIceConnectionReceivingChange: ")
//            }
//
//            override fun onIceGatheringChange(iceGatheringState: IceGatheringState) {
//                Log.d(TAG, "onIceGatheringChange: ")
//            }
//
//            override fun onIceCandidate(iceCandidate: IceCandidate) {
//                Log.d(TAG, "onIceCandidate: ")
//                val message = JSONObject()
//
//                try {
//                    message.put("type", "candidate")
//                    message.put("label", iceCandidate.sdpMLineIndex)
//                    message.put("id", iceCandidate.sdpMid)
//                    message.put("candidate", iceCandidate.sdp)
//
//                    Log.d(TAG, "onIceCandidate: sending candidate $message")
//                    sendMessage(message)
//                } catch (e: JSONException) {
//                    e.printStackTrace()
//                }
//            }
//
//            override fun onIceCandidatesRemoved(iceCandidates: Array<IceCandidate>) {
//                Log.d(TAG, "onIceCandidatesRemoved: ")
//            }
//
//            override fun onAddStream(mediaStream: MediaStream) {
//                Log.d(TAG, "onAddStream: " + mediaStream.videoTracks.size)
//                val remoteVideoTrack = mediaStream.videoTracks[0]
//                val remoteAudioTrack = mediaStream.audioTracks[0]
//                remoteAudioTrack.setEnabled(true)
//                remoteVideoTrack.setEnabled(true)
//                remoteVideoTrack.addRenderer(VideoRenderer(binding!!.surfaceShareCamera))
//            }
//
//            override fun onRemoveStream(mediaStream: MediaStream) {
//                Log.d(TAG, "onRemoveStream: ")
//            }
//
//            override fun onDataChannel(dataChannel: DataChannel) {
//                Log.d(TAG, "onDataChannel: ")
//            }
//
//            override fun onRenegotiationNeeded() {
//                Log.d(TAG, "onRenegotiationNeeded: ")
//            }
//        }
//
//        return factory!!.createPeerConnection(rtcConfig, pcConstraints, pcObserver)
    }

    private fun createVideoCapturer(): VideoCapturer? {
        val videoCapturer = if (useCamera2()) {
            createCameraCapturer(Camera2Enumerator(this))
        } else {
            createCameraCapturer(Camera1Enumerator(true))
        }
        return videoCapturer
    }

    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames

        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                val videoCapturer: VideoCapturer? = enumerator.createCapturer(deviceName, null)

                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }

        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                val videoCapturer: VideoCapturer? = enumerator.createCapturer(deviceName, null)

                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }

        return null
    }

    private fun useCamera2(): Boolean {
        return Camera2Enumerator.isSupported(this)
    }

    override fun onRemoteStreamAdded(stream: org.webrtc.MediaStream) {
        runOnUiThread {
            stream.videoTracks[0].addSink(
                binding.surfaceViewShareScreen
            )
        }
    }

    override fun onCallEndReceived() {
    }

    override fun onConnectionConnected() {
    }

    override fun onConnectionRequestReceived(target: String) {
        webrtcServiceRepository!!.acceptCAll(target)
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val RC_CALL = 111
        const val VIDEO_TRACK_ID: String = "ARDAMSv0"
        const val VIDEO_RESOLUTION_WIDTH: Int = 1280
        const val VIDEO_RESOLUTION_HEIGHT: Int = 720
        const val FPS: Int = 30

        private const val capturePermissionRequestCode = 1
    }
}
