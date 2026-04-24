# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**HRAutomation Android** — 本地 LLM + Embedding 驱动的智能招聘系统 Android 客户端。

- **Primary Language**: Kotlin 2.0.21
- **Min SDK**: Android 8.0 (API 26)
- **Target SDK**: Android 15 (API 35)
- **Compile SDK**: Android 16 (API 36)
- **Build System**: Gradle 8.11.1 + AGP 8.9.1
- **Architecture**: MVVM — Single Activity + Multiple Fragments with ViewBinding + ViewModel + Repository

## Build Commands

```bash
# Debug APK
./gradlew assembleDebug

# Release APK (per-ABI splits for MIUI compatibility)
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run lint checks
./gradlew lint

# Install via ADB
adb install app/build/outputs/apk/debug/app-debug.apk

# Initialize submodule after clone
git submodule update --init --recursive
```

## Architecture

### UI Layer
- `MainActivity` — Single activity with BottomNavigationView (5 tabs)
- Main tabs: Jobs, Candidates, Matching Center, Benchmark, Settings
- Fragments use ViewBinding (not Compose)
- RecyclerView adapters for list displays
- ViewModel + LiveData for state management

### Data Layer
- **Room Database** (`AppDatabase`) — Singleton, thread-safe
- **Entities**: `JobEntity`, `CandidateEntity`, `MatchEntity`, `ApplicationEntity`
- **DAOs**: `JobDao`, `CandidateDao`, `MatchDao`, `ApplicationDao`
- **Repositories**: `JobRepository`, `CandidateRepository`, `MatchRepository`, `ApplicationRepository`

### ML Layer (Submodule: hiringai-ml-kit)
ML 代码已完全剥离到独立 Git 子模块 `hiringai-ml-kit/`，通过 Bridge 模式与主应用解耦。

- **LocalLLMService** — Singleton for LLM inference (in `hiringai-ml-kit/ml/`)
  - Device-side: llama.cpp via `llama-kotlin-android` (GGUF models)
  - Remote: Ollama HTTP API
  - Models stored in `app/files/models/`
- **LocalEmbeddingService** — ONNX Runtime for vector embedding (in `hiringai-ml-kit/ml/`)
  - Model: all-MiniLM-L6-v2 (~90MB)
  - Stored in `app/files/embedding/`
- **MlBridge** — Data bridge decoupling business entities from ML module
  - `JobInfo(title, requirements)` replaces direct `JobEntity` usage
  - `CandidateInfo(name, email, phone, resume)` replaces direct `CandidateEntity` usage

### Native Library Safety
- **主仓库 SafeNativeLoader** (`com.hiringai.mobile.SafeNativeLoader`) — uses `HiringAIApplication.instance` singleton
- **子模块 SafeNativeLoader** (`com.hiringai.mobile.ml.SafeNativeLoader`) — uses Context parameter, no Application dependency
- Both versions share the same crash marker file
- Crash detection prevents re-loading incompatible native libraries

## Key Patterns

### Bridge Pattern (ML Decoupling)
```kotlin
// 主仓库 ViewModel 调用 ML 服务时，通过 Bridge 转换实体
val jobInfo = JobInfo(title = job.title, requirements = job.requirements)
llmService.generateJobProfile(jobInfo)

val candidateInfo = CandidateInfo(name = candidate.name, email = candidate.email,
    phone = candidate.phone, resume = candidate.resume)
llmService.generateCandidateProfile(candidateInfo)
```

### Singleton Services
```kotlin
LocalLLMService.getInstance(context)     // from hiringai-ml-kit
LocalEmbeddingService.getInstance(context) // from hiringai-ml-kit
AppDatabase.getInstance(context)           // from app module
```

### ML Inference Priority
1. Local llama.cpp model (if loaded)
2. Remote Ollama server (fallback)
3. Return empty/error response if neither available

## Common Development Tasks

### Adding a new entity
1. Create entity class in `data/local/entity/Entities.kt`
2. Add to `@Database(entities = [...])` in `AppDatabase.kt`
3. Create DAO in `data/local/dao/Daos.kt`
4. Add abstract method to `AppDatabase.kt`
5. Create Repository in `data/repository/`

### Adding ML model
Add to `LocalLLMService.AVAILABLE_MODELS` in `hiringai-ml-kit/ml/`:
```kotlin
ModelConfig(
    name = "ModelName-Q4_0",
    url = "$HF_MIRROR/owner/repo/resolve/main/model.gguf",
    size = 394_774_816,
    requiredRAM = 1, // GB
    contextSize = 2048,
    template = "chatml", // prompt template
    description = "..."
)
```

### Testing ML features
- Local model requires arm64-v8a or x86_64 ABI
- NDK filters: `ndk { abiFilters 'arm64-v8a', 'x86_64' }`
- Ollama requires network connectivity
- Standalone test APK: `./gradlew :app:assembleDebug -PstandaloneBuild=true` (in hiringai-ml-kit/)

## File Locations

| Component | Path |
|-----------|------|
| Application | `app/src/main/java/com/hiringai/mobile/HiringAIApplication.kt` |
| Database | `app/src/main/java/com/hiringai/mobile/data/local/` |
| Repositories | `app/src/main/java/com/hiringai/mobile/data/repository/` |
| UI Fragments | `app/src/main/java/com/hiringai/mobile/ui/` |
| ViewModels | `app/src/main/java/com/hiringai/mobile/ui/*/` |
| Benchmark UI | `app/src/main/java/com/hiringai/mobile/ui/benchmark/` |
| Accuracy Metrics | `app/src/main/java/com/hiringai/mobile/benchmark/` |
| Layouts | `app/src/main/res/layout/` |
| Strings/Colors | `app/src/main/res/values/` |
| ML Services | `hiringai-ml-kit/ml/src/main/java/com/hiringai/mobile/ml/` |
| ML Bridge | `hiringai-ml-kit/ml/src/main/java/com/hiringai/mobile/ml/bridge/` |
| Model Catalog | `hiringai-ml-kit/ml/src/main/java/com/hiringai/mobile/ml/catalog/` |
| Benchmark Runner | `hiringai-ml-kit/ml/src/main/java/com/hiringai/mobile/ml/benchmark/` |
| ML Logging | `hiringai-ml-kit/ml/src/main/java/com/hiringai/mobile/ml/logging/` |
| Test APK UI | `hiringai-ml-kit/app/src/main/java/com/hiringai/mobile/ml/testapp/ui/` |
