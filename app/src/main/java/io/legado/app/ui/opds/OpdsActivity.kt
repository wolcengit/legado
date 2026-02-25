@file:Suppress("DEPRECATION")

package io.legado.app.ui.opds

import android.os.Bundle
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentStatePagerAdapter
import io.legado.app.base.BaseActivity
import io.legado.app.databinding.ActivityOpdsBinding
import io.legado.app.lib.theme.accentColor
import io.legado.app.utils.viewbindingdelegate.viewBinding

class OpdsActivity : BaseActivity<ActivityOpdsBinding>() {

    override val binding by viewBinding(ActivityOpdsBinding::inflate)
    private val adapter by lazy { TabFragmentPageAdapter() }
    private val tabTitles = arrayOf("源管理", "浏览")

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.viewPager.adapter = adapter
        binding.tabLayout.setupWithViewPager(binding.viewPager)
        binding.tabLayout.setSelectedTabIndicatorColor(accentColor)
        onBackPressedDispatcher.addCallback(this) {
            val browseFragment = supportFragmentManager.fragments
                .filterIsInstance<OpdsBrowseFragment>()
                .firstOrNull()
            if (browseFragment == null || !browseFragment.onBackPressed()) {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    private inner class TabFragmentPageAdapter :
        FragmentStatePagerAdapter(supportFragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

        override fun getPageTitle(position: Int): CharSequence {
            return tabTitles[position]
        }

        override fun getItem(position: Int): Fragment {
            return when (position) {
                0 -> OpdsSourceFragment()
                1 -> OpdsBrowseFragment()
                else -> throw IllegalArgumentException("Invalid tab position: $position")
            }
        }

        override fun getCount(): Int = tabTitles.size
    }
}
