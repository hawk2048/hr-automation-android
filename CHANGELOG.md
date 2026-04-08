# Changelog

All notable changes to this project will be documented in this file.

## [1.0.0] - 2026-04-08

### Added

- 职位管理：创建、编辑、归档招聘职位
- 候选人管理：录入候选人信息与简历关联
- 智能匹配：基于 Embedding 向量相似度的自动匹配排序
- 人才画像：LLM 生成候选人评估（本地 / Ollama 双模式）
- 本地 Embedding 服务：ONNX Runtime + all-MiniLM-L6-v2
- Room 本地数据库：jobs、candidates、matches 三表结构
- Material Design UI：Bottom Navigation + 4 个功能 Fragment
- 设置页面：Ollama 地址配置、模型下载、关于信息
- GitHub Actions CI/CD：自动构建 + 测试 + Release 发布

### Fixed

- OkHttp 4.x API 兼容性：RequestBody.create() 参数顺序修正
- Release workflow ANDROID_HOME 覆盖导致的 SDK 检测失败
- 全部 lint 警告清零（12 类警告修复）
- gradle-build-action 废弃替换为 gradle/actions/setup-gradle@v4
- Node.js 24 适配（FORCE_JAVASCRIPT_ACTIONS_TO_NODE24）
