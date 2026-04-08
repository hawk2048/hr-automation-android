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
│  LocalLLMService    → 远程 Ollama（已实现）   │
│                     → llama.cpp JNI（未实现）  │
│  LocalEmbeddingService → ONNX Runtime（未实现）│
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
| 远程 Ollama | 任意 | 不限 | 可用 | 电脑/服务器运行 Ollama，手机通过 HTTP 调用 |
| 本地 llama.cpp | Qwen2.5-0.5B-Q4 | 2 GB | 未实现 | 需集成 llama.cpp JNI |
| 本地 llama.cpp | Phi-3-Mini-Q4 | 4 GB | 未实现 | 需集成 llama.cpp JNI |
| 本地 ONNX | all-MiniLM-L6-v2 | <1 GB | 未实现 | 需集成 ONNX Runtime |

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

### 方案一：远程 Ollama 服务器（推荐，当前唯一可用）

Ollama 是桌面/服务端程序，不支持在 Android 设备上运行。需要在一台电脑或服务器上启动 Ollama，然后手机通过网络连接。

1. 在**电脑或服务器**上安装并启动 Ollama：`ollama serve`
2. 拉取模型：`ollama pull qwen2.5:0.5b`
3. 确保 Ollama 监听地址可被手机访问（默认 `0.0.0.0:11434`）
4. 在 App 设置页面填写 Ollama 地址：
   - **模拟器**：`http://10.0.2.2:11434`（访问宿主机）
   - **真机**：`http://<电脑局域网IP>:11434`（如 `http://192.168.1.100:11434`）
   - **远程服务器**：`http://<服务器IP>:11434`

> 注意：Ollama 默认只监听 127.0.0.1，如需局域网访问需设置环境变量 `OLLAMA_HOST=0.0.0.0`。

### 方案二：设备端本地推理（尚未实现）

设备端推理需要集成 llama.cpp Android 库通过 JNI 加载 GGUF 模型，代码框架已在 `LocalLLMService.kt` 中预留，但核心推理逻辑尚未实现。Embedding 向量计算同理，需要集成 ONNX Runtime Android 库。

如果需要实现本地推理，推荐方案：
- **LLM**：集成 [llama.cpp Android](https://github.com/ggerganov/llama.cpp/tree/master/android) 或 [MLC-LLM Android](https://github.com/mlc-ai/mlc-llm)，可跑 Qwen2.5-0.5B 等小模型
- **Embedding**：集成 [ONNX Runtime Mobile](https://onnxruntime.ai/docs/mobile/)，可跑 all-MiniLM-L6-v2
- 两者均支持离线运行，数据不出设备

## 权限说明

| 权限 | 用途 |
|------|------|
| INTERNET | 访问远程 Ollama 服务器、下载模型 |
| ACCESS_NETWORK_STATE | 检测网络可用性 |

## License

Private — Internal Use Only
