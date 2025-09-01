package com.cyberflux.qwinai.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.cyberflux.qwinai.HomeFragment
import com.cyberflux.qwinai.HistoryFragment

/**
 * Adapter for the ViewPager2 in StartActivity
 * Manages the Home and History fragments
 */
class ViewPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 2 // Two pages: Home and History

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> HomeFragment()
            1 -> HistoryFragment()
            else -> HomeFragment() // Default to Home
        }
    }
}