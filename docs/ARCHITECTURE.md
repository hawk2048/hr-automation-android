# HRAutomation Android - 项目架构文档

## 1. 系统架构概览

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              主仓库 (hr-automation-android)                  │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                        Presentation Layer                            │    │
│  │  ┌───────────────────────────────────────────────────────────────┐  │    │
│  │  │                    MainActivity (Container)                    │  │    │
│  │  │  ┌──────────┬──────────┬──────────┬──────────┬──────────┐   │  │    │
│  │  │  │  Jobs    │ Candid-  │ Matching │Benchmark │Settings  │   │  │    │
│  │  │  │Fragment  │atesFrg   │ Center   │  Hub     │Fragment  │   │  │    │
│  │  │  │+VM+Repo  │+VM+Repo  │(双向)    │(4子页面) │(模型管理)│   │  │    │
│  │  │  └──────────┴──────────┴──────────┴──────────┴──────────┘   │  │    │
│  │  │                       BottomNavigationView                    │  │    │
│  │  └───────────────────────────────────────────────────────────────┘  │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                      │                                      │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                           Data Layer                                 │    │
│  │  ┌─────────────────────────────────────────────────────────────┐   │    │
│  │  │  Room Database (4 entities, 4 DAOs, 4 Repositories)        │   │    │
│  │  │  JobEntity | CandidateEntity | MatchEntity | ApplicationEntity│  │    │
│  │  │                  AppDatabase (singleton)                      │   │    │
│  │  └─────────────────────────────────────────────────────────────┘   │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                      │                                      │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                    Benchmark & Accuracy Layer                        │    │
│  │  datasets/ (LibriSpeech, MiniImageNet)                              │    │
│  │  accuracy/metrics/ (BLEU, WER, Top-K Accuracy)                      │    │
│  │  GroundTruthMatcher                                                 │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                      │                                      │
│                   implementation project(':hiringai-ml-kit:ml')             │
│                                      │                                      │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │              ML 子模块 (hiringai-ml-kit/ml/)                         │    │
│  │                                                                     │    │
│  │  ┌───────────────┐ ┌────────────────┐ ┌────────────────────────┐  │    │
│  │  │ LocalLLM      │ │ LocalEmbedding │ │ LocalImage/Speech      │  │    │
│  │  │ (llama.cpp /  │ │ (ONNX Runtime) │ │ (ONNX Runtime)         │  │    │
│  │  │  Ollama)      │ │  cosineSim()   │ │  ASR / OCR / CLIP      │  │    │
│  │  └───────┬───────┘ └───────┬────────┘ └───────────┬────────────┘  │    │
│  │          │                 │                       │                │    │
│  │  ┌───────┴─────────────────┴───────────────────────┴───────────┐  │    │
│  │  │                    MlBridge (解耦层)                         │  │    │
│  │  │  JobInfo(title, requirements) ← 映射自 JobEntity           │  │    │
│  │  │  CandidateInfo(name, email, phone, resume) ← 映射自        │  │    │
│  │  │                          CandidateEntity                    │  │    │
│  │  └────────────────────────────────────────────────────────────┘  │    │
│  │  ┌──────────────┐ ┌──────────────────┐ ┌────────────────────┐   │    │
│  │  │ ModelCatalog │ │ LLMBenchmark     │ │ MlLogger           │   │    │
│  │  │ (国内源+缓存)│ │ (v2 子阶段进度)  │ │ (分级+持久化+流)   │   │    │
│  │  └──────────────┘ └──────────────────┘ └────────────────────┘   │    │
│  │  ┌──────────────┐ ┌──────────────────┐                           │    │
│  │  │ SafeNative   │ │ Acceleration     │                           │    │
│  │  │ Loader       │ │ (GPU/NNAPI)      │                           │    │
│  │  └──────────────┘ └──────────────────┘                           │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 2. 技术栈

### 2.1 核心技术

| 类别 | 技术 | 版本 | 用途 |
|------|------|------|------|
| 语言 | Kotlin | 2.0.21 | 主要开发语言 |
| 最低 SDK | Android 8.0 | API 26 | 兼容性基线 |
| 目标 SDK | Android 15 | API 35 | 最新特性支持 |
| 编译 SDK | Android 16 | API 36 | 最新 API 访问 |
| 构建工具 | Gradle | 8.11.1 | 构建系统 |
| AGP | Android Gradle Plugin | 8.9.1 | Android 构建支持 |
| 注解处理 | KSP | 2.0.21-1.0.28 | Room 编译器 |

### 2.2 UI 层

