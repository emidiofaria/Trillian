package com.drivingcoach.ui.session

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.drivingcoach.ui.session.tabs.ChartFragment
import com.drivingcoach.ui.session.tabs.CoachFragment
import com.drivingcoach.ui.session.tabs.LapsFragment
import com.drivingcoach.ui.session.tabs.analysis.AnalysisFragment

class SessionPagerAdapter(
    fragment: Fragment,
    private val sessionId: Long
) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> LapsFragment.newInstance(sessionId)
            1 -> CoachFragment.newInstance(sessionId)
            2 -> ChartFragment.newInstance(sessionId)
            3 -> AnalysisFragment.newInstance(sessionId)
            else -> throw IllegalArgumentException("Invalid position: $position")
        }
    }
}
