package com.cyberflux.qwinai.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.cyberflux.qwinai.HomeFragment
import com.cyberflux.qwinai.HistoryFragment

/**
 * Adapter for the ViewPager2 in StartActivity
 * Only manages Home and History fragments - Chat and Image open activities directly
 * Uses 2 actual ViewPager positions that map to tab positions 0 and 3
 */
class ViewPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 2 // Only 2 actual fragments: Home and History

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> HomeFragment()      // ViewPager position 0 = Tab position 0 (Home)
            1 -> HistoryFragment()   // ViewPager position 1 = Tab position 3 (History)
            else -> HomeFragment()
        }
    }
    
    /**
     * Convert tab position to ViewPager position
     * Tab 0 (Home) → ViewPager 0
     * Tab 3 (History) → ViewPager 1
     */
    fun getViewPagerPositionForTab(tabPosition: Int): Int {
        return when (tabPosition) {
            0 -> 0  // Home tab → ViewPager position 0
            3 -> 1  // History tab → ViewPager position 1
            else -> 0 // Default to Home
        }
    }
    
    /**
     * Convert ViewPager position to tab position
     * ViewPager 0 → Tab 0 (Home)
     * ViewPager 1 → Tab 3 (History)
     */
    fun getTabPositionForViewPager(viewPagerPosition: Int): Int {
        return when (viewPagerPosition) {
            0 -> 0  // ViewPager position 0 → Home tab
            1 -> 3  // ViewPager position 1 → History tab
            else -> 0 // Default to Home
        }
    }
}