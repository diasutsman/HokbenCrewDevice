package id.hokben.crewdevice

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import id.hokben.crewdevice.databinding.FragmentShareScreenBinding
import id.hokben.crewdevice.repository.MainRepository
import id.hokben.crewdevice.service.WebrtcService.Companion.listener
import id.hokben.crewdevice.service.WebrtcService.Companion.screenPermissionIntent
import id.hokben.crewdevice.service.WebrtcService.Companion.surfaceView
import id.hokben.crewdevice.service.WebrtcServiceRepository
import id.hokben.crewdevice.utils.Utils
import org.webrtc.MediaStream
import javax.inject.Inject


@AndroidEntryPoint
class ShareScreenFragment : Fragment(), MainRepository.Listener {

    companion object {
        private const val TAG = "ShareScreenFragment"
        private const val RC_CALL = 111
        const val VIDEO_TRACK_ID: String = "ARDAMSv0"
        const val VIDEO_RESOLUTION_WIDTH: Int = 1280
        const val VIDEO_RESOLUTION_HEIGHT: Int = 720
        const val FPS: Int = 30

        private const val capturePermissionRequestCode = 1
    }

    @Inject
    lateinit var webrtcServiceRepository: WebrtcServiceRepository

    private lateinit var binding: FragmentShareScreenBinding


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding =
            FragmentShareScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initShareScreen()
    }

    @SuppressLint("NewApi")
    private fun initShareScreen() {
        Log.e("NotError", "MainActivity@initShareScreen")

        surfaceView = binding.surfaceViewShareScreen
        listener = this
        webrtcServiceRepository.startIntent(Utils.getUsername(activity?.contentResolver))
//        startScreenCapture()
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
                activity?.contentResolver,
                true
            )
        )
    }

    override fun onConnectionRequestReceived(target: String) {
        webrtcServiceRepository.acceptCAll(target)
    }

    override fun onRemoteStreamAdded(stream: MediaStream) {
        Log.d(TAG,"ShareScreenFragment@onRemoteStreamAdded: $stream")
        activity?.runOnUiThread {
            stream.videoTracks[0].addSink(
                binding.surfaceViewShareScreen
            )
        }
    }

    override fun onCallEndReceived() {
    }

    override fun onConnectionConnected() {
    }

}