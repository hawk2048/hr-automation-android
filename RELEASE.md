# HRAutomation Android - CI/CD 发布说明

## 工作流说明

### 1. CI 构建检查 (`ci.yml`)

**触发条件**：push 到 `main`、`develop`、`feature/**` 分支，或发起 PR 到 `main`/`develop`

**并发控制**：同一分支的多次 push 会取消前序运行（`cancel-in-progress: true`）

**Job 结构**：

| Job | 依赖 | 说明 |
|-----|------|------|
| `lint` | 无 | Android Lint 检查 |
| `test` | 无 | 运行单元测试（`testDebugUnitTest`） |
| `build` | lint + test | 构建 Debug APK，构建失败自动创建 Issue |

**技术细节**：
- 运行环境：`ubuntu-latest`
- JDK 17 (Temurin)
- Gradle 8.11.1（通过 `gradle/actions/setup-gradle@v4` 安装）
- Android SDK：`android-actions/setup-android@v2` + `platforms;android-36` + `build-tools;35.0.0`
- Node.js 24 强制启用（`FORCE_JAVASCRIPT_ACTIONS_TO_NODE24=true`）
- 分支缓存策略：仅 main/develop 分支写入缓存，其他分支只读（`cache-read-only`）
- Lint 和测试并行执行，通过后才开始构建

**产出**：
- Lint 报告 HTML（保留 7 天）
- 单元测试报告（保留 7 天）
- Debug APK artifact（保留 7 天）
- 构建日志（保留 7 天）

---

### 2. Release 发布 (`release.yml`)

**触发条件**：推送 `v*.*.*` 格式的 tag（如 `v1.0.0`）

**并发控制**：同一 tag 不取消正在运行的发布流程

**执行内容**：
1. 从 tag 提取版本号（`v1.2.3` → `VERSION_NAME=1.2.3`）
2. 以 git commit 总数作为 `versionCode`（单调递增）
3. 构建 Release APK（per-ABI splits：arm64-v8a + x86_64 + universal）
4. 验证 APK 签名
5. 自动生成 Changelog（与上一个 tag 之间的 commits）
6. 创建 GitHub Release，APK 自动挂载到 Release 下载区
7. 上传 mapping 文件（用于 R8 混淆后的 crash 解析，保留 90 天）

**技术细节**：
- 与 CI 使用相同的 SDK 和 Gradle 配置
- SDK 安装包含 `platforms;android-36` + `platforms;android-35` + `build-tools;35.0.0`
- Release APK 使用 per-ABI splits 减少 APK 体积，兼容 MIUI 安装
- 未配置签名 Secrets 时自动使用 debug 签名
- APK 签名验证步骤确保产物有效

---

### 3. 代码质量检查 (`code-quality.yml`)

**触发条件**：push 到 `main`/`develop` 或 PR 到 `main`/`develop`

**Job 结构**：

| Job | 说明 |
|-----|------|
| `detekt` | Detekt Kotlin 静态分析（HTML/XML/TXT/SARIF 报告） |
| `dependency-review` | PR 依赖审查（仅 PR 触发，检查中高风险漏洞） |

**Detekt 配置**：
- 配置文件：项目根目录 `detekt.yml`
- 报告格式：HTML、XML、TXT、SARIF
- 基线文件：`detekt-baseline.xml`（首次运行可生成）
- 本地运行：`./gradlew detekt`

---

## 发布新版本步骤

```bash
# 1. 确保代码已提交并推送到 main
git add .
git commit -m "feat: 新功能描述"
git push origin main

# 2. 打 tag（遵循语义化版本）
git tag v1.0.0
git push origin v1.0.0

# 3. 等待 GitHub Actions 完成（约 3-5 分钟）
# 完成后访问 Releases 页面即可下载 APK
```

发布后 APK 下载地址格式：
```
https://github.com/<用户名>/<仓库名>/releases/tag/v1.0.0
```

---

## 可选：配置正式签名（推荐生产使用）

如果需要用正式 keystore 签名（而非 debug 签名），在 GitHub 仓库 Settings → Secrets and variables → Actions 中配置以下 Secrets：

| Secret 名称 | 说明 |
|------------|------|
| `KEYSTORE_BASE64` | keystore 文件的 Base64 编码内容 |
| `KEYSTORE_PASSWORD` | keystore 密码 |
| `KEY_ALIAS` | key alias 名称 |
| `KEY_PASSWORD` | key 密码 |

生成 Base64 编码的命令：
```bash
# macOS / Linux
base64 -i release.jks | pbcopy

# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks")) | Set-Clipboard
```

