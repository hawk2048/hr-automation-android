## Handoff: team-plan → team-exec
- **Decided**: 采用标准 MVVM 架构 - Repository 封装数据访问，ViewModel 管理状态，Fragment 负责渲染。任务依赖：#3,#4 先完成 → #1,#2 才能开始。
- **Rejected**: 完全不依赖 Hilt/Koin 的方案（保持简单），完全重写 Entity 方案（保持兼容）
- **Risks**: ViewModel 生命周期与 Fragment 需要正确处理，避免内存泄漏
- **Files**: 需创建 repository/ 和 viewmodel/ 目录，修改现有 Fragment
- **Remaining**: 4个worker并行执行，worker-1/2做底层设施，worker-3/4做UI层重构