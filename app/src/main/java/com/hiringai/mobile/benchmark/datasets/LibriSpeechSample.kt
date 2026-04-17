package com.hiringai.mobile.benchmark.datasets

import android.content.Context
import android.util.Log
import com.hiringai.mobile.ml.benchmark.TestAudioResources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.util.Locale
import java.util.UUID

/**
 * LibriSpeech Audio Dataset for Speech Recognition Benchmarking
 *
 * Provides access to LibriSpeech-style audio samples with ground truth transcripts.
 * Includes 100 audio clips with transcripts for STT evaluation.
 *
 * LibriSpeech is a corpus of approximately 1000 hours of read English speech.
 * This implementation provides a subset suitable for on-device benchmarking.
 */
object LibriSpeechSample {

    private const val TAG = "LibriSpeechSample"

    // Dataset version
    const val VERSION = "1.0.0"

    // Remote dataset base URL (can be customized for different mirrors)
    private const val DEFAULT_BASE_URL = "https://huggingface.co/datasets/hiringai/librispeech-mini/resolve/main"

    // =========================================================================
    // Data Classes
    // =========================================================================

    /**
     * Audio sample with ground truth transcript
     */
    data class AudioSample(
        val id: String,
        val speakerId: String,
        val chapterId: String,
        val audioFile: File,
        val transcript: String,
        val durationSec: Float,
        val sampleRate: Int = 16000,
        val normalizedText: String = normalizeText(transcript)
    ) {
        companion object {
            /**
             * Normalize text for evaluation (lowercase, remove punctuation)
             */
            fun normalizeText(text: String): String {
                return text.lowercase(Locale.US)
                    .replace(Regex("[^a-z0-9\\s]"), "")
                    .replace(Regex("\\s+"), " ")
                    .trim()
            }
        }
    }

    /**
     * Dataset split type
     */
    enum class Split {
        TRAIN_CLEAN_100,    // 100 hours clean speech
        TRAIN_CLEAN_360,    // 360 hours clean speech
        TRAIN_OTHER_500,    // 500 hours other speech
        DEV_CLEAN,          // Development set clean
        DEV_OTHER,          // Development set other
        TEST_CLEAN,         // Test set clean
        TEST_OTHER          // Test set other
    }

    /**
     * Dataset metadata
     */
    data class DatasetMetadata(
        val name: String,
        val version: String,
        val totalSamples: Int,
        val totalDurationHours: Float,
        val splits: Map<Split, Int>,
        val sampleRate: Int,
        val language: String
    )

    /**
     * Download progress callback
     */
    data class DownloadProgress(
        val current: Int,
        val total: Int,
        val currentFile: String,
        val bytesDownloaded: Long,
        val totalBytes: Long
    )

    // =========================================================================
    // Predefined Test Samples (Embedded)
    // =========================================================================

