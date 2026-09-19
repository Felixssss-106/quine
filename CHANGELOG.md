# Changelog

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 与
[语义化版本](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Added · M1 底座（进行中）

- `core-storage`：任务与快照的数据层 —— `TaskRun` / `TaskStep` / `Snapshot` 三张表 +
  `TaskDao` + v1→v2 迁移。数据模型直接取 `agent-prompt.md` §2.6，不是另起炉灶。
  - 迁移只新增表，不动 `conversations` / `messages`；有仪器测试守着「升级后旧会话不丢」。
  - `Snapshot.blobRef` 是内容寻址（同一内容只存一份），回滚 = 把 blob 写回原路径。
- `docs/plans/m1.md` 的三个前置决定：① 沙箱发行版定为 Alpine 起步；② **Android 10–13 已闭环**；
  ③ 自用验收三件真事取自简报 §3.1 / §3.4。均标注可推翻。

### 已闭环：Android 10–13 上执行二进制（M0 时期标记的最大风险）

M0 开局标过：「Android 10+ 对从应用私有目录执行二进制有 SELinux 限制，直接决定 proot
沙箱走哪条路」。装了 API 29 镜像（AVD `QpApi29`）实测后确认：**不存在这个限制**。

| 场景 | API 29 / Android 10 | API 36 / Android 16 |
|---|---|---|
| 应用主目录 exec ELF | ✅ | ✅ |
| `files/rootfs/bin/` 子目录 exec（真实 rootfs 布局） | ✅ | ✅ |
| 带 `LD_LIBRARY_PATH` exec | ✅ | — |

→ minSdk 29 → 36 行为一致，**沙箱按统一方案实现即可，不需要降级分支或降级提示**。
真正的约束在打包（二进制须以 `libxxx.so` 放进 `jniLibs/<abi>/` + `extractNativeLibs=true`），不在执行。
仍建议真机（Android 14+）复测一次，模拟器与真机的 SELinux 策略可能有差异。

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
- **`http://` 的 baseUrl 一律连不上**：targetSdk 28+ 默认禁止明文流量，而错误文案是
  「网络没连上模型服务。检查网络或代理」——把用户引向完全错误的排查方向。
  在模拟器上做了对照实验（同主机同路径，只换 scheme）：`https://example.com/v1` 拿到
  真实的 405，`http://example.com/v1` 报网络错误，坐实是明文策略而非网络问题。
  处理：**放行明文，但用可见警告承担风险**（见下）。

### 决策：明文 HTTP 放行 + 可见警告

`app/src/main/res/xml/network_security_config.xml` 放开明文，理由是：

- `baseUrl` 是用户自己填的，自建 / 局域网模型服务（Ollama、LM Studio、vLLM、内网网关）
  通常只有 http；一刀切拦掉会让「自定义 baseUrl」这个文档承诺的能力在最常见的自建场景失效。
- 风险不靠**静默拦截**承担，而是靠设置页在 baseUrl 为 `http://` 时给一条可见警告
  （「这个地址没有加密。Key 会以明文发出去。只在你自己的局域网里这样用；公网地址请换成 https。」）。
- 预设供应商全是 https，默认路径依然加密。

这与项目的设计哲学一致：**透明 > 一刀切**，让用户看见风险，而不是替他做决定。

### 验收

在模拟器（AVD `AILifeTest`，Android 16 / API 36）上实跑，M0 验收线通过：

- 首启屏一浅色 / 深色两套都正确渲染，主题跟随系统
- composer 三态黑圆钮全部验证：空 = 语音 · 有字 = ↑ 发送 · 生成中 = ▪ 停止
- 未接模型时发送 → 错误条三段式 + 重试 / 编辑，无崩溃
- **完整链路**：用户消息 → 流式吐字 → `fs_read` 真的读到了工作区里的文件 →
  活动卡「读了 notes/todo.md」→ 结果回灌后模型继续输出 Markdown 列表
- 设置页：供应商配置保存成功，Key 以 `sk-…7890` 掩码显示，连通性测试通过
- `./gradlew lint test assembleDebug` 全绿，112 个 JVM 单测通过
- **19 个仪器测试全绿**（模拟器实跑）：`app` 3 个（图标 alias 切换 —— 守着
  「顺序写反会让两个桌面入口同时关闭、图标彻底消失」这个灾难性失败模式）、
  `core-storage` 12 个（Room DAO 级联删除 / 标题派生 / JSON 往返 + Keystore
  **明文不落盘**）、`feature-chat` 4 个（Compose 渲染冒烟）
- CI 新增 advisory 的 `instrumented` job（起模拟器跑上面这组），先非阻断观察一轮
- **附录 C 狗粮清单可做的部分跑完**：冷启动 ~1.9s（软件渲染模拟器，真机基准待补）；
  断网发送给出三段式错误 + 重试，且重试真的能恢复；杀进程后会话与草稿完整恢复；
  旋转状态不丢；用特征串 Key 走完整链路后 logcat **0 处命中明文**、私有目录也无明文。
  剩下 3 项（删除确认、快照回滚、飞行模式跑沙箱）依赖 M1 的能力，届时补
- 截图见 `docs/shots/m0/`

尚未做：真机（Android 14+）上的同一组验证、旋转 / 折叠、图标切换后的桌面图标刷新、
附录 C 狗粮清单里的冷启动耗时与 logcat 无 Key 明文抽查。

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
