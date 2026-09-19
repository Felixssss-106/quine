# Changelog

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 与
[语义化版本](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Added · M1 底座（进行中）

- `core-storage`：任务与快照的数据层 —— `TaskRun` / `TaskStep` / `Snapshot` 三张表 +
  `TaskDao` + v1→v2 迁移。数据模型直接取 `agent-prompt.md` §2.6，不是另起炉灶。
  - 迁移只新增表，不动 `conversations` / `messages`；有仪器测试守着「升级后旧会话不丢」。
  - `Snapshot.blobRef` 是内容寻址（同一内容只存一份），回滚 = 把 blob 写回原路径。
- `core-tools`：文件写与列目录，带**写入前强制快照**。
  - `Workspace` 扩展：`readBytes`（快照按字节走，二进制文件才不会被回滚破坏）、
    `writeBytes`（支持乐观并发检查）、`list`、`delete`；`PrivateWorkspace` 与 `SafWorkspace` 都已实现。
  - `fs_write`：**拿不到快照就拒绝写入**（fail closed），绝不留下撤不回的改动；
    写入前读到的内容会作为 `expectedOldBytes` 再校验一次，挡住「读取后被别人改过」的覆盖。
  - `fs_list`：列目录，目录在前、按名排序。
  - `SnapshotRegistry` 接口（依赖倒置，实现在 app 层接 `SnapshotStore` + `TaskDao`）；
    `ToolContext.snapshots` **刻意不给默认值**，避免调用方在不知不觉中跳过快照这条红线。
  - 写入走「临时文件 + rename」，写到一半崩了不会留下半截文件。
- `core-tools`：`fs_rollback` —— 「改坏了一键回滚」的那一键，闭环 v1-blueprint.md §2.1 的红线。
  - 给 `path` 就回滚该文件最近一次快照，给 `snapshot`（`fs_write` 成功时给出的 id）就回滚到那一次。
  - **只回滚本工作区的快照**：`path` 是相对路径，而用户可在「授权目录」与「私有工作区」之间切换，
    不记下归属就回滚 = 拿 A 的旧内容覆盖 B 的同名文件。为此 `Snapshot` 表加了 `root` 列（v2→v3 迁移）。
  - **回滚前先给当前内容也做一份快照**，所以回滚本身还能再撤回（有测试守着「回滚的回滚」）。
  - 与 `fs_write` 一致 fail closed：快照做不成、或 blob 已被清理，就报错且不动文件。
  - 文件被删了也能靠快照恢复。
  - ⚠️ `fs_rollback` 这个工具 id 不在 `agent-prompt.md` §3.9 的工具清单里（那里只列了
    `fs_read / fs_write / fs_list / fs_search / fs_diff`），是我为实现「一键回滚」新加的 —— 待确认。

- `Snapshotter` → `SnapshotRegistry`：`capture` 返回 `SnapshotRef`（含回滚用的 `id`）而非裸 `blobRef`，
  并补上 `latestFor` / `find` / `contentOf` 三个查询方法；四个方法统一为 `suspend`
  （背后是 Room 与磁盘 IO，在 IO 线程上 `runBlocking` 等自己是把可取消的等待变成堵死的线程）。
- 数据库 v2 → v3：`snapshots` 表加 `root` 列（只加列、不动数据；老快照取空串，回滚时按归属不符拒绝）。
- `core-tools`：`fs_search` —— 在工作区里按内容检索，返回「路径:行号:内容」。
  - 文档 §3.1 写的是「搜索（沙箱内 ripgrep）」，沙箱还没落地，这里先用纯 Kotlin 逐行扫描，
    能力对齐、范围更小（只覆盖工作区）；等沙箱就绪换成 ripgrep 时工具契约不变。
  - 跳过二进制文件（探前 8 KB 有无 NUL 字节）；到结果上限会明说还有更多并带引用（§2.3）。
  - 默认不区分大小写，可用 `caseSensitive` 打开。
- `core-tools`：`fs_diff` —— 「**批量改动先给 diff 预览且可取消**」（§3.1 红线）的落点。
  - 给 `path` 加上打算写入的 `content`，把差别显示出来（`+` 新增、`−` 删除），
    **一个字节都不改**；确认后再用 `fs_write` 写。文件还不存在时整篇算新增。

- `core-sandbox`（**新模块**）：沙箱安装的骨架 —— 首启屏二背后那份工作（`page-specs.md` §1）。
  - 四步状态机：① 检查设备环境 ② 展开 Linux 工作区 ③ 初始化文件系统 ④ 工具链自检。
    **每一步都能单独失败**，且失败时知道自己卡在第几步 —— 屏二的
    「卡在第 ③ 步：存储空间不足。」就是据此说出来的。
  - `TarGz`：自己解析 tar（gzip 用 JDK），不引第三方库。挡掉路径穿越（`../`）、
    跳过设备节点、校验 tar 头校验和；软链建不出来只记 warning（Android 上正常，
    某些 JVM 环境没权限，不能因此把解压判死）。
  - 下载边下边算 sha256（rootfs 几十 MB，不整份读进内存）；校验不过就不装。
  - **可重入**：靠 `.quine-ready` 标记判断上次是否装完，装一半的残留会被清掉再重试。
  - 还没做：真正的 proot 执行与 `shell_run`。

- `feature-onboarding`：**首启屏二（沙箱初始化）**（`page-specs.md` §1）。
  - 四步状态点 + 2pt 进度条 + 等宽日志区（可折叠、约 40 字/秒缓滚）+ 底部小字。
  - 失败态：进度条转 danger 并停住 + 「卡在第 ③ 步：…」+「重试」「清理空间」+ 日志自动展开。
  - 完成：整屏黑白反转 120ms → 进主界面（motion §2.9）。
  - 切后台状态保持（ViewModel + 重建时用 `.quine-ready` 判断是否要重来）。
  - 屏一 → 屏二 → 主界面的导航顺序补齐（原来屏一完就直接进聊天了）。
  - ⚠️ **rootfs 来源还没定**（Alpine/哪个镜像站/是否内置），app 层目前是一个
    `PendingRootfsSource`：不假装有源，而是把「还没配置下载地址」当成第 ② 步的失败说清楚。

### Fixed

- **`fs_rollback` 其实从没被注册进工具注册表**：上一轮改装配处的那两处编辑静默丢失了，
  于是它编译得过、单测也过得，但**模型永远看不到这个工具**。
  修法不是补一行，而是把清单集中到 `BuiltinTools.all`、由 `BuiltinToolsTest` 钉住完整性 ——
  漏注册这种静默失败，靠「记得写」是防不住的。
- 修仪器测试 `ChatScreenRenderTest.渲染流式文本与呼吸光标` 的时序赌注：
  assistant 文本走 Markdown 组件，要先把文本解析成节点才画得出来，首帧未必就绪。
  改成显式等待 —— 注意这里**不能**用 `waitForIdle`，流式光标是无限动画，等到超时也不会 idle。

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

## [Unreleased · M1] — 任务面 UI（Issue #5）与 diff / 终端块（Issue #6）

### Added

- `feature-tasks`（**新增模块**）：任务列表（运行中 / 需确认 / 完成 / 没成 四组）+ 任务详情页。
  - 详情页 = 头卡 + 过滤 chips（全部 / 文件 / 终端 / 网页 / 屏幕）+ 安静 ↔ 啰嗦 滑杆 +
    **单一时间线**为骨，五件套内联（page-specs §2）。
  - 产物区「这次留下了什么」来自快照表 —— 只列文件名与时间，不编体积数。
  - 长任务每 20 条折叠一组「…还有 N 条」。
  - 整屏终端：从终端块右上 `⤢` 抬起来，默认滚到尾部（命令跑完要看的是结果）。
  - 操作条只放**现在真能按的**（停止 / 去聊天里看看）；重试 / 批准 / 接管明说「等执行器接进来」。
  - `TaskDao` / `TaskRunEntity` / `TaskStepEntity` / `SnapshotEntity` 都已就绪（上一轮），
    `TasksDeps` 是接口，`AppContainer` 内手工实现；feature 不依赖 app。
  - 「谁写 `task_runs`」仍未定 —— **现在没有产出会让这张表出现行**。蓝图里说任务可独立诞生
    （定时 / 分享）或由长对话转后台（page-specs §3「长任务出口」），两条都依赖执行器接进来。
- `core-design`：颜色 token 加 `terminalBg` / `terminalText` / `terminalMuted`（**永久暗面**，
  不随主题翻转 —— 终端的仪式感）。Hex 仅此新增，仍只有 `QuineColors.kt` 一处。
- `feature-chat`：顶栏加「任务」入口（紧挨「设置」），点击进任务面；返回 / 关掉都是 `popBackStack`。

### Changed

- 时间格式化器不再缓存到静态字段 —— locale / 时区是运行时可变的，钉住会改完语言不生效
  （lint `ConstantLocale`）。格式化器每次调用按当前 locale / zone 构建，开销可接受。

### 测试

- `feature-tasks`：JVM 单测 7 项（分组、取消、详情、坏 payload、块解析、状态文案）。
- 仪器测试 5 项（空态、分组渲染、详情头卡 + 时间线 + 产物、运行中有停止、已消失任务有提示）。
- 工具侧：`TextDiff` / `SnapshotRegistry` / `fs_write` / `fs_rollback` 之前一轮的单测与仪器测试均复用。

## [0.1.0] - 待发布

首个里程碑（M0）尚未打 tag；验收线是「能对话、看流式、补全一个工具调用并回显结果」。