| 技术 | 版本 | 用途 |
|------|------|------|
| Material Components | 1.12.0 | Material Design UI 组件 |
| ViewBinding | — | 视图绑定，替代 findViewById |
| Navigation Component | 2.8.5 | Fragment 导航管理 |
| RecyclerView | 1.3.2 | 列表展示 |
| MPAndroidChart | v3.1.0 | 匹配度雷达图 |
| Lifecycle ViewModel | 2.8.7 | MVVM 状态管理 |
| Lifecycle LiveData | 2.8.7 | 响应式数据观察 |

### 2.3 数据层

| 技术 | 版本 | 用途 |
|------|------|------|
| Room | 2.6.1 | SQLite ORM，本地数据库 |
| Retrofit | 2.11.0 | HTTP 客户端，Ollama API 调用 |
| OkHttp | 4.12.0 | 网络请求，模型下载 |
| Gson | 2.10.1 | JSON 序列化 |
| PDFBox Android | 2.0.27.0 | PDF 简历解析 |

### 2.4 ML 层 (hiringai-ml-kit)

| 技术 | 版本 | 用途 |
|------|------|------|
| llama-kotlin-android | 0.1.3 | llama.cpp Kotlin 绑定，本地 LLM |
| ONNX Runtime Android | 1.24.3 | Embedding/Image/Speech 推理 |
| kotlinx-serialization-json | 1.6.2 | 加速配置序列化 |

### 2.5 异步处理

| 技术 | 版本 | 用途 |
|------|------|------|
| Kotlin Coroutines | 1.9.0 | 异步任务，IO 操作 |
| Lifecycle Scope | — | Fragment 生命周期绑定 |

## 3. 模块详解

### 3.1 主仓库与子模块的关系

```
hr-automation-android/                    # 主仓库 (feature/mvvm-refactor)
├── app/                                  # 主应用 (com.hiringai.mobile)
│   ├── build.gradle                      # implementation project(':hiringai-ml-kit:ml')
│   └── src/main/java/com/hiringai/mobile/
│       ├── HiringAIApplication.kt        # Application + CrashHandler + 主仓库版 SafeNativeLoader
│       ├── data/                         # Room DB + DAOs + Repositories
│       ├── ui/                           # 5 个功能模块的 Fragment + ViewModel
│       ├── benchmark/                    # 基准测试数据集 + 准确度指标
│       └── util/                         # PDF 解析等工具
├── hiringai-ml-kit/                      # Git 子模块 (独立仓库, main 分支)
│   ├── ml/                               # Android Library (com.hiringai.mobile.ml)
│   │   └── src/main/java/com/hiringai/mobile/ml/
│   │       ├── bridge/                   # MlBridge + JobInfo/CandidateInfo
│   │       ├── catalog/                  # CatalogModel + ModelCatalogService
│   │       ├── benchmark/                # LLMBenchmarkRunner + 数据集
│   │       ├── acceleration/             # GPU/NNAPI 加速
│   │       ├── logging/                  # MlLogger 日志系统
│   │       ├── speech/                   # 语音识别服务
│   │       ├── LocalLLMService.kt
│   │       ├── LocalEmbeddingService.kt
│   │       ├── LocalImageModelService.kt
│   │       ├── ModelManager.kt
│   │       ├── DeviceCapabilityDetector.kt
│   │       └── SafeNativeLoader.kt       # 子模块版 (用 Context 参数)
│   └── app/                              # 独立测试 APK (standaloneBuild=true)
│       └── src/main/java/com/hiringai/mobile/ml/testapp/ui/
│           ├── MainActivity.kt
│           ├── BenchmarkActivity.kt + BenchmarkViewModel.kt
│           ├── ModelCatalogActivity.kt + ModelItemAdapter.kt
│           ├── ModelDetailBottomSheet.kt
│           └── LogViewerActivity.kt
└── settings.gradle                       # include ':app', ':hiringai-ml-kit:ml'
```

### 3.2 Bridge 模式 — ML 与业务解耦

ML 子模块不直接依赖主仓库的 Entity 类，通过 Bridge 层实现解耦：

```kotlin
// hiringai-ml-kit/ml/bridge/MlBridge.kt
data class JobInfo(val title: String, val requirements: String)
data class CandidateInfo(val name: String, email: String, phone: String, resume: String)

// 主仓库 ViewModel 调用示例
val jobInfo = JobInfo(title = job.title, requirements = job.requirements)
llmService.generateJobProfile(jobInfo)
```

**原则**：ML 库只接受基本类型和 Bridge 数据类，不感知 Room Entity。

### 3.3 SafeNativeLoader 双版本共存

