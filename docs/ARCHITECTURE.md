# HRAutomation Android - 项目架构文档

## 1. 系统架构概览

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Presentation Layer                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                        MainActivity (Container)                       │   │
│  │  ┌─────────────┬─────────────┬─────────────┬─────────────┐          │   │
│  │  │ JobsFragment│CandidatesFrg│ MatchesFrg  │SettingsFrg  │          │   │
│  │  │  (职位管理)  │ (候选人管理) │ (智能匹配)  │  (设置)      │          │   │
│  │  └─────────────┴─────────────┴─────────────┴─────────────┘          │   │
│  │                           BottomNavigationView                        │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                               Business Layer                                 │
│  ┌─────────────────────────────┐  ┌─────────────────────────────────────┐  │
│  │      ML Service Layer       │  │          Data Service Layer         │  │
│  │  ┌───────────────────────┐  │  │  ┌─────────────────────────────┐   │  │
│  │  │   LocalLLMService     │  │  │  │    Match Algorithm          │   │  │
│  │  │ (llama.cpp / Ollama)  │  │  │  │  cosineSimilarity()         │   │  │
│  │  └───────────────────────┘  │  │  └─────────────────────────────┘   │  │
│  │  ┌───────────────────────┐  │  └─────────────────────────────────────┘  │
│  │  │ LocalEmbeddingService │  │                                            │
│  │  │   (ONNX Runtime)      │  │                                            │
│  │  └───────────────────────┘  │                                            │
│  └─────────────────────────────┘                                            │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                                Data Layer                                    │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                         Room Database                                 │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                   │   │
│  │  │  JobEntity  │  │CandidateEntity│ │ MatchEntity │                   │   │
│  │  ├─────────────┤  ├─────────────┤  ├─────────────┤                   │   │
│  │  │ JobDao      │  │CandidateDao │  │ MatchDao    │                   │   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘                   │   │
│  │                         AppDatabase                                   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                             Native Layer (C/C++)                             │
│  ┌─────────────────────────────┐  ┌─────────────────────────────────────┐  │
│  │    llama.cpp (llama-android)│  │      ONNX Runtime Android           │  │
│  │    - GGUF 模型加载           │  │      - ONNX 模型推理                │  │
│  │    - CPU/GPU 推理            │  │      - CPU-only (NNAPI disabled)    │  │
│  │    - 流式生成                │  │      - 384维向量输出                │  │
│  └─────────────────────────────┘  └─────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 2. 技术栈

### 2.1 核心技术

| 类别 | 技术 | 版本 | 用途 |
|------|------|------|------|
| 语言 | Kotlin | 1.9.22 | 主要开发语言 |
| 最低 SDK | Android 8.0 | API 26 | 兼容性基线 |
| 目标 SDK | Android 15 | API 35 | 最新特性支持 |
| 构建工具 | Gradle | 8.11.1 | 构建系统 |
| AGP | Android Gradle Plugin | 8.9.1 | Android 构建支持 |

### 2.2 UI 层

| 技术 | 版本 | 用途 |
|------|------|------|
| Material Components | 1.12.0 | Material Design UI 组件 |
| ViewBinding | - | 视图绑定，替代 findViewById |
| Navigation Component | 2.8.5 | Fragment 导航管理 |
| RecyclerView | 1.3.2 | 列表展示 |
| SwipeRefreshLayout | 1.1.0 | 下拉刷新 |

### 2.3 数据层

| 技术 | 版本 | 用途 |
|------|------|------|
| Room | 2.6.1 | SQLite ORM，本地数据库 |
| Retrofit | 2.11.0 | HTTP 客户端，Ollama API 调用 |
| OkHttp | 4.12.0 | 网络请求，模型下载 |
| Gson | 2.10.1 | JSON 序列化 |

### 2.4 ML 层

| 技术 | 版本 | 用途 |
|------|------|------|
| llama-kotlin-android | 0.1.3 | llama.cpp Kotlin 绑定，本地 LLM |
| ONNX Runtime Android | 1.24.3 | Embedding 模型推理 |

### 2.5 异步处理

| 技术 | 版本 | 用途 |
|------|------|------|
| Kotlin Coroutines | 1.9.0 | 异步任务，IO 操作 |
| Lifecycle Scope | - | Fragment 生命周期绑定 |

## 3. 模块详解

### 3.1 数据模型 (Entity)

