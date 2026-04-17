# Android 开发环境配置计划 (WSL Ubuntu 24.04)

## 需求摘要

在 WSL Ubuntu 24.04 中配置 Android 命令行开发环境，无需完整 IDE。

- **安装方式**: cmdline-tools-setup 脚本
- **SDK 版本**: API 35 (Android 15)
- **Build Tools**: 35.0.0

## 验收标准

1. ✅ `sdkmanager --list` 可正常执行
2. ✅ `echo $ANDROID_HOME` 输出正确的 SDK 路径
3. ✅ `echo $ANDROID_SDK_ROOT` 输出正确的 SDK 路径
4. ✅ 已安装 platform-tools、build-tools;35.0.0、platforms;android-35
5. ✅ Gradle 构建可正常执行 (`./gradlew tasks`)

## 实现步骤

### 步骤 1: 安装系统依赖

```bash
sudo apt update
sudo apt install -y wget unzip openjdk-17-jdk
```

**文件**: 无

---

### 步骤 2: 下载并运行 cmdline-tools-setup 脚本

```bash
cd ~
wget -q https://github.com/nicokosi/cmdline-tools-setup/releases/latest/download/cmdline-tools-setup.sh -O cmdline-tools-setup.sh
chmod +x cmdline-tools-setup.sh
./cmdline-tools-setup.sh --sdk-root $HOME/android-sdk
```

**验证**: 
- 检查 `$HOME/android-sdk` 目录是否存在
- 检查 `$HOME/android-sdk/cmdline-tools/latest/bin/` 是否存在

---

### 步骤 3: 配置环境变量

将以下内容添加到 `~/.bashrc`:

```bash
# Android SDK
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$HOME/android-sdk"
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"
```

执行生效:
```bash
source ~/.bashrc
```

**文件**: `~/.bashrc`

---

### 步骤 4: 安装 Android SDK 组件

```bash
# 接受所有许可证
yes | sdkmanager --licenses

# 安装必要的组件
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

**验证**: `sdkmanager --list_installed`

---

### 步骤 5: 配置 Gradle 项目 (可选验证)

在项目目录运行:
```bash
cd /mnt/d/work/AI\ Tools/claude/hr-automation-android
./gradlew tasks --no-daemon
```

**预期输出**: 显示可用任务列表

---

## 风险与缓解

| 风险 | 缓解措施 |
|------|----------|
| WSL 无法直接运行 GUI 模拟器 | 仅配置命令行工具，无需模拟器 |
| 网络问题导致下载失败 | 使用镜像源或预先下载 |
| Java 版本不兼容 | 指定使用 JDK 17 |

---

## 验证步骤

1. `java -version` → 显示 OpenJDK 17
2. `sdkmanager --version` → 显示版本号
3. `echo $ANDROID_HOME` → 显示 `/root/android-sdk`
4. `./gradlew tasks --no-daemon` → 成功显示任务列表

---

## 预计时间

- 系统依赖安装: ~2 分钟
- SDK 下载安装: ~5-10 分钟
- 环境配置: ~1 分钟
- 验证: ~3 分钟

**总计: 约 15 分钟**