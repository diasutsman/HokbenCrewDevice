package id.hokben.crewdevice

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import id.hokben.crewdevice.databinding.FragmentShareCameraBinding
import org.webrtc.EglBase


class ShareCameraFragment(private val rootEglBase: EglBase) : Fragment() {

    lateinit var binding: FragmentShareCameraBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentShareCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.surfaceShareCamera.init(rootEglBase.eglBaseContext, null)
        binding.surfaceShareCamera.setEnableHardwareScaler(true)
        binding.surfaceShareCamera.setMirror(true)

    }

}