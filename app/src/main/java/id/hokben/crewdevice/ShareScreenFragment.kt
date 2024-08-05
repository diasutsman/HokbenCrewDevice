package id.hokben.crewdevice

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import id.hokben.crewdevice.databinding.FragmentShareScreenBinding
import org.webrtc.EglBase
import org.webrtc.RendererCommon


@AndroidEntryPoint
class ShareScreenFragment(private val rootEglBase: EglBase) : Fragment() {

    lateinit var binding: FragmentShareScreenBinding


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


        binding.surfaceViewShareScreen.init(rootEglBase.eglBaseContext, null)
        binding.surfaceViewShareScreen.setEnableHardwareScaler(true)
        binding.surfaceViewShareScreen.setMirror(false)

        binding.surfaceViewShareScreen.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
    }


}