# HRAutomation Android

本地 LLM + Embedding 驱动的智能招聘系统 Android 客户端。

## 功能概览

- **职位管理** — 创建、编辑、归档招聘职位及要求描述
- **候选人管理** — 录入候选人信息、简历关联、PDF 简历解析
- **智能匹配** — 基于 Embedding 向量相似度的职位-候选人自动匹配与排序
- **人才画像** — 本地 LLM 生成候选人画像和职位匹配评估
- **双向匹配中心** — 候选人↔岗位双向匹配 UI，多维度筛选
- **匹配度可视化** — 技能/经验/学历匹配度分解显示
- **基准测试中心** — LLM/语音/图像/加速后端全面性能基准测试
- **本地推理** — 支持 llama.cpp 本地模型或远程 Ollama 服务器两种推理方式，数据不出设备

## 技术架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        Presentation Layer                        │
│  MainActivity + BottomNavigationView                            │
│  ├── JobsFragment / CandidatesFragment                          │
│  ├── MatchingCenterFragment (ViewPager2 + TabLayout)            │
│  │   ├── JobToCandidateFragment  ── 岗位→候选人匹配             │
│  │   └── CandidateToJobFragment  ── 候选人→岗位匹配             │
│  ├── BenchmarkHubFragment (LLM/语音/图像/加速)                  │
│  └── SettingsFragment (推理模式/模型管理/设备兼容)               │
│  ViewModel: JobsVM / CandidatesVM + Repository 模式             │
├─────────────────────────────────────────────────────────────────┤
│                         Data Layer                               │
│  Room Database (4 entities, 4 DAOs, 4 Repositories)             │
│  AppDatabase (singleton, thread-safe, db: hr_automation_db)     │
├─────────────────────────────────────────────────────────────────┤
│                    ML Layer (Submodule)                          │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  hiringai-ml-kit/ml/  (Android Library, 独立仓库)       │    │
│  │  ├── LocalLLMService     → llama.cpp JNI / Ollama       │    │
│  │  ├── LocalEmbeddingService → ONNX Runtime               │    │
│  │  ├── MlBridge            → JobInfo/CandidateInfo 解耦   │    │
│  │  ├── ModelCatalogService → 国内模型源 + 缓存            │    │
│  │  ├── CatalogModel        → 统一模型描述 + 标签推断      │    │
│  │  ├── LLMBenchmarkRunner  → v2 子阶段进度                │    │
│  │  ├── MlLogger            → 分级日志 + 持久化            │    │
│  │  └── SafeNativeLoader    → 安全 native 库加载           │    │
│  └─────────────────────────────────────────────────────────┘    │
├─────────────────────────────────────────────────────────────────┤
│                       Networking                                 │
│  Retrofit 2.11 + OkHttp 4.12 + Gson                             │
└─────────────────────────────────────────────────────────────────┘
```

### 子模块架构

```
hr-automation-android/                # 主仓库
├── app/                              # 主应用 (com.hiringai.mobile)
└── hiringai-ml-kit/                  # Git 子模块 (com.hiringai.mobile.ml)
    ├── ml/                           # Android Library (高内聚低耦合)
    │   ├── bridge/                   # MlBridge — 业务实体解耦
    │   ├── catalog/                  # 模型目录 + 数据模型
    │   ├── benchmark/                # 基准测试工具集
    │   ├── acceleration/             # 硬件加速 (GPU/NNAPI)
    │   ├── logging/                  # MlLogger 日志系统
    │   ├── speech/                   # 语音识别服务
    │   └── ...核心服务
    └── app/                          # 独立测试 APK (standaloneBuild=true)
        └── ui/                       # ViewModel + RecyclerView + BottomSheet