| 版本 | 包名 | 依赖 | 用途 |
|------|------|------|------|
| 主仓库版 | `com.hiringai.mobile.SafeNativeLoader` | `HiringAIApplication.instance` | 主应用启动检测 |
| 子模块版 | `com.hiringai.mobile.ml.SafeNativeLoader` | `Context` 参数 | ML 库独立运行 |

两者共享同一 crash marker 文件路径，不冲突。

### 3.4 数据模型关系

```
┌─────────────────┐       ┌─────────────────┐
│    JobEntity    │       │ CandidateEntity │
├─────────────────┤       ├─────────────────┤
│ id: Long        │       │ id: Long        │
│ title: String   │       │ name: String    │
│ requirements    │       │ email: String   │
│ status: String  │       │ phone: String   │
│ profile: String │       │ resume: String  │
│ createdAt: Long │       │ profile: String │
└────────┬────────┘       │ createdAt: Long │
         │                └────────┬────────┘
         │                         │
         ▼                         ▼
┌─────────────────────────────────────────────────────┐
│                    MatchEntity                       │
├─────────────────────────────────────────────────────┤
│ id: Long                                            │
│ jobId: Long ────────────────► JobEntity             │
│ candidateId: Long ──────────► CandidateEntity       │
│ score: Float          (综合匹配度 0.0-1.0)           │
│ skillMatch: Float     (技能匹配度)                   │
│ experienceMatch: Float (经验匹配度)                  │
│ educationMatch: Float  (学历匹配度)                  │
│ matchReason: String   (推荐理由)                     │
│ status: String        (pending/screened/accepted)   │
│ profile: String       (AI 画像)                      │
│ evaluation: String    (AI 评估)                      │
│ createdAt: Long                                     │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│                  ApplicationEntity                   │
├─────────────────────────────────────────────────────┤
│ id: Long                                            │
│ jobId: Long                                         │
│ candidateId: Long                                   │
│ coverLetter: String                                 │
│ status: String     (pending/accepted/rejected)      │
│ appliedAt: Long                                     │
└─────────────────────────────────────────────────────┘
```

### 3.5 UI 导航流程

```
┌─────────────────────────────────────────────────────────────┐
│                      MainActivity                            │
│  ┌───────────────────────────────────────────────────────┐  │
│  │                 Fragment Container (NavController)      │  │
│  │                                                        │  │
│  │  [Jobs] [Candidates] [Match Center] [Bench] [Settings] │  │
│  │                                                        │  │
│  │  Match Center (ViewPager2):                             │  │
│  │  ├── JobToCandidateFragment   岗位→候选人               │  │
│  │  └── CandidateToJobFragment  候选人→岗位               │  │
│  │                                                        │  │
│  │  Benchmark Hub:                                         │  │
│  │  ├── LLMBenchmarkFragment     LLM 性能测试             │  │
│  │  ├── BenchmarkSpeechFragment  语音识别测试             │  │
│  │  ├── BenchmarkImageFragment   图像模型测试             │  │
│  │  └── BenchmarkDashboardFragment 加速后端测试           │  │
│  └───────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │              BottomNavigationView (5 tabs)              │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### 3.6 ML 推理流程

```
                    用户操作 (匹配/画像)
                        │
                        ▼
┌───────────────────────────────────────────────────────────┐
│                    ViewModel 层                             │
│  JobsViewModel / CandidatesViewModel                       │
│  调用 MlBridge 转换: JobEntity → JobInfo                   │
│                    CandidateEntity → CandidateInfo          │
└────────────────────────┬──────────────────────────────────┘
                         │
                         ▼
