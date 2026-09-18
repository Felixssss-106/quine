# Changelog

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 与
[语义化版本](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Added · M0 骨架

工程与基础设施：

- Gradle 多模块骨架（10 个模块）+ Version Catalog + wrapper 8.14.3。
- CI：`lint` + `testDebugUnitTest` + `assembleDebug`（`.github/workflows/ci.yml`）。
- MIT 许可证；`docs/` 设计文档集与 `icons/` 图标资产随仓。
- `local.properties` 不入版本库；备份与设备迁移在 `data_extraction_rules.xml` 里全量排除
  （Android 12+ 的 D2D 直传不受 `allowBackup=false` 约束）。

`core-common`：

- 统一错误模型 `QuineError`（发生了什么 / 影响 / 下一步三段式）与 `ErrorKind`。
- 日志脱敏 `LogRedaction` + `Secret`（Key、Bearer、邮箱形状）。
- `Ids` / `SystemTime` / `QuineDispatchers`；`TrustLevel` / `IconVariant` / `IconAliasController`。

`core-gateway`（纯 JVM，可脱离 Android 单测）：

- OpenAI 兼容模型网关：SSE 流式、增量工具调用拼装、usage 统计、五类错误映射、
  重试预算、`ProviderProbe` 连通性探测。
- 供应商预设：DeepSeek / Kimi / 智谱 GLM / 通义千问 / OpenAI / 自定义。

`core-tools`：

- 工具协议 `ToolSpec` / `Tool` / `ToolResult` / `ToolRegistry`，危险等级与能力等级枚举。
- 手写 JSON Schema 子集校验器（`type` / `properties` / `required` / `enum` / `items` /
  `additionalProperties`）；校验失败不抛异常，而是回灌给模型自纠。
- `PathGuard`：拒绝绝对路径、反斜杠、NUL、`..` 与 `.` 跳转段。
- `Workspace` 抽象 + 两个实现：`PrivateWorkspace`（`filesDir/workspace`，纯 java.io）
  与 `SafWorkspace`（SAF 授权目录，逐段 `findFile`，结构上不可逃逸）。
- `fs_read`：64 KB 截断并带 `sourceRef`；未授权时回落私有工作区。

`core-loop`：

- agent 状态机 `LoopState` / `LoopEvent` / `AgentLoop`（`channelFlow` 实现）。
- 工具结果回灌（失败也回灌，让模型自纠）；取消语义；上下文滑动窗口裁尾并丢弃孤儿 TOOL 消息。
- 重试的**唯一权威在 loop**：装配时把 provider 的重试预算设为 0，避免两层重试相乘。

`core-storage`：

- Room（`conversations` / `messages`，`exportSchema = false`）+ `ConversationStore`。
- DataStore `SettingsStore`：供应商配置、SAF 目录、图标变体、信任档位、当前会话、草稿。
- `KeystoreApiKeyStore`：AndroidKeyStore AES/GCM，密钥不出硬件，密文进私有 SharedPreferences。

`core-design`：

- 「双面人」token 层单一来源：色 / 字阶 / 圆角与网格 / 动效；`ink` 是唯一翻转的 token。
- `QuineTheme` 同时映射到 Material3 `ColorScheme` / `Typography`，让 Material3 组件继承同一套语言。
- 基础组件：ink 药丸按钮、描边次按钮、文字按钮、chip、档位胶囊、描边输入框（含 ±6px×3 抖动）、
  毛发分隔线、呼吸点、骨架屏 shimmer、流式光标；触感映射。
- 打包 Maple Mono 三档字重（OFL-1.1，见 `licenses/maple-mono-OFL.txt`）。

界面：

- `feature-onboarding`：首启屏一（「你好。」+ 供应商 chips + Key 校验 + 隐私小字 +
  SAF 目录选择；拒绝授权时明确说明会回落私有工作区）。
- `feature-chat`：消息流（用户气泡 / AI 裸文本 + Markdown）、80ms/块流式 + 1s 呼吸光标、
  活动卡一行摘要可展开、错误条带重试/编辑、composer 三态 + 档位胶囊 + 1→6 行、
  草稿与滚动位置恢复。
- `feature-settings`：供应商 / Key / 模型 / 连通性测试、工作目录、外观·应用图标深浅切换、信任档位、关于。
- `app`：单 Activity + 导航 + 手工装配 `AppContainer` + 双 `activity-alias` 图标切换。

### Fixed

- `SseFrameDecoder` 用 `data.isEmpty()` 当作「没收到 data」的哨兵，导致裸 `data`
  字段（SSE 规范里合法的空值 data）无法产出一帧。改为单独记录「是否见过 data 字段」。
- `OpenAiChunkParser` 遇到模型服务返回的坏 JSON 时直接抛 `SerializationException`，
  用户会看到一串解析器术语。现在统一收敛成人话 `LlmException`（「模型服务返回了看不懂的内容」
  + 下一步动作）。
- **CI 漏跑纯 JVM 模块的测试**：`testDebugUnitTest` 只覆盖 Android 模块的 debug 变体，
  `core-common` / `core-gateway` 的测试任务名是 `test`，此前从未执行。CI 改用 `test`。
  这个缺口正是上面两个 bug 一直没被发现的原因。

### 说明

- 许可证：MIT。`applicationId = com.quine.app`，minSdk 29 / targetSdk 36 / compileSdk 36。
- Markdown 渲染用 `multiplatform-markdown-renderer-m3` **0.36.0-b02**：
  该版本与项目同为 Kotlin 2.2.0；0.39.0 起改用 Kotlin 2.3+ 编译，2.2.0 编译器读不了其元数据。
- 等宽字体取 Maple Mono 标准变体（不含 CJK 字形），中文由系统字体回落链兜住。
- 沙箱（proot）、任务面、搜索 / WebView、Shizuku、自动化按里程碑 M1–M5 推进。

### 尚未实现（M0 范围内的明确缺口）

- 语音输入、附件菜单、`＋` 菜单的实际功能：点击只给「后续版本上线」说明。
- 审批矩阵：信任档位已持久化，但拦截逻辑在 M1 生效。
- 流式过程中被停止或失败的那一段，只落库已吐出的文本，不保留未完成轮次的活动卡。

## [0.1.0] - 待发布

首个里程碑（M0）尚未打 tag；验收线是「能对话、看流式、补全一个工具调用并回显结果」。