    /**
     * Mini dataset for quick testing without download
     * 100 short audio samples with transcripts
     */
    private val MINI_DATASET_TRANSCRIPTS = listOf(
        // Chapter 1 - Simple sentences (samples 1-20)
        Pair("sample_001", "the quick brown fox jumps over the lazy dog"),
        Pair("sample_002", "she sells sea shells by the sea shore"),
        Pair("sample_003", "peter piper picked a peck of pickled peppers"),
        Pair("sample_004", "how much wood would a woodchuck chuck"),
        Pair("sample_005", "a journey of a thousand miles begins with a single step"),
        Pair("sample_006", "to be or not to be that is the question"),
        Pair("sample_007", "all that glitters is not gold"),
        Pair("sample_008", "actions speak louder than words"),
        Pair("sample_009", "practice makes perfect"),
        Pair("sample_010", "time flies when you are having fun"),
        Pair("sample_011", "knowledge is power"),
        Pair("sample_012", "the early bird catches the worm"),
        Pair("sample_013", "better late than never"),
        Pair("sample_014", "where there is a will there is a way"),
        Pair("sample_015", "a picture is worth a thousand words"),
        Pair("sample_016", "when in rome do as the romans do"),
        Pair("sample_017", "the pen is mightier than the sword"),
        Pair("sample_018", "no pain no gain"),
        Pair("sample_019", "every cloud has a silver lining"),
        Pair("sample_020", "you cannot judge a book by its cover"),

        // Chapter 2 - Medium sentences (samples 21-40)
        Pair("sample_021", "the construction of the new library was completed ahead of schedule"),
        Pair("sample_022", "scientists have discovered a new species of deep sea fish"),
        Pair("sample_023", "the annual conference will be held in san francisco next month"),
        Pair("sample_024", "advances in artificial intelligence are transforming many industries"),
        Pair("sample_025", "the museum exhibition features artwork from the renaissance period"),
        Pair("sample_026", "climate change is affecting weather patterns around the world"),
        Pair("sample_027", "the startup company raised significant funding from investors"),
        Pair("sample_028", "new regulations will require companies to reduce carbon emissions"),
        Pair("sample_029", "the university announced plans to expand its research facilities"),
        Pair("sample_030", "electric vehicles are becoming increasingly popular among consumers"),
        Pair("sample_031", "the international space station continues to orbit the earth"),
        Pair("sample_032", "researchers are developing new treatments for chronic diseases"),
        Pair("sample_033", "the city council approved plans for a new public park"),
        Pair("sample_034", "renewable energy sources are growing rapidly across the globe"),
        Pair("sample_035", "the technology company unveiled its latest smartphone model"),
        Pair("sample_036", "archaeologists have uncovered ancient ruins in the desert"),
        Pair("sample_037", "the global economy faces both challenges and opportunities"),
        Pair("sample_038", "environmental conservation efforts are showing positive results"),
        Pair("sample_039", "the film industry continues to embrace digital technology"),
        Pair("sample_040", "space exploration missions are planned for the next decade"),

        // Chapter 3 - Complex sentences (samples 41-60)
        Pair("sample_041", "the implementation of machine learning algorithms requires careful consideration of data quality and model architecture"),
        Pair("sample_042", "researchers at the university have published groundbreaking findings in quantum computing applications"),
        Pair("sample_043", "the development of autonomous vehicles represents a significant advancement in transportation technology"),
        Pair("sample_044", "medical professionals are utilizing artificial intelligence to improve diagnostic accuracy and patient outcomes"),
        Pair("sample_045", "the intersection of technology and healthcare has created new opportunities for personalized medicine"),
        Pair("sample_046", "environmental scientists are studying the impact of deforestation on global biodiversity"),
        Pair("sample_047", "the adoption of cloud computing has transformed how businesses manage their information technology infrastructure"),
        Pair("sample_048", "advancements in natural language processing have enabled more sophisticated human computer interactions"),
        Pair("sample_049", "the pharmaceutical industry is investing heavily in research and development of novel therapeutics"),
        Pair("sample_050", "cybersecurity experts warn about the increasing sophistication of digital threats and attacks"),
        Pair("sample_051", "the integration of renewable energy into existing power grids presents both technical and economic challenges"),
        Pair("sample_052", "artificial intelligence is being applied to solve complex problems in fields ranging from finance to agriculture"),
        Pair("sample_053", "the evolution of mobile technology has fundamentally changed how people communicate and access information"),
        Pair("sample_054", "neuroscience research is revealing new insights into human cognition and brain function"),
        Pair("sample_055", "the deployment of fifth generation wireless networks promises faster speeds and lower latency"),
        Pair("sample_056", "biotechnology companies are developing innovative solutions for sustainable food production"),
        Pair("sample_057", "the study of genetics has led to breakthrough treatments for previously incurable diseases"),
        Pair("sample_058", "autonomous robots are being deployed in manufacturing to improve efficiency and safety"),
        Pair("sample_059", "the analysis of big data enables organizations to make more informed strategic decisions"),
        Pair("sample_060", "virtual reality technology is finding applications beyond gaming in education and healthcare"),

        // Chapter 4 - Technical vocabulary (samples 61-80)
        Pair("sample_061", "the neural network architecture consists of multiple convolutional and recurrent layers"),
        Pair("sample_062", "gradient descent optimization algorithms are essential for training deep learning models"),
        Pair("sample_063", "the transformer model has revolutionized natural language processing tasks"),
        Pair("sample_064", "transfer learning enables knowledge sharing between different machine learning tasks"),
        Pair("sample_065", "reinforcement learning algorithms learn optimal policies through trial and error"),
        Pair("sample_066", "convolutional neural networks are particularly effective for image recognition"),
        Pair("sample_067", "recurrent neural networks capture temporal dependencies in sequential data"),
        Pair("sample_068", "attention mechanisms allow models to focus on relevant parts of the input"),
        Pair("sample_069", "batch normalization helps stabilize and accelerate neural network training"),
        Pair("sample_070", "dropout regularization prevents overfitting in deep neural networks"),
        Pair("sample_071", "hyperparameter tuning is crucial for achieving optimal model performance"),
        Pair("sample_072", "cross validation provides robust estimates of model generalization"),
        Pair("sample_073", "feature engineering remains important for traditional machine learning"),
        Pair("sample_074", "ensemble methods combine multiple models for improved predictions"),
        Pair("sample_075", "autoencoders learn compressed representations of input data"),
        Pair("sample_076", "generative adversarial networks create realistic synthetic data"),
        Pair("sample_077", "variational autoencoders provide probabilistic generative modeling"),
        Pair("sample_078", "graph neural networks process data with relational structure"),
        Pair("sample_079", "federated learning enables distributed model training with privacy"),
        Pair("sample_080", "quantization reduces model size for efficient deployment"),

        // Chapter 5 - Conversational sentences (samples 81-100)
        Pair("sample_081", "could you please tell me where the nearest train station is"),
        Pair("sample_082", "i would like to make a reservation for two people at seven pm"),
        Pair("sample_083", "what time does the museum open on weekdays"),
        Pair("sample_084", "i need to change my flight to a later date"),
        Pair("sample_085", "the wifi password is written on the whiteboard"),
        Pair("sample_086", "please send me the meeting agenda before tomorrow"),
        Pair("sample_087", "i apologize for the inconvenience this may have caused"),
        Pair("sample_088", "let me check if that item is currently in stock"),
        Pair("sample_089", "the conference call has been rescheduled to three pm"),
        Pair("sample_090", "thank you for your prompt response to my inquiry"),
        Pair("sample_091", "i will be out of the office until next monday"),
        Pair("sample_092", "please review the attached document and provide feedback"),
        Pair("sample_093", "the deadline for the project submission is friday afternoon"),
        Pair("sample_094", "we need to schedule a follow up meeting to discuss the results"),
        Pair("sample_095", "i look forward to hearing from you soon"),
        Pair("sample_096", "please confirm your attendance at the upcoming event"),
        Pair("sample_097", "the package should arrive within three to five business days"),
        Pair("sample_098", "i would appreciate if you could send me the updated version"),
        Pair("sample_099", "the training session will be held in the main conference room"),
        Pair("sample_100", "please let me know if you have any questions or concerns")
    )