┌───────────────────────────────────────────────────────────┐
│                    ML Service 层                            │
│                                                            │
│  ┌─────────────────────────────────────────────────────┐  │
│  │  1. 文本预处理                                       │  │
│  │     候选人简历 → 纯文本                              │  │
│  │     职位要求 → 纯文本                                │  │
│  └─────────────────────────────────────────────────────┘  │
│                        │                                   │
│  ┌─────────────────────────────────────────────────────┐  │
│  │  2. Embedding 向量化 (ONNX Runtime)                  │  │
│  │     text → BERT tokenizer → ONNX inference          │  │
│  │     → 384维向量 → L2归一化                           │  │
│  └─────────────────────────────────────────────────────┘  │
│                        │                                   │
│  ┌─────────────────────────────────────────────────────┐  │
│  │  3. 相似度计算                                       │  │
│  │     cosineSimilarity(vec_job, vec_candidate)        │  │
│  │     → score (0.0 - 1.0)                             │  │
│  └─────────────────────────────────────────────────────┘  │
│                        │                                   │
│  ┌─────────────────────────────────────────────────────┐  │
│  │  4. LLM 生成评估 (llama.cpp / Ollama)               │  │
│  │     prompt = 职位要求 + 候选人简历                   │  │
│  │     → 生成候选人画像 + 匹配评估                      │  │
│  └─────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────┘
```

## 4. 关键设计决策

### 4.1 为什么将 ML 剥离为独立子模块？

| 对比项 | 剥离前 | 剥离后 |
|--------|--------|--------|
| 编译时间 | 全量编译 | ml/ 可独立编译 |
| 复用性 | 不可复用 | 其他项目直接引入 |
| 职责边界 | ML 与业务耦合 | Bridge 层清晰解耦 |
| 独立测试 | 需要完整主应用 | 独立 APK 测试 |
| CI/CD | 与主应用绑定 | 独立 CI 流水线 |

### 4.2 为什么选择 llama.cpp？

| 对比项 | llama.cpp | TensorFlow Lite | MediaPipe |
|--------|-----------|-----------------|-----------|
| 模型格式 | GGUF (广泛支持) | TFLite (需转换) | 特定格式 |
| 内存效率 | 极高 (量化) | 一般 | 一般 |
| Kotlin 绑定 | 有 (llama-kotlin-android) | 官方支持 | 官方支持 |
| 社区支持 | 活跃 | Google 主导 | Google 主导 |
| 模型生态 | HuggingFace GGUF 丰富 | 需自行转换 | 有限 |

### 4.3 为什么禁用 NNAPI？

小米/高通设备的 NNAPI 驱动存在已知问题：
- `qti-default`, `qti-dsp`, `qti-gpu` 驱动在 ONNX Runtime 初始化时会触发 SIGILL
- Java 层 try-catch 无法捕获 native crash
- 解决方案：使用 CPU-only 执行，牺牲部分性能换取稳定性

### 4.4 为什么使用延迟加载 Native 库？

1. **启动稳定性**：避免在 Application.onCreate() 中加载导致闪退时用户看不到 UI
2. **优雅降级**：native 库不可用时，ML 功能禁用但应用仍可用
3. **崩溃标记**：记录崩溃状态，下次启动自动跳过问题库

## 5. 性能指标

### 5.1 模型内存占用

| 模型 | 参数量 | 量化 | 内存占用 | 推理速度 |
|------|--------|------|----------|----------|
| Qwen2.5-0.5B-Instruct-Q4_0 | 0.5B | Q4_0 | ~400MB | ~20 tokens/s |
| TinyLlama-1.1B-Chat-Q4_K_M | 1.1B | Q4_K_M | ~670MB | ~10 tokens/s |
| all-MiniLM-L6-v2 | 22M | ONNX | ~90MB | ~50ms/text |

### 5.2 APK 大小

| ABI | 大小 | 说明 |
|-----|------|------|
| arm64-v8a | ~36MB | 推荐用于真机 |
| x86_64 | ~41MB | 用于模拟器 |
| universal | ~71MB | 包含所有架构 |

## 6. 扩展指南

### 6.1 添加新的 LLM 模型

在 `hiringai-ml-kit/ml/LocalLLMService.kt` 的 `AVAILABLE_MODELS` 中添加：

```kotlin
ModelConfig(
    name = "YourModel-Q4_0",
    url = "$HF_MIRROR/org/model/resolve/main/model-q4_0.gguf",
    size = 123_456_789,
    requiredRAM = 4,
    contextSize = 4096,
    template = "chatml"  // 或 "llama", "alpaca" 等
)
```

### 6.2 添加新的 Embedding 模型

在 `hiringai-ml-kit/ml/LocalEmbeddingService.kt` 的 `AVAILABLE_MODELS` 中添加：

```kotlin
EmbeddingModelConfig(
    name = "your-embedding-model",
    modelUrl = "$HF_MIRROR/org/model/resolve/main/onnx/model.onnx",
    vocabUrl = "$HF_MIRROR/org/model/resolve/main/vocab.txt",
    modelSize = 100_000_000,
    dimension = 768,
    maxSeqLength = 512
)
```

### 6.3 启用 GPU 加速

修改 `LocalLLMService.kt` 中的 `loadModel()` 调用：

```kotlin
llmService.loadModel(config, threads = 4, gpuLayers = 35)  // gpuLayers > 0 启用 GPU
```

注意：GPU 加速需要设备支持 Vulkan 或 OpenCL。

### 6.4 构建独立测试 APK

```bash
cd hiringai-ml-kit
./gradlew assembleDebug -PstandaloneBuild=true
```

独立 APK 包含：设备信息、基准测试（MVVM + RecyclerView + BottomSheet）、模型目录、日志查看。
