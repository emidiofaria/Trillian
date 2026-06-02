package com.bmw.drivingcoach.ui.session

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.bmw.drivingcoach.ui.session.tabs.ChartFragment
import com.bmw.drivingcoach.ui.session.tabs.CoachFragment
import com.bmw.drivingcoach.ui.session.tabs.LapsFragment

class SessionPagerAdapter(
    fragment: Fragment,
    private val sessionId: Long
) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> LapsFragment.newInstance(sessionId)
            1 -> CoachFragment.newInstance(sessionId)
            2 -> ChartFragment.newInstance(sessionId)
            else -> throw IllegalArgumentException("Invalid position: $position")
        }
    }
}
