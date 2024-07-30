package com.shivam.androidwebrtc.repository

import android.content.Intent
import android.util.Log
import com.shivam.androidwebrtc.socket.SocketClient
import com.shivam.androidwebrtc.utils.DataModel
import com.shivam.androidwebrtc.utils.DataModelType.*
import com.shivam.androidwebrtc.webrtc.MyPeerObserver
import com.shivam.androidwebrtc.webrtc.WebrtcClient
import com.google.gson.Gson
import org.webrtc.*
import javax.inject.Inject


class MainRepository @Inject constructor(
    private val socketClient: SocketClient,
    private val webrtcClient: WebrtcClient,
    private val gson: Gson
) : SocketClient.Listener, WebrtcClient.Listener {

    private lateinit var username: String
    private lateinit var target: String
    private lateinit var surfaceView: SurfaceViewRenderer
    var listener: Listener? = null

    fun init(username: String, surfaceView: SurfaceViewRenderer) {
        this.username = username
        this.surfaceView = surfaceView
        initSocket()
        initWebrtcClient()

    }

    private fun initSocket() {
        socketClient.listener = this
        socketClient.init(username)
    }

    fun setPermissionIntentToWebrtcClient(intent:Intent){
        Log.e("NotError", "MainRepository@setPermissionIntentToWebrtcClient")
        webrtcClient.setPermissionIntent(intent)
    }

    fun sendScreenShareConnection(target: String){
        Log.e("NotError", this.javaClass.kotlin.simpleName + "@sendScreenShareConnection")
        socketClient.sendMessageToSocket(
            DataModel(
                type = StartStreaming,
                username = username,
                target = target,
                null
            )
        )
    }

    fun startScreenCapturing(surfaceView: SurfaceViewRenderer){
        Log.e("NotError", "MainRepository@startScreenCapturing")
        webrtcClient.startScreenCapturing(surfaceView)
    }

    fun startCall(target: String){
        webrtcClient.call(target)
    }

    fun sendCallEndedToOtherPeer(){
        socketClient.sendMessageToSocket(
            DataModel(
                type = EndCall,
                username = username,
                target = target,
                null
            )
        )
    }

    fun restartRepository(){
        webrtcClient.restart()
    }

    fun onDestroy(){
        socketClient.onDestroy()
        webrtcClient.closeConnection()
    }

    private fun initWebrtcClient() {
        webrtcClient.listener = this
        webrtcClient.initializeWebrtcClient(username, surfaceView,
            object : MyPeerObserver() {
                override fun onIceCandidate(p0: IceCandidate?) {
                    super.onIceCandidate(p0)
                    p0?.let { webrtcClient.sendIceCandidate(it, target) }
                }

                override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
                    super.onIceConnectionChange(p0)
                    Log.d("TAG", "onConnectionChange: $p0")
                    if (p0 == PeerConnection.IceConnectionState.CONNECTED){
                        listener?.onConnectionConnected()
                    }
                }

                override fun onAddStream(p0: MediaStream?) {
                    super.onAddStream(p0)
                    Log.d("TAG", "onAddStream: $p0")
                    p0?.let { listener?.onRemoteStreamAdded(it) }
                }
            })
    }

    override fun onNewMessageReceived(model: DataModel) {
        Log.e("NotError", "MainRepository@onNewMessageReceived model: $model")
        when (model.type) {
            StartStreaming -> {
                this.target = model.username
                //notify ui, conneciton request is being made, so show it
                listener?.onConnectionRequestReceived(model.username)
            }
            EndCall -> {
                //notify ui call is ended
                listener?.onCallEndReceived()
            }
            Offer -> {
                webrtcClient.onRemoteSessionReceived(
                    SessionDescription(
                        SessionDescription.Type.OFFER, model.data
                            .toString()
                    )
                )
                this.target = model.username
                webrtcClient.answer(target)
            }
            Answer -> {
                webrtcClient.onRemoteSessionReceived(
                    SessionDescription(SessionDescription.Type.ANSWER, model.data.toString())
                )

            }
            IceCandidates -> {
                val candidate = try {
                    gson.fromJson(model.data.toString(), IceCandidate::class.java)
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
                candidate?.let {
                    webrtcClient.addIceCandidate(it)
                }
            }
            else -> Unit
        }
    }

    override fun onTransferEventToSocket(data: DataModel) {
        socketClient.sendMessageToSocket(data)
    }

    interface Listener {
        fun onConnectionRequestReceived(target: String)
        fun onConnectionConnected()
        fun onCallEndReceived()
        fun onRemoteStreamAdded(stream: MediaStream)
        fun onConnectionRequestReceived()
    }
}