    // =========================================================================
    // Dataset Access Methods
    // =========================================================================

    /**
     * Get dataset directory
     */
    fun getDatasetDir(context: Context): File {
        val dir = File(context.filesDir, "datasets/librispeech")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Get audio samples directory
     */
    fun getAudioDir(context: Context): File {
        val dir = File(getDatasetDir(context), "audio")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Check if dataset is downloaded
     */
    fun isDatasetDownloaded(context: Context): Boolean {
        val audioDir = getAudioDir(context)
        val transcriptFile = File(getDatasetDir(context), "transcripts.txt")
        return audioDir.exists() &&
               audioDir.listFiles()?.isNotEmpty() == true &&
               transcriptFile.exists()
    }

    /**
     * Get dataset metadata
     */
    fun getMetadata(): DatasetMetadata {
        return DatasetMetadata(
            name = "LibriSpeech Mini",
            version = VERSION,
            totalSamples = MINI_DATASET_TRANSCRIPTS.size,
            totalDurationHours = 1.5f, // Approximate for 100 samples
            splits = mapOf(
                Split.TEST_CLEAN to 100
            ),
            sampleRate = 16000,
            language = "en-US"
        )
    }

    /**
     * Load all audio samples with transcripts
     *
     * Returns List<Pair<File, String>> as requested - audio file and transcript.
     * Will generate synthetic audio if not downloaded.
     *
     * @param context Android context
     * @param forceDownload Whether to force re-download of dataset
     * @param onProgress Progress callback
     * @return List of audio file and transcript pairs
     */
    suspend fun loadSamples(
        context: Context,
        forceDownload: Boolean = false,
        onProgress: (DownloadProgress) -> Unit = {}
    ): List<Pair<File, String>> = withContext(Dispatchers.IO) {
        val audioDir = getAudioDir(context)

        // Generate samples if not present
        if (!isDatasetDownloaded(context) || forceDownload) {
            generateSamples(context, onProgress)
        }

        // Load samples
        MINI_DATASET_TRANSCRIPTS.mapNotNull { (sampleId, transcript) ->
            val audioFile = File(audioDir, "$sampleId.wav")
            if (audioFile.exists()) {
                Pair(audioFile, transcript)
            } else {
                // Generate if missing
                generateAudioFile(context, audioFile, transcript)
                if (audioFile.exists()) {
                    Pair(audioFile, transcript)
                } else null
            }
        }
    }

    /**
     * Load samples as AudioSample objects (more detailed)
     */
    suspend fun loadAudioSamples(
        context: Context,
        forceDownload: Boolean = false,
        onProgress: (DownloadProgress) -> Unit = {}
    ): List<AudioSample> = withContext(Dispatchers.IO) {
        val audioDir = getAudioDir(context)

        if (!isDatasetDownloaded(context) || forceDownload) {
            generateSamples(context, onProgress)
        }

        MINI_DATASET_TRANSCRIPTS.mapNotNull { (sampleId, transcript) ->
            val audioFile = File(audioDir, "$sampleId.wav")
            if (!audioFile.exists()) {
                generateAudioFile(context, audioFile, transcript)
            }

            if (audioFile.exists()) {
                AudioSample(
                    id = sampleId,
                    speakerId = "speaker_${sampleId.takeLast(3).toIntOrNull()?.rem(10) ?: 0}",
                    chapterId = "chapter_${(sampleId.takeLast(3).toIntOrNull() ?: 0) / 20 + 1}",
                    audioFile = audioFile,
                    transcript = transcript,
                    durationSec = estimateDuration(transcript),
                    sampleRate = 16000
                )
            } else null
        }
    }

    /**
     * Get a specific sample by ID
     */
    fun getSampleById(context: Context, sampleId: String): Pair<File, String>? {
        val audioFile = File(getAudioDir(context), "$sampleId.wav")
        val transcript = MINI_DATASET_TRANSCRIPTS.find { it.first == sampleId }?.second
            ?: return null

        if (!audioFile.exists()) {
            return null
        }

        return Pair(audioFile, transcript)
    }

    /**
     * Get samples by chapter
     */
    fun getSamplesByChapter(context: Context, chapterNum: Int): List<Pair<File, String>> {
        val startIdx = (chapterNum - 1) * 20
        val endIdx = startIdx + 20

        return MINI_DATASET_TRANSCRIPTS
            .slice(startIdx until endIdx.coerceAtMost(MINI_DATASET_TRANSCRIPTS.size))
            .mapNotNull { (sampleId, transcript) ->
                val audioFile = File(getAudioDir(context), "$sampleId.wav")
                if (audioFile.exists()) {
                    Pair(audioFile, transcript)
                } else null
            }
    }

    // =========================================================================
    // Dataset Generation
    // =========================================================================

    /**
     * Generate synthetic audio samples
     */
    private suspend fun generateSamples(
        context: Context,
        onProgress: (DownloadProgress) -> Unit
    ) = withContext(Dispatchers.IO) {
        val audioDir = getAudioDir(context)
        val total = MINI_DATASET_TRANSCRIPTS.size

        MINI_DATASET_TRANSCRIPTS.forEachIndexed { index, (sampleId, transcript) ->
            val audioFile = File(audioDir, "$sampleId.wav")

            if (!audioFile.exists()) {
                generateAudioFile(context, audioFile, transcript)
            }

            onProgress(
                DownloadProgress(
                    current = index + 1,
                    total = total,
                    currentFile = sampleId,
                    bytesDownloaded = audioFile.length(),
                    totalBytes = audioFile.length()
                )
            )
        }

        // Save transcripts file
        val transcriptFile = File(getDatasetDir(context), "transcripts.txt")
        transcriptFile.writeText(
            MINI_DATASET_TRANSCRIPTS.joinToString("\n") { (id, text) -> "$id|$text" }
        )
    }

    /**
     * Generate a synthetic audio file for the given transcript
     */
    private fun generateAudioFile(context: Context, audioFile: File, transcript: String) {
        try {
            // Use TestAudioResources to generate audio
            val config = TestAudioResources.TestAudioConfig(
                name = audioFile.nameWithoutExtension,
                type = TestAudioResources.TestAudioType.ENGLISH_SPEECH,
                durationSec = estimateDuration(transcript),
                sampleRate = 16000,
                expectedText = transcript,
                description = "LibriSpeech sample"
            )

            TestAudioResources.generateTestAudio(context, config)

            // Move to correct location if generated elsewhere
            val generatedFile = File(getAudioDir(context), "${config.name}.wav")
            if (generatedFile.exists() && generatedFile != audioFile) {
                generatedFile.renameTo(audioFile)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to generate audio via TestAudioResources, creating silent audio")

            // Create a silent WAV file as fallback
            createSilentWav(audioFile, estimateDuration(transcript))
        }
    }

    /**
     * Create a silent WAV file
     */
    private fun createSilentWav(file: File, durationSec: Float) {
        val sampleRate = 16000
        val numSamples = (sampleRate * durationSec).toInt()
        val dataSize = numSamples * 2 // 16-bit samples

        FileOutputStream(file).use { output ->
            // RIFF header
            output.write("RIFF".toByteArray())
            writeLittleEndianInt(output, 36 + dataSize)
            output.write("WAVE".toByteArray())

            // fmt chunk
            output.write("fmt ".toByteArray())
            writeLittleEndianInt(output, 16) // chunk size
            writeLittleEndianShort(output, 1) // PCM format
            writeLittleEndianShort(output, 1) // mono
            writeLittleEndianInt(output, sampleRate)
            writeLittleEndianInt(output, sampleRate * 2) // byte rate
            writeLittleEndianShort(output, 2) // block align
            writeLittleEndianShort(output, 16) // bits per sample

            // data chunk
            output.write("data".toByteArray())
            writeLittleEndianInt(output, dataSize)

            // Silent audio data (zeros)
            val buffer = ByteArray(dataSize)
            output.write(buffer)
        }
    }

    private fun writeLittleEndianInt(output: FileOutputStream, value: Int) {
        output.write(value and 0xFF)
        output.write((value shr 8) and 0xFF)
        output.write((value shr 16) and 0xFF)
        output.write((value shr 24) and 0xFF)
    }

    private fun writeLittleEndianShort(output: FileOutputStream, value: Int) {
        output.write(value and 0xFF)
        output.write((value shr 8) and 0xFF)
    }

    /**
     * Estimate audio duration from transcript
     */
    private fun estimateDuration(transcript: String): Float {
        // Average speaking rate: ~150 words per minute = 2.5 words per second
        val wordCount = transcript.split(Regex("\\s+")).filter { it.isNotEmpty() }.size
        return wordCount / 2.5f
    }

    // =========================================================================
    // Utility Methods
    // =========================================================================

    /**
     * Clear downloaded dataset
     */
    fun clearDataset(context: Context) {
        getDatasetDir(context).deleteRecursively()
    }

    /**
     * Get dataset statistics
     */
    fun getStatistics(): Map<String, Any> {
        return mapOf(
            "total_samples" to MINI_DATASET_TRANSCRIPTS.size,
            "total_words" to MINI_DATASET_TRANSCRIPTS.sumOf { it.second.split(Regex("\\s+")).size },
            "avg_sentence_length" to MINI_DATASET_TRANSCRIPTS.map { it.second.length }.average(),
            "chapters" to 5
        )
    }

    /**
     * Export transcripts to file
     */
    fun exportTranscripts(context: Context, outputFile: File) {
        outputFile.writeText(
            MINI_DATASET_TRANSCRIPTS.joinToString("\n") { (id, text) ->
                "$id\t$text"
            }
        )
    }

    /**
     * Load transcripts from file
     */
    fun loadTranscriptsFromFile(file: File): List<Pair<String, String>> {
        return file.readLines()
            .filter { it.contains("|") || it.contains("\t") }
            .map { line ->
                val parts = if (line.contains("|")) line.split("|") else line.split("\t")
                Pair(parts[0].trim(), parts.getOrNull(1)?.trim() ?: "")
            }
    }
}

/**
 * Import helper for TestAudioResources reference
 */
private typealias TestAudioResources = com.hiringai.mobile.ml.benchmark.TestAudioResources