```

### 数据模型

| Entity | 表名 | 关键字段 |
|--------|------|----------|
| JobEntity | jobs | id, title, requirements, status, profile, createdAt |
| CandidateEntity | candidates | id, name, email, phone, resume, profile, createdAt |
| MatchEntity | matches | id, jobId, candidateId, score, skillMatch, experienceMatch, educationMatch, matchReason, status, profile, evaluation, createdAt |
| ApplicationEntity | applications | id, jobId, candidateId, status, coverLetter, appliedAt |

### ML 推理方案

| 方案 | 模型 | 量化 | 大小 | 内存需求 | 说明 |
|------|------|------|------|----------|------|
| 本地 llama.cpp | Qwen2.5-0.5B | Q4_0 | ~400MB | 1 GB | 超轻量级，中文优化 |
| 本地 llama.cpp | Phi-2 | Q4_0 | ~500MB | 1 GB | 微软模型，英文推理优秀 |
| 本地 llama.cpp | SmolLM2-1.7B | Q4_0 | ~1GB | 2 GB | 平衡性能 |
| 本地 llama.cpp | TinyLlama-1.1B | Q4_K_M | ~670MB | 2 GB | 生态丰富 |
| 本地 llama.cpp | Gemma-2B | Q4_K_M | ~1.6GB | 2 GB | Google，指令遵循强 |
| 本地 llama.cpp | StableLM-3B | Q4_K_M | ~1.9GB | 2 GB | 长上下文支持 |
| 本地 ONNX | all-MiniLM-L6-v2 | - | ~90MB | <1 GB | ONNX Runtime + NNAPI |
| 远程 Ollama | 任意 | - | - | 不限 | 电脑/服务器运行 |

## 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 语言 | Kotlin | 2.0.21 |
| 最低 SDK | Android 8.0 (API 26) | — |
| 目标 SDK | Android 15 (API 35) | — |
| 编译 SDK | Android 16 (API 36) | — |
| 构建 | Gradle 8.11.1 + AGP 8.9.1 | — |
| 注解处理 | KSP | 2.0.21-1.0.28 |
| UI | Material Components | 1.12.0 |
| 数据库 | Room | 2.6.1 |
| 网络 | Retrofit + OkHttp | 2.11.0 / 4.12.0 |
| 异步 | Kotlin Coroutines | 1.9.0 |
| 导航 | Navigation Component | 2.8.5 |
| 序列化 | Gson + kotlinx-serialization | 2.10.1 / 1.6.2 |
| PDF 解析 | PDFBox Android | 2.0.27.0 |
| 图表 | MPAndroidChart | v3.1.0 |
| ML 推理 | llama-kotlin-android | 0.1.3 |
| ML 嵌入 | ONNX Runtime Android | 1.24.3 |
| CI/CD | GitHub Actions | — |

## 项目结构

```
hr-automation-android/
├── .github/workflows/
│   ├── ci.yml                    # CI：lint + test + build 三阶段流水线
│   ├── release.yml               # CD：tag 触发自动发布 Release
│   └── code-quality.yml          # 代码质量：Detekt + 依赖审查
├── app/
│   ├── build.gradle              # 模块构建配置（依赖、签名、SDK 版本、Detekt）
│   ├── lint.xml                  # Lint 规则配置
│   ├── proguard-rules.pro        # ProGuard 混淆规则
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/
│       │   │   ├── test_audio/   # 语音基准测试音频
│       │   │   └── test_images/  # 图像基准测试图片
│       │   ├── java/com/hiringai/mobile/
│       │   │   ├── HiringAIApplication.kt    # Application + CrashHandler + SafeNativeLoader
│       │   │   ├── data/
│       │   │   │   ├── local/
│       │   │   │   │   ├── AppDatabase.kt    # Room 数据库（singleton）
│       │   │   │   │   ├── dao/Daos.kt       # JobDao, CandidateDao, MatchDao, ApplicationDao
│       │   │   │   │   └── entity/Entities.kt # 4 个 Entity
│       │   │   │   └── repository/           # 4 个 Repository
│       │   │   ├── ui/
│       │   │   │   ├── MainActivity.kt       # 主 Activity + BottomNavigation
│       │   │   │   ├── jobs/                 # JobsFragment + JobsViewModel + JobAdapter
│       │   │   │   ├── candidates/           # CandidatesFragment + CandidatesViewModel
│       │   │   │   ├── matching/             # MatchingCenter + 双向匹配 + 筛选
│       │   │   │   ├── benchmark/            # 基准测试中心 (LLM/语音/图像/加速)
│       │   │   │   └── settings/             # 设置页面
│       │   │   ├── benchmark/                # 基准测试数据集 + 准确度指标
│       │   │   └── util/PdfExtractor.kt      # PDF 简历解析
│       │   └── res/                          # 布局/导航/字符串/颜色/主题
│       ├── test/                             # 单元测试（JVM）
│       └── androidTest/                      # 仪器测试
├── hiringai-ml-kit/             # Git 子模块 (独立仓库)
│   ├── ml/                      # Android Library — ML 核心库
│   │   └── src/main/java/com/hiringai/mobile/ml/
│   │       ├── bridge/          # MlBridge + JobInfo/CandidateInfo
│   │       ├── catalog/         # CatalogModel + ModelCatalogService
│   │       ├── benchmark/       # LLMBenchmarkRunner + 数据集
│   │       ├── acceleration/    # GPU/NNAPI 加速
│   │       ├── logging/         # MlLogger 日志系统
│   │       ├── speech/          # 语音识别服务
│   │       ├── LocalLLMService.kt
│   │       ├── LocalEmbeddingService.kt
│   │       ├── LocalImageModelService.kt
│   │       ├── ModelManager.kt
│   │       ├── DeviceCapabilityDetector.kt
│   │       └── SafeNativeLoader.kt
│   └── app/                     # 独立测试 APK (standaloneBuild=true)
│       └── src/main/java/com/hiringai/mobile/ml/testapp/ui/
│           ├── MainActivity.kt
│           ├── BenchmarkActivity.kt + BenchmarkViewModel.kt
│           ├── ModelCatalogActivity.kt
│           ├── ModelItemAdapter.kt
│           ├── ModelDetailBottomSheet.kt
│           └── LogViewerActivity.kt
├── docs/
│   ├── ARCHITECTURE.md          # 详细架构文档
│   └── ML_ON_ANDROID.md         # ML 模型技术文档
├── build.gradle                 # 根级构建（插件声明）
├── settings.gradle              # include ':app' + ':hiringai-ml-kit:ml'
├── detekt.yml                   # Detekt 静态分析配置
├── README.md                    # 本文件
├── CLAUDE.md                    # Claude Code 开发指引
├── PROJECT_DOC.md               # 项目文档
├── RELEASE.md                   # CI/CD 发布说明
├── CHANGELOG.md                 # 变更日志
└── CONTRIBUTING.md              # 贡献指南
```

## 构建与运行

### 环境要求

- Android Studio Hedgehog (2023.1.1) 或更新
- JDK 17
- Android SDK：compileSdk 36, minSdk 26
- Gradle 8.11.1（项目自带 wrapper）

### 初始化子模块

```bash
# 克隆后初始化子模块
git submodule update --init --recursive
```

### 本地构建

```bash
# Debug APK
./gradlew assembleDebug