```
┌─────────────────┐       ┌─────────────────┐
│    JobEntity    │       │ CandidateEntity │
├─────────────────┤       ├─────────────────┤
│ id: Long        │       │ id: Long        │
│ title: String   │       │ name: String    │
│ requirements    │       │ email: String   │
│ status: String  │       │ phone: String   │
│ createdAt: Long │       │ resume: String  │
└────────┬────────┘       │ createdAt: Long │
         │                └────────┬────────┘
         │                         │
         │    ┌────────────────────┼────────────────────┐
         │    │                    │                    │
         ▼    ▼                    ▼                    │
┌─────────────────────────────────────────────────────┐
│                    MatchEntity                       │
├─────────────────────────────────────────────────────┤
│ id: Long                                            │
│ jobId: Long ───────────────────► JobEntity          │
│ candidateId: Long ─────────────► CandidateEntity    │
│ score: Float          (相似度得分 0.0-1.0)           │
│ status: String        (pending/accepted/rejected)   │
│ profile: String       (AI 生成的候选人画像)          │
│ evaluation: String    (AI 生成的匹配评估)            │
│ createdAt: Long                                     │
└─────────────────────────────────────────────────────┘
```

### 3.2 UI 导航流程

```
┌─────────────────────────────────────────────────────────────┐
│                      MainActivity                            │
│  ┌───────────────────────────────────────────────────────┐  │
│  │                 Fragment Container                     │  │
│  │  ┌─────────────────────────────────────────────────┐  │  │
│  │  │                                                  │  │  │
│  │  │     [JobsFragment] [CandidatesFragment]         │  │  │
│  │  │     [MatchesFragment] [SettingsFragment]        │  │  │
│  │  │                                                  │  │  │
│  │  └─────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │              BottomNavigationView                     │  │
│  │   [职位]      [候选人]      [匹配]      [设置]        │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### 3.3 ML 推理流程

```
                    用户输入
                        │
                        ▼
