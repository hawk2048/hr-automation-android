package com.hiringai.mobile.ui

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.hiringai.mobile.HiringAIApplication
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
        
        // Show previous crash log if exists
        checkCrashLog()
    }
    
    private fun checkCrashLog() {
        val crashLog = HiringAIApplication.getCrashLog(application)
        if (crashLog != null) {
            HiringAIApplication.clearCrashLog(application)
            AlertDialog.Builder(this)
                .setTitle("Previous Crash Log")
                .setMessage(crashLog.take(2000))
                .setPositiveButton("OK") { _, _ -> }
                .setNeutralButton("Copy") { _, _ ->
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("crash_log", crashLog))
                    Toast.makeText(this, "Crash log copied to clipboard", Toast.LENGTH_SHORT).show()
                }
                .show()
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