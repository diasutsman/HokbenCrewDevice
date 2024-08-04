package id.hokben.crewdevice

import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import id.hokben.crewdevice.databinding.ActivityMainBinding


@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private lateinit var shareCameraFragment: ShareCameraFragment;
    private lateinit var shareScreenFragment: ShareScreenFragment;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.toolbar.title = "${getString(R.string.app_name)}, Brand Name: ${Build.BRAND}"
        setSupportActionBar(binding.toolbar);
        initFragments()
        initViewpager()
    }

    private fun initViewpager() {
        val fragments = listOf(
            shareCameraFragment,
            shareScreenFragment
        ) // Replace with your fragment instances
        val adapter = ViewPagerAdapter(fragments, this)
        binding.pager.adapter = adapter
        binding.pager.offscreenPageLimit = 3 // add this so that the share screen and the camera always shown
        TabLayoutMediator(binding.tabLayout, binding.pager) { tab, position ->
            // Set tab text or icon here if needed
            when (position) {
                0 -> {
                    tab.text = "Client Camera"
                }

                1 -> {
                    tab.text = "Client Screen"
                }
            }

        }.attach()
    }

    private fun initFragments() {
        shareCameraFragment = ShareCameraFragment()
        shareScreenFragment = ShareScreenFragment()
    }

    private inner class ViewPagerAdapter(
        private val fragments: List<Fragment>,
        fa: FragmentActivity
    ) : FragmentStateAdapter(fa) {

        override fun getItemCount(): Int = fragments.size

        override fun createFragment(position: Int): Fragment = fragments[position]
    }

}
