package com.hiringai.mobile.ui.candidates

import android.app.Activity
import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.hiringai.mobile.R
import com.hiringai.mobile.data.local.AppDatabase
import com.hiringai.mobile.data.local.entity.CandidateEntity
import com.hiringai.mobile.databinding.FragmentCandidatesBinding
import com.hiringai.mobile.ml.LocalLLMService
import com.hiringai.mobile.util.PdfExtractor
import kotlinx.coroutines.launch

class CandidatesFragment : Fragment() {

    private var _binding: FragmentCandidatesBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: AppDatabase
    private lateinit var llmService: LocalLLMService
    private lateinit var adapter: CandidateAdapter

    // For PDF upload
    private var pendingResume: String? = null
    private var selectedPdfUri: Uri? = null

    private val pdfPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedPdfUri = uri
                processPdfAndAnalyze(uri)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCandidatesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = AppDatabase.getInstance(requireContext())
        llmService = LocalLLMService.getInstance(requireContext())

        setupRecyclerView()
        setupFab()
        setupBatchImport()
        loadCandidates()
    }

    private fun setupRecyclerView() {
        adapter = CandidateAdapter { candidate ->
            showCandidateDetails(candidate)
        }
        binding.rvCandidates.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCandidates.adapter = adapter
    }

    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            showAddCandidateDialog()
        }
    }

    private fun setupBatchImport() {
        binding.btnBatchImport.setOnClickListener {
            // Open file picker for multiple PDFs
            val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(android.content.Intent.CATEGORY_OPENABLE)
                type = "application/pdf"
                putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            batchPickerLauncher.launch(intent)
        }
    }

    private val batchPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val clipData = result.data?.clipData
            val uris = mutableListOf<Uri>()

            if (clipData != null) {
                for (i in 0 until clipData.itemCount) {
                    uris.add(clipData.getItemAt(i).uri)
                }
            } else {
                result.data?.data?.let { uris.add(it) }
            }

            if (uris.isNotEmpty()) {
                startBatchImport(uris)
            }
        }
    }

    private fun startBatchImport(uris: List<Uri>) {
        if (!llmService.isModelLoaded) {
            Toast.makeText(requireContext(), "请先在设置中加载LLM模型", Toast.LENGTH_SHORT).show()
            return
        }

        binding.cardImportProgress.visibility = View.VISIBLE
        binding.btnBatchImport.isEnabled = false

        val total = uris.size
        var processed = 0
        var success = 0
        var failed = 0

        lifecycleScope.launch {
            for (uri in uris) {
                try {
                    requireActivity().runOnUiThread {
                        binding.tvImportStatus.text = "正在处理: ${processed + 1}/$total"
                        binding.progressImport.progress = ((processed * 100) / total)
                        binding.tvImportDetail.text = "提取PDF文本中..."
                    }

                    // Extract PDF text
                    val text = PdfExtractor.extractText(requireContext(), uri)
                    if (text == null || text.isEmpty()) {
                        failed++
                        processed++
                        continue
                    }

                    val cleanText = PdfExtractor.cleanText(text)

                    requireActivity().runOnUiThread {
                        binding.tvImportDetail.text = "AI分析中... (${cleanText.length}字符)"
                    }

                    // Extract name from filename or PDF content
                    val fileName = getFileNameFromUri(uri)
                    val candidateName = extractNameFromFileName(fileName)

                    // Generate AI profile
                    val profile = generateCandidateProfile(cleanText)

                    if (profile != null) {
                        // Save to database
                        db.candidateDao().insert(
                            CandidateEntity(
                                name = candidateName,
                                email = "",
                                phone = "",
                                resume = profile
                            )
                        )
                        success++
                    } else {
                        failed++
                    }

                } catch (e: Exception) {
                    failed++
                }
                processed++
            }

            requireActivity().runOnUiThread {
                binding.tvImportStatus.text = "导入完成!"
                binding.progressImport.progress = 100
                binding.tvImportDetail.text = "成功: $success, 失败: $failed"
                binding.btnBatchImport.isEnabled = true

                Toast.makeText(
                    requireContext(),
                    "批量导入完成! 成功: $success, 失败: $failed",
                    Toast.LENGTH_LONG
                ).show()

                // Hide progress card after delay
                binding.root.postDelayed({
                    binding.cardImportProgress.visibility = View.GONE
                    loadCandidates()
                }, 3000)
            }
        }
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var name = "未知"
        requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    private fun extractNameFromFileName(fileName: String): String {
        // Try to extract name from patterns like "姓名 6年.pdf" or "[姓名]_上海"
        // Remove file extension
        val nameWithoutExt = fileName.substringBeforeLast(".")
        // Try to find Chinese name pattern (usually at the end before years)
        val match = Regex("""[\u4e00-\u9fa5]+""").findAll(nameWithoutExt).lastOrNull()
        return match?.value ?: "候选人_${System.currentTimeMillis() % 10000}"
    }

    private suspend fun generateCandidateProfile(resumeText: String): String? {
        val prompt = buildString {
            append("请分析以下简历，生成结构化候选人画像:\n\n")
            append("简历内容:\n$resumeText\n\n")
            append("请按以下格式输出:\n")
            append("【基本信息】\n")
            append("姓名: (从简历中提取)\n")
            append("联系方式: (如有)\n\n")
            append("【教育背景】\n")
            append("- 学校, 学历, 专业, 时间\n\n")
            append("【工作经历】\n")
            append("- 公司, 职位, 时间, 主要职责\n\n")
            append("【技能清单】\n")
            append("- 技术技能\n")
            append("- 软技能\n\n")
            append("【项目经验】\n")
            append("- 项目名称, 角色, 技术栈, 成果\n\n")
            append("【证书培训】\n")
            append("- 证书名称, 获得时间\n\n")
            append("【自我评价】\n")
            append("- 职业目标, 个人优势")
        }

        return llmService.generate(prompt, maxTokens = 800)
    }

    private fun loadCandidates() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val candidates = db.candidateDao().getAll()

            requireActivity().runOnUiThread {
                binding.progressBar.visibility = View.GONE

                if (candidates.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.rvCandidates.visibility = View.GONE
                } else {
                    binding.tvEmpty.visibility = View.GONE
                    binding.rvCandidates.visibility = View.VISIBLE
                    adapter.submitList(candidates)
                }
            }
        }
    }

    private fun showAddCandidateDialog() {
        pendingResume = null
        selectedPdfUri = null

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
        }

        val etName = EditText(requireContext()).apply {
            hint = "姓名 (必填)"
        }
        layout.addView(etName)

        val etEmail = EditText(requireContext()).apply {
            hint = "邮箱 (选填)"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        layout.addView(etEmail)

        val etPhone = EditText(requireContext()).apply {
            hint = "电话 (选填)"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        layout.addView(etPhone)

        val tvResumeLabel = TextView(requireContext()).apply {
            text = "简历 (PDF):"
            setPadding(0, 20, 0, 10)
        }
        layout.addView(tvResumeLabel)

        val btnUploadPdf = Button(requireContext()).apply {
            text = "上传 PDF 简历"
        }
        layout.addView(btnUploadPdf)

        val tvPdfStatus = TextView(requireContext()).apply {
            text = "未选择文件"
            setTextColor(android.graphics.Color.GRAY)
        }
        layout.addView(tvPdfStatus)

        val etResume = EditText(requireContext()).apply {
            hint = "或手动输入简历内容"
            minLines = 3
        }
        layout.addView(etResume)

        // PDF upload button click
        btnUploadPdf.setOnClickListener {
            val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(android.content.Intent.CATEGORY_OPENABLE)
                type = "application/pdf"
            }
            pdfPickerLauncher.launch(intent)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("添加候选人")
            .setView(layout)
            .setPositiveButton("添加") { _, _ ->
                val name = etName.text.toString().trim()
                val email = etEmail.text.toString().trim()
                val phone = etPhone.text.toString().trim()
                val manualResume = etResume.text.toString().trim()
                val resume = pendingResume ?: manualResume

                if (name.isEmpty()) {
                    Toast.makeText(requireContext(), "请输入姓名", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // If LLM is loaded, offer to analyze
                if (llmService.isModelLoaded && resume.isNotEmpty()) {
                    showAnalyzeDialog(name, email, phone, resume)
                } else {
                    addCandidate(name, email, phone, resume)
                }
            }
            .setNegativeButton("取消", null)
            .create()

        dialog.show()
    }

    private fun processPdfAndAnalyze(uri: Uri) {
        lifecycleScope.launch {
            Toast.makeText(requireContext(), "正在提取PDF内容...", Toast.LENGTH_SHORT).show()

            val extractedText = PdfExtractor.extractText(requireContext(), uri)
            if (extractedText != null && extractedText.isNotEmpty()) {
                pendingResume = PdfExtractor.cleanText(extractedText)
                Toast.makeText(requireContext(), "PDF提取成功! (${pendingResume?.length}字符)", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "PDF提取失败，请手动输入", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAnalyzeDialog(name: String, email: String, phone: String, resume: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("AI 画像分析")
            .setMessage("是否使用 AI 分析简历内容?")
            .setPositiveButton("分析") { _, _ ->
                analyzeCandidate(name, email, phone, resume)
            }
            .setNegativeButton("跳过") { _, _ ->
                addCandidate(name, email, phone, resume)
            }
            .show()
    }

    private fun analyzeCandidate(name: String, email: String, phone: String, resume: String) {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val prompt = buildString {
                append("请分析以下简历，生成候选人画像:\n\n")
                append("简历内容:\n$resume\n\n")
                append("请分析:\n")
                append("1. 姓名/联系方式 (如有)\n")
                append("2. 教育背景\n")
                append("3. 工作经历 (列出公司/职位/年限)\n")
                append("4. 技能清单 (技术技能/软技能)\n")
                append("5. 项目经验 (关键项目描述)\n")
                append("6. 证书/培训 (如有)\n")
                append("7. 自我评价/职业目标 (如有)\n")
            }

            val analysis = llmService.generate(prompt, maxTokens = 500)

            requireActivity().runOnUiThread {
                binding.progressBar.visibility = View.GONE

                if (analysis != null) {
                    val fullResume = "【简历分析】\n$analysis\n\n【原始内容】\n$resume"
                    addCandidate(name, email, phone, fullResume)
                    Toast.makeText(requireContext(), "简历分析完成!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "分析失败，使用原始内容", Toast.LENGTH_SHORT).show()
                    addCandidate(name, email, phone, resume)
                }
            }
        }
    }

    private fun addCandidate(name: String, email: String, phone: String, resume: String) {
        lifecycleScope.launch {
            db.candidateDao().insert(
                CandidateEntity(
                    name = name,
                    email = email,
                    phone = phone,
                    resume = resume
                )
            )

            requireActivity().runOnUiThread {
                Toast.makeText(requireContext(), "候选人已添加", Toast.LENGTH_SHORT).show()
                loadCandidates()
            }
        }
    }

    private fun showCandidateDetails(candidate: CandidateEntity) {
        val details = buildString {
            append("姓名: ${candidate.name}\n")
            append("邮箱: ${candidate.email.ifEmpty { "无" }}\n")
            append("电话: ${candidate.phone.ifEmpty { "无" }}\n")
            append("简历: ${candidate.resume.ifEmpty { "无" }}\n")
        }

        AlertDialog.Builder(requireContext())
            .setTitle(candidate.name)
            .setMessage(details)
            .setPositiveButton("确定", null)
            .setNegativeButton("删除") { _, _ ->
                deleteCandidate(candidate)
            }
            .show()
    }

    private fun deleteCandidate(candidate: CandidateEntity) {
        lifecycleScope.launch {
            db.candidateDao().delete(candidate)

            requireActivity().runOnUiThread {
                Toast.makeText(requireContext(), "候选人已删除", Toast.LENGTH_SHORT).show()
                loadCandidates()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}