┌───────────────────────────────────────────────────────────┐
│                    智能匹配流程                             │
│                                                            │
│  ┌─────────────────────────────────────────────────────┐  │
│  │  1. 文本预处理                                       │  │
│  │     候选人简历 → 纯文本                              │  │
│  │     职位要求 → 纯文本                                │  │
│  └─────────────────────────────────────────────────────┘  │
│                        │                                   │
│                        ▼                                   │
│  ┌─────────────────────────────────────────────────────┐  │
│  │  2. Embedding 向量化 (ONNX Runtime)                  │  │
│  │     text → BERT tokenizer → ONNX inference          │  │
│  │     → 384维向量 → L2归一化                           │  │
│  └─────────────────────────────────────────────────────┘  │
│                        │                                   │
│                        ▼                                   │
│  ┌─────────────────────────────────────────────────────┐  │
│  │  3. 相似度计算                                       │  │
│  │     cosineSimilarity(vec_job, vec_candidate)        │  │
│  │     → score (0.0 - 1.0)                             │  │
│  └─────────────────────────────────────────────────────┘  │
│                        │                                   │
│                        ▼                                   │
│  ┌─────────────────────────────────────────────────────┐  │
│  │  4. LLM 生成评估 (llama.cpp / Ollama)               │  │
│  │     prompt = 职位要求 + 候选人简历                   │  │
│  │     → 生成候选人画像 + 匹配评估                      │  │
│  └─────────────────────────────────────────────────────┘  │
│                        │                                   │
│                        ▼                                   │
│  ┌─────────────────────────────────────────────────────┐  │
│  │  5. 结果存储                                         │  │
│  │     MatchEntity(score, profile, evaluation)         │  │
│  │     → Room Database                                  │  │
│  └─────────────────────────────────────────────────────┘  │
│                                                            │
└───────────────────────────────────────────────────────────┘
```

## 4. 目录结构

```
hr-automation-android/
├── app/
│   ├── build.gradle                    # 模块构建配置
│   ├── proguard-rules.pro              # 混淆规则
│   └── src/main/
│       ├── AndroidManifest.xml         # 清单文件
│       ├── java/com/hiringai/mobile/
│       │   ├── HiringAIApplication.kt  # Application 类，Crash 处理
│       │   ├── SafeNativeLoader.kt     # Native 库安全加载器
│       │   ├── data/
│       │   │   └── local/
│       │   │       ├── AppDatabase.kt  # Room 数据库单例
│       │   │       ├── dao/
│       │   │       │   └── Daos.kt     # JobDao, CandidateDao, MatchDao
│       │   │       └── entity/
│       │   │           └── Entities.kt # JobEntity, CandidateEntity, MatchEntity
│       │   ├── ml/
│       │   │   ├── LocalLLMService.kt      # LLM 推理服务
│       │   │   └── LocalEmbeddingService.kt # Embedding 推理服务
│       │   └── ui/
│       │       ├── MainActivity.kt        # 主 Activity
│       │       ├── jobs/
│       │       │   └── JobsFragment.kt    # 职位管理
│       │       ├── candidates/
│       │       │   └── CandidatesFragment.kt # 候选人管理
│       │       ├── matches/
│       │       │   └── MatchesFragment.kt    # 智能匹配
│       │       └── settings/
│       │           └── SettingsFragment.kt   # 设置页面
│       └── res/
│           ├── layout/                  # XML 布局
│           ├── navigation/              # 导航图
│           ├── values/                  # 字符串、颜色、主题
│           └── xml/                     # 网络安全配置
├── gradle/wrapper/                      # Gradle Wrapper
├── build.gradle                         # 根级构建配置
├── settings.gradle                      # 项目设置
├── ARCHITECTURE.md                      # 本文档
├── ML_ON_ANDROID.md                     # ML 模型应用文档
├── README.md                            # 项目说明
└── RELEASE.md                           # 发布说明
```

## 5. 关键设计决策

### 5.1 为什么选择 llama.cpp？

| 对比项 | llama.cpp | TensorFlow Lite | MediaPipe |
|--------|-----------|-----------------|-----------|
| 模型格式 | GGUF (广泛支持) | TFLite (需转换) | 特定格式 |
| 内存效率 | 极高 (量化) | 一般 | 一般 |
| Kotlin 绑定 | 有 (llama-kotlin-android) | 官方支持 | 官方支持 |
| 社区支持 | 活跃 | Google 主导 | Google 主导 |
| 模型生态 | HuggingFace GGUF 丰富 | 需自行转换 | 有限 |

### 5.2 为什么禁用 NNAPI？

小米/高通设备的 NNAPI 驱动存在已知问题：
- `qti-default`, `qti-dsp`, `qti-gpu` 驱动在 ONNX Runtime 初始化时会触发 SIGILL
- Java 层 try-catch 无法捕获 native crash
- 解决方案：使用 CPU-only 执行，牺牲部分性能换取稳定性

### 5.3 为什么使用延迟加载 Native 库？

1. **启动稳定性**：避免在 Application.onCreate() 中加载导致闪退时用户看不到 UI
2. **优雅降级**：native 库不可用时，ML 功能禁用但应用仍可用
3. **崩溃标记**：记录崩溃状态，下次启动自动跳过问题库

## 6. 性能指标

### 6.1 模型内存占用

| 模型 | 参数量 | 量化 | 内存占用 | 推理速度 |
|------|--------|------|----------|----------|
| Qwen2.5-0.5B-Instruct-Q4_0 | 0.5B | Q4_0 | ~400MB | ~20 tokens/s |
| TinyLlama-1.1B-Chat-Q4_K_M | 1.1B | Q4_K_M | ~670MB | ~10 tokens/s |
| all-MiniLM-L6-v2 | 22M | ONNX | ~90MB | ~50ms/text |

### 6.2 APK 大小

| ABI | 大小 | 说明 |
|-----|------|------|
| arm64-v8a | ~36MB | 推荐用于真机 |
| x86_64 | ~41MB | 用于模拟器 |
| universal | ~71MB | 包含所有架构 |

## 7. 扩展指南

### 7.1 添加新的 LLM 模型

在 `LocalLLMService.kt` 的 `AVAILABLE_MODELS` 中添加：

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

### 7.2 添加新的 Embedding 模型

在 `LocalEmbeddingService.kt` 的 `AVAILABLE_MODELS` 中添加：

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

### 7.3 启用 GPU 加速

修改 `LocalLLMService.kt` 中的 `loadModel()` 调用：

```kotlin
llmService.loadModel(config, threads = 4, gpuLayers = 35)  // gpuLayers > 0 启用 GPU
```

注意：GPU 加速需要设备支持 Vulkan 或 OpenCL。
