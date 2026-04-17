# 贡献指南

## 开发环境设置

1. 克隆仓库
2. 使用 Android Studio Hedgehog (2023.1.1) 或更新版本打开项目
3. 等待 Gradle Sync 完成
4. 连接 Android 设备或启动模拟器（API 26+）
5. 运行项目

## 代码规范

### Kotlin

- 遵循 [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- 使用 4 空格缩进
- 数据类优先用于数据持有
- 协程用于异步操作，避免回调地狱
- ViewModel + LiveData/StateFlow 管理 UI 状态

### Android

- minSdk 26, targetSdk 35
- ViewBinding 用于视图绑定（暂未迁移 Compose）
- Room 用于本地数据持久化
- Navigation Component 用于页面导航
- Material Components 用于 UI 组件

### 资源文件

- 字符串资源放在 `res/values/strings.xml`，禁止布局中硬编码文本
- 颜色资源放在 `res/values/colors.xml`
- 布局文件命名：`fragment_*.xml`、`item_*.xml`、`activity_*.xml`
- FAB 和 ImageView 必须添加 `contentDescription`

## 提交规范

使用 [Conventional Commits](https://www.conventionalcommits.org/) 格式：

```
<type>(<scope>): <description>

[optional body]
```

类型说明：

| 类型 | 用途 |
|------|------|
| feat | 新功能 |
| fix | Bug 修复 |
| refactor | 重构（不改变行为） |
| docs | 文档更新 |
| style | 格式调整（不影响逻辑） |
| test | 测试相关 |
| ci | CI/CD 配置变更 |
| chore | 构建、依赖等杂项 |

示例：
```
feat(matches): add cosine similarity sorting
fix(llm): correct OkHttp 4.x RequestBody API usage
ci: upgrade gradle-build-action to setup-gradle@v4
```

## 分支策略

- `main` — 稳定发布分支
- `develop` — 开发集成分支
- `feature/*` — 功能开发分支

## PR 流程

1. 从 `develop` 创建 `feature/your-feature` 分支
2. 开发并确保本地构建通过：`./gradlew assembleDebug`
3. 确保 lint 零警告：`./gradlew lint`
4. 提交 PR 到 `develop`，描述改动内容和关联 Issue
5. CI 自动构建验证通过后合并

## 构建验证

提交前建议运行：

```bash
# 构建
./gradlew assembleDebug

# 测试
./gradlew test

# Lint
./gradlew lint

# 一键全检
./gradlew assembleDebug test lint
```

## 发布流程

详见 [RELEASE.md](RELEASE.md)

```bash
git tag v1.x.x
git push origin v1.x.x
# GitHub Actions 自动构建并创建 Release
```
