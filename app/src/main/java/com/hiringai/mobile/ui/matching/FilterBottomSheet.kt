package com.hiringai.mobile.ui.matching

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.hiringai.mobile.R
import com.hiringai.mobile.databinding.DialogFilterOptionsBinding

/**
 * Filter bottom sheet dialog for multi-dimensional filtering of match results
 */
class FilterBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogFilterOptionsBinding? = null
    private val binding get() = _binding!!

    private var currentFilter: FilterOptions = FilterOptions()
    private var listener: FilterCallback? = null

    private lateinit var prefs: SharedPreferences

    interface FilterCallback {
        fun onFilterApplied(filter: FilterOptions)
    }

    fun setFilterCallback(listener: FilterCallback) {
        this.listener = listener
    }

    fun setCurrentFilter(filter: FilterOptions) {
        this.currentFilter = filter
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogFilterOptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        setupSpinners()
        setupSliders()
        setupButtons()
        loadSavedPreferences()
        updateUI()
    }

    private fun setupSpinners() {
        // Education spinner
        val educationAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            resources.getStringArray(R.array.education_options)
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerEducation.adapter = educationAdapter
        binding.spinnerEducation.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val values = resources.getStringArray(R.array.education_values)
                currentFilter = currentFilter.copy(educationLevel = values[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Salary spinner
        val salaryAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            resources.getStringArray(R.array.salary_options)
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerSalary.adapter = salaryAdapter
        binding.spinnerSalary.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val values = resources.getStringArray(R.array.salary_values)
                currentFilter = currentFilter.copy(salaryRange = values[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Recent active spinner
        val recentActiveAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            resources.getStringArray(R.array.recent_active_options)
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerRecentActive.adapter = recentActiveAdapter
        binding.spinnerRecentActive.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val values = resources.getStringArray(R.array.recent_active_values)
                currentFilter = currentFilter.copy(recentActiveDays = values[position].toInt())
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupSliders() {
        // Match score slider
        binding.rangeSliderMatchScore.addOnChangeListener { slider, _, _ ->
            val values = slider.values
            val min = values[0].toInt()
            val max = values[1].toInt()
            currentFilter = currentFilter.copy(minMatchScore = min, maxMatchScore = max)
            binding.tvMatchScoreRange.text = "${min}% - ${max}%"
        }

        // Experience slider
        binding.rangeSliderExperience.addOnChangeListener { slider, _, _ ->
            val values = slider.values
            val min = values[0].toInt()
            val max = values[1].toInt()
            currentFilter = currentFilter.copy(minExperienceYears = min, maxExperienceYears = max)
            binding.tvExperienceRange.text = "${min}年 - ${max}年"
        }
    }

    private fun setupButtons() {
        binding.btnReset.setOnClickListener {
            currentFilter = FilterOptions()
            updateUI()
        }

        binding.btnApply.setOnClickListener {
            // Get skills from input
            val skillsText = binding.etSkills.text.toString().trim()
            val skillsList = if (skillsText.isNotEmpty()) {
                skillsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            } else {
                emptyList()
            }
            currentFilter = currentFilter.copy(skills = skillsList)

            // Save preferences
            savePreferences()

            // Apply filter
            listener?.onFilterApplied(currentFilter)
            dismiss()
        }
    }

    private fun updateUI() {
        // Update match score slider
        binding.rangeSliderMatchScore.values = listOf(
            currentFilter.minMatchScore.toFloat(),
            currentFilter.maxMatchScore.toFloat()
        )
        binding.tvMatchScoreRange.text = "${currentFilter.minMatchScore}% - ${currentFilter.maxMatchScore}%"

        // Update experience slider
        binding.rangeSliderExperience.values = listOf(
            currentFilter.minExperienceYears.toFloat(),
            currentFilter.maxExperienceYears.toFloat()
        )
        binding.tvExperienceRange.text = "${currentFilter.minExperienceYears}年 - ${currentFilter.maxExperienceYears}年"

        // Update education spinner
        val educationValues = resources.getStringArray(R.array.education_values)
        val educationIndex = educationValues.indexOf(currentFilter.educationLevel).coerceAtLeast(0)
        binding.spinnerEducation.setSelection(educationIndex)

        // Update salary spinner
        val salaryValues = resources.getStringArray(R.array.salary_values)
        val salaryIndex = salaryValues.indexOf(currentFilter.salaryRange).coerceAtLeast(0)
        binding.spinnerSalary.setSelection(salaryIndex)

        // Update recent active spinner
        val recentActiveValues = resources.getStringArray(R.array.recent_active_values)
        val recentActiveIndex = recentActiveValues.indexOf(currentFilter.recentActiveDays.toString()).coerceAtLeast(0)
        binding.spinnerRecentActive.setSelection(recentActiveIndex)

        // Update skills input
        binding.etSkills.setText(currentFilter.skills.joinToString(", "))
    }

    private fun loadSavedPreferences() {
        currentFilter = FilterOptions(
            minMatchScore = prefs.getInt(KEY_MIN_MATCH_SCORE, 0),
            maxMatchScore = prefs.getInt(KEY_MAX_MATCH_SCORE, 100),
            minExperienceYears = prefs.getInt(KEY_MIN_EXPERIENCE, 0),
            maxExperienceYears = prefs.getInt(KEY_MAX_EXPERIENCE, 20),
            educationLevel = prefs.getString(KEY_EDUCATION, "all") ?: "all",
            salaryRange = prefs.getString(KEY_SALARY, "all") ?: "all",
            recentActiveDays = prefs.getInt(KEY_RECENT_ACTIVE, 0)
        )

        val savedSkills = prefs.getString(KEY_SKILLS, "") ?: ""
        currentFilter = currentFilter.copy(
            skills = if (savedSkills.isNotEmpty()) savedSkills.split(",") else emptyList()
        )
    }

    private fun savePreferences() {
        prefs.edit().apply {
            putInt(KEY_MIN_MATCH_SCORE, currentFilter.minMatchScore)
            putInt(KEY_MAX_MATCH_SCORE, currentFilter.maxMatchScore)
            putInt(KEY_MIN_EXPERIENCE, currentFilter.minExperienceYears)
            putInt(KEY_MAX_EXPERIENCE, currentFilter.maxExperienceYears)
            putString(KEY_EDUCATION, currentFilter.educationLevel)
            putString(KEY_SALARY, currentFilter.salaryRange)
            putInt(KEY_RECENT_ACTIVE, currentFilter.recentActiveDays)
            putString(KEY_SKILLS, currentFilter.skills.joinToString(","))
            apply()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "FilterBottomSheet"
        private const val PREFS_NAME = "filter_preferences"

        // Preference keys
        private const val KEY_MIN_MATCH_SCORE = "min_match_score"
        private const val KEY_MAX_MATCH_SCORE = "max_match_score"
        private const val KEY_MIN_EXPERIENCE = "min_experience"
        private const val KEY_MAX_EXPERIENCE = "max_experience"
        private const val KEY_EDUCATION = "education_level"
        private const val KEY_SALARY = "salary_range"
        private const val KEY_RECENT_ACTIVE = "recent_active_days"
        private const val KEY_SKILLS = "skills"

        fun newInstance(): FilterBottomSheet {
            return FilterBottomSheet()
        }
    }
}