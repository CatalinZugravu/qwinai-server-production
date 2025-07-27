package com.cyberflux.qwinai

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.SearchView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout

/**
 * View binding class for the fragment_history.xml layout
 */
class FragmentHistoryBinding private constructor(private val rootView: View) {
    val root: View = rootView
    val tvHistoryTitle: TextView = rootView.findViewById(R.id.tvHistoryTitle)
    val btnSettings: ImageButton = rootView.findViewById(R.id.btnSettings)
    val tvMessage: TextView = rootView.findViewById(R.id.tvMessage)
    val searchView: SearchView = rootView.findViewById(R.id.searchView)
    val tabLayout: TabLayout = rootView.findViewById(R.id.tabLayout)
    val viewPager: ViewPager2 = rootView.findViewById(R.id.viewPager)

    companion object {
        fun inflate(inflater: LayoutInflater, parent: ViewGroup?, attachToParent: Boolean): FragmentHistoryBinding {
            val root = inflater.inflate(R.layout.fragment_history, parent, attachToParent)
            return FragmentHistoryBinding(root)
        }
    }
}