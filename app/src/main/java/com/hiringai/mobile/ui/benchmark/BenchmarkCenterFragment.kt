package com.hiringai.mobile.ui.benchmark

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.hiringai.mobile.databinding.FragmentBenchmarkCenterBinding

/**
 * 基准测试中心 - 统一入口
 *
 * 提供三类AI模型的性能测试:
 * 1. 语音模型 (Whisper/Paraformer/Cam++)
 * 2. 图像模型 (Vision Transformer/SD/图像理解)
 * 3. 大语言模型 (Gemma 4 e2b/e4b等)
 */
class BenchmarkCenterFragment : Fragment() {

    private var _binding: FragmentBenchmarkCenterBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBenchmarkCenterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViewPager()
        setupTabLayout()
    }

    private fun setupViewPager() {
        val adapter = BenchmarkPagerAdapter(requireActivity())
        binding.viewPager.adapter = adapter
    }

    private fun setupTabLayout() {
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "语音模型"
                1 -> "图像模型"
                2 -> "LLM模型"
                else -> "测试$position"
            }
        }.attach()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * ViewPager 适配器
     */
    private inner class BenchmarkPagerAdapter(activity: FragmentActivity) :
        FragmentStateAdapter(activity) {

        override fun getItemCount(): Int = 3

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> BenchmarkSpeechFragment.newInstance()
                1 -> BenchmarkImageFragment()
                2 -> LLMBenchmarkFragment.newInstance()
                else -> Fragment()
            }
        }
    }

    companion object {
        fun newInstance() = BenchmarkCenterFragment()
    }
}
