# HRAutomation Android - GitHub Actions CI/CD 使用说明

## 工作流说明

### 1. CI 构建检查 (`ci.yml`)

**触发条件**：push 到 `main`、`develop`、`feature/**` 分支，或发起 PR

**执行内容**：
- 运行单元测试
- 构建 Debug APK
- 上传 Debug APK 为临时 artifact（保留 7 天，可在 Actions 页面下载）

---

### 2. Release 发布 (`release.yml`)

**触发条件**：推送 `v*.*.*` 格式的 tag（如 `v1.0.0`）

**执行内容**：
1. 从 tag 提取版本号（`v1.2.3` → `VERSION_NAME=1.2.3`）
2. 以 git commit 总数作为 `versionCode`（单调递增）
3. 构建 Release APK（文件名：`HRAutomation-release-v1.2.3.apk`）
4. 自动生成 Changelog（与上一个 tag 之间的 commits）
5. 创建 GitHub Release，APK 自动挂载到 Release 下载区

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

# 3. 等待 GitHub Actions 完成（约 5-8 分钟）
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

## 文件结构

```
hr-automation-android/
├── .github/
│   └── workflows/
│       ├── ci.yml          ← 持续集成（每次 push）
│       └── release.yml     ← 自动发布（push tag）
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties
├── app/
│   └── build.gradle        ← 含签名配置和版本号环境变量读取
├── build.gradle            ← 根级构建（插件声明）
├── settings.gradle         ← 项目设置（模块包含、仓库）
└── gradle.properties       ← 全局 Gradle 配置
```