**如果未配置 Secrets，workflow 自动回退使用 debug 签名**，可以正常安装到设备上进行测试。

---

## 版本命名规范

| tag 示例 | 说明 | 是否标记为预发布 |
|---------|------|----------------|
| `v1.0.0` | 正式版本 | 否 |
| `v1.1.0` | 功能迭代 | 否 |
| `v1.0.1` | Bug 修复 | 否 |
| `v1.0.0-beta.1` | 测试版（含 `-`）| 是（Prerelease）|
| `v2.0.0-rc.1` | 候选版本 | 是（Prerelease）|

---

## 构建产物

| 产物 | 路径 | 说明 |
|------|------|------|
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` | CI artifact，含调试信息 |
| Release APK (arm64) | `app/build/outputs/apk/release/app-arm64-v8a-release.apk` | 主力发布包 |
| Release APK (x86_64) | `app/build/outputs/apk/release/app-x86_64-release.apk` | 模拟器/x86 设备 |
| Release APK (universal) | `app/build/outputs/apk/release/app-universal-release.apk` | 全架构包 |
| Mapping 文件 | `app/build/outputs/mapping/release/mapping.txt` | R8 混淆映射，保留 90 天 |
| Detekt 报告 | `app/build/reports/detekt/` | 代码质量分析 |
| Lint 报告 | `app/build/reports/lint-results-debug.html` | Android Lint 结果 |

---

## 文件结构

```
hr-automation-android/
├── .github/
│   └── workflows/
│       ├── ci.yml               ← 持续集成（lint + test + build 三阶段）
│       ├── release.yml          ← 自动发布（tag 触发）
│       └── code-quality.yml     ← 代码质量（Detekt + 依赖审查）
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties
├── app/
│   ├── build.gradle             ← 模块构建（依赖、签名、SDK 版本、KSP、Detekt）
│   ├── lint.xml                 ← Lint 规则配置
│   ├── proguard-rules.pro       ← ProGuard 混淆规则
│   └── src/
│       ├── main/                ← 源码和资源
│       ├── test/                ← 单元测试（JVM）
│       └── androidTest/         ← 仪器测试（设备/模拟器）
├── build.gradle                 ← 根级构建（AGP + Kotlin + KSP + Detekt 插件）
├── settings.gradle              ← 项目设置（模块包含、仓库）
├── gradle.properties            ← 全局 Gradle 配置（并行构建、缓存）
├── detekt.yml                   ← Detekt 静态分析配置
└── README.md
```

---

## 本地验证命令

```bash
# 一键全检（与 CI 对齐）
./gradlew detekt lintDebug testDebugUnitTest assembleDebug

# 单独运行各检查
./gradlew detekt                  # Detekt 静态分析
./gradlew lintDebug               # Android Lint
./gradlew testDebugUnitTest       # 单元测试
./gradlew assembleDebug           # 构建 Debug APK
./gradlew assembleRelease         # 构建 Release APK
```

---

## 生成 Detekt 基线

首次在已有项目上运行 Detekt 时，可以用基线文件忽略存量问题：

```bash
./gradlew detektBaseline
# 生成 detekt-baseline.xml，已知的存量问题将被忽略
# 后续新增代码的问题仍会被检测
```

---

## 常见问题

### Q: CI 构建失败，提示找不到 Android SDK？

确保 workflow 中有 `android-actions/setup-android@v2` 和 `sdkmanager "platforms;android-36"` 步骤。不要手动覆盖 `ANDROID_HOME` 环境变量。

### Q: Release APK 用的是 debug 签名？

这是正常的。需要配置 GitHub Secrets 中的 `KEYSTORE_BASE64` 等 4 个变量才会使用正式签名。Debug 签名的 APK 一样可以安装测试。

### Q: Detekt 报告太多存量问题怎么办？

运行 `./gradlew detektBaseline` 生成基线文件，存量问题会被忽略，只关注新增代码的问题。

### Q: CI 缓存保存失败？

GitHub Actions 缓存服务偶尔会临时不可用，不影响构建结果。下次成功运行时会自动恢复缓存。仅 main/develop 分支写入缓存，feature 分支只读。

### Q: Lint 报 OldTargetApi 警告？

Lint 不认识最新的 API level，已在 `app/lint.xml` 中抑制此误报。升级 AGP 后可移除。

### Q: 并发控制如何工作？

同一分支的多次 push 会自动取消前序 CI 运行，避免资源浪费。Release 工作流不会取消，确保每个 tag 都能发布。
