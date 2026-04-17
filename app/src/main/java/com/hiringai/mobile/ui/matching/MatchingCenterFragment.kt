package com.hiringai.mobile.ui.matching

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.hiringai.mobile.R
import com.hiringai.mobile.databinding.FragmentMatchingCenterBinding

class MatchingCenterFragment : Fragment() {

    private var _binding: FragmentMatchingCenterBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMatchingCenterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViewPager()
    }

    private fun setupViewPager() {
        val adapter = MatchingPagerAdapter(this)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.tab_candidate_to_job)
                1 -> getString(R.string.tab_job_to_candidate)
                else -> ""
            }
        }.attach()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class MatchingPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> CandidateToJobFragment()
                1 -> JobToCandidateFragment()
                else -> throw IllegalArgumentException("Invalid position: $position")
            }
        }
    }

    companion object {
        fun newInstance() = MatchingCenterFragment()
    }
}