# Release APK（使用 debug 签名）
./gradlew assembleRelease

# 运行测试
./gradlew test

# Lint 检查
./gradlew lint
```

### 安装到设备

```bash
# 通过 ADB
adb install app/build/outputs/apk/debug/app-debug.apk

# 或直接从 Android Studio 运行
```

## ML 模型配置

### 方案一：设备端推理（推荐，隐私优先）

应用内直接运行 GGUF 模型，数据不出设备：

1. 打开 App 设置页面
2. 选择推理模式：设备端推理 (llama.cpp)
3. 选择 LLM 模型并下载（Qwen2.5-0.5B 约 400MB）
4. 可选：下载 Embedding 模型（all-MiniLM-L6-v2 约 90MB）
5. 点击"加载已下载的模型"
6. 模型文件存储路径：`app/files/models/` (LLM)、`app/files/embedding/` (Embedding)

LLM 推理使用 [llama-kotlin-android](https://github.com/CodeShipping/llama-kotlin-android) (llama.cpp JNI)，Embedding 使用 [ONNX Runtime Android](https://onnxruntime.ai/docs/mobile/) + NNAPI 硬件加速。

### 方案二：远程 Ollama 服务器

## 权限说明

| 权限 | 用途 |
|------|------|
| INTERNET | 访问远程 Ollama 服务器、下载模型 |
| ACCESS_NETWORK_STATE | 检测网络可用性 |
| RECORD_AUDIO | 语音识别和基准测试 |
| READ_MEDIA_IMAGES | 图像基准测试 |
| CAMERA | 拍照进行图像识别 |

## License

Private — Internal Use Only
