# HRAutomation Android

本地 LLM + Embedding 驱动的智能招聘系统 Android 客户端。

## 功能概览

- **职位管理** — 创建、编辑、归档招聘职位及要求描述
- **候选人管理** — 录入候选人信息、简历关联
- **智能匹配** — 基于 Embedding 向量相似度的职位-候选人自动匹配与排序
- **人才画像** — 本地 LLM 生成候选人画像和职位匹配评估
- **本地推理** — 支持 llama.cpp 本地模型或远程 Ollama 服务器两种推理方式，数据不出设备

## 技术架构

```
┌─────────────────────────────────────────────┐
│                   UI Layer                   │
│  MainActivity + 4 Fragments (ViewBinding)    │
│  Jobs / Candidates / Matches / Settings      │
├─────────────────────────────────────────────┤
│                Data Layer                    │
│  Room Database (3 entities, 3 DAOs)          │
│  AppDatabase (singleton, thread-safe)        │
├─────────────────────────────────────────────┤
│                 ML Layer                     │
│  LocalLLMService    → llama.cpp JNI（已实现）  │
│                     → 远程 Ollama（已实现）    │
│  LocalEmbeddingService → ONNX Runtime（已实现）│
│  cosineSimilarity   → 向量匹配计算           │
├─────────────────────────────────────────────┤
│              Networking                      │
│  Retrofit 2.11 + OkHttp 4.12 + Gson         │
└─────────────────────────────────────────────┘
```

### 数据模型

| Entity | 表名 | 关键字段 |
|--------|------|----------|
| JobEntity | jobs | id, title, requirements, status, createdAt |
| CandidateEntity | candidates | id, name, email, phone, resume, createdAt |
| MatchEntity | matches | id, jobId, candidateId, score, status, profile, evaluation |

### ML 推理方案

| 方案 | 模型 | 内存需求 | 状态 | 说明 |
|------|------|----------|------|------|
| 本地 llama.cpp | Qwen2.5-0.5B-Q4_0 | 2 GB | 已实现 | llama-kotlin-android JNI，设备端推理 |
| 本地 llama.cpp | TinyLlama-1.1B-Q4_K_M | 3 GB | 已实现 | llama-kotlin-android JNI，更强推理能力 |
| 本地 ONNX | all-MiniLM-L6-v2 | <1 GB | 已实现 | ONNX Runtime + NNAPI 加速，384 维向量 |
| 远程 Ollama | 任意 | 不限 | 已实现 | 电脑/服务器运行 Ollama，手机通过 HTTP 调用 |

## 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 语言 | Kotlin | 1.9.22 |
| 最低 SDK | Android 8.0 (API 26) | — |
| 目标 SDK | Android 15 (API 35) | — |
| 构建 | Gradle 8.4 + AGP 8.2.2 | — |
| 注解处理 | KSP | 1.9.22-1.0.17 |
| UI | Material Components | 1.11.0 |
| 数据库 | Room | 2.6.1 |
| 网络 | Retrofit + OkHttp | 2.11.0 / 4.12.0 |
| 异步 | Kotlin Coroutines | 1.7.3 |
| 导航 | Navigation Component | 2.7.7 |
| 序列化 | Gson | 2.10.1 |
| CI/CD | GitHub Actions | — |

## 项目结构

```
hr-automation-android/
├── .github/workflows/
│   ├── ci.yml                    # CI：push/PR 自动构建 + 测试
│   └── release.yml               # CD：tag 触发自动发布 Release
├── app/
│   ├── build.gradle              # 模块构建配置（依赖、签名、SDK 版本）
│   ├── lint.xml                  # Lint 规则配置
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/hiringai/mobile/
│       │   ├── HiringAIApplication.kt
│       │   ├── data/local/
│       │   │   ├── AppDatabase.kt          # Room 数据库（singleton）
│       │   │   ├── dao/
│       │   │   │   └── Daos.kt             # JobDao, CandidateDao, MatchDao
│       │   │   └── entity/
│       │   │       └── Entities.kt         # JobEntity, CandidateEntity, MatchEntity
│       │   ├── ml/
│       │   │   ├── LocalLLMService.kt      # LLM 推理（llama.cpp / Ollama）
│       │   │   └── LocalEmbeddingService.kt # Embedding 向量计算（ONNX）
│       │   └── ui/
│       │       ├── MainActivity.kt         # 主 Activity + BottomNavigation
│       │       ├── candidates/CandidatesFragment.kt
│       │       ├── jobs/JobsFragment.kt
│       │       ├── matches/MatchesFragment.kt
│       │       └── settings/SettingsFragment.kt
│       └── res/
│           ├── layout/                     # XML 布局文件
│           ├── mipmap-anydpi/              # Adaptive Icon（含 monochrome）
│           ├── navigation/                 # Navigation 图
│           ├── values/                     # strings, colors, themes
│           └── xml/                        # 其他配置
├── gradle/wrapper/
│   └── gradle-wrapper.properties           # Gradle 8.4
├── build.gradle                            # 根级构建（插件声明）
├── settings.gradle                         # 项目设置
├── gradle.properties                       # 全局 Gradle 配置
├── README.md                               # 本文件
├── RELEASE.md                              # CI/CD 发布说明
├── CHANGELOG.md                            # 变更日志
└── CONTRIBUTING.md                         # 贡献指南
```

## 构建与运行

### 环境要求

- Android Studio Hedgehog (2023.1.1) 或更新
- JDK 17
- Android SDK：compileSdk 35, minSdk 26
- Gradle 8.4（项目自带 wrapper）

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

## License

Private — Internal Use Only
