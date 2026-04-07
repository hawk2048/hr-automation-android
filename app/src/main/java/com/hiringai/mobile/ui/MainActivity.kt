package com.hiringai.mobile.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.hiringai.mobile.R
import com.hiringai.mobile.databinding.ActivityMainBinding
import com.hiringai.mobile.ui.jobs.JobsFragment
import com.hiringai.mobile.ui.candidates.CandidatesFragment
import com.hiringai.mobile.ui.matches.MatchesFragment
import com.hiringai.mobile.ui.settings.SettingsFragment

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupBottomNavigation()
        
        if (savedInstanceState == null) {
            loadFragment(JobsFragment())
        }
    }
    
    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_jobs -> JobsFragment()
                R.id.nav_candidates -> CandidatesFragment()
                R.id.nav_matches -> MatchesFragment()
                R.id.nav_settings -> SettingsFragment()
                else -> return@setOnItemSelectedListener false
            }
            loadFragment(fragment)
            true
        }
    }
    
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}