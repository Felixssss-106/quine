# Quine

> 一个安卓端的 Agent 工具：会改文件、会上网查、会操控系统、会跑命令、会在你不在的时候把活干完。

**一句话**：把 Codex / Claude Code 的能力搬进 Android 手机，并且它长得好看。

- 平台：Android 独占（手机 / 平板 / 折叠屏，竖横双形态精做）
- 运行时：纯本地起步（proot Linux 沙箱内置于 App），预留混合后端
- 模型：多供应商 BYO key（OpenAI 兼容为主轴 + Anthropic / Gemini 原生适配）
- 形态：聊天 + 任务双主面；竖屏亲和对话、横屏工程台
- 源代码：https://github.com/Felixssss-106/quine （公开仓库）

---

## 当前状态

**M0 骨架已落地**：能对话、看流式、补全一个工具调用并回显结果。

| 部分 | 状态 |
|---|---|
| `core-common` · `core-gateway` · `core-tools` · `core-loop` · `core-storage` · `core-design` | ✅ 已实现，核心逻辑有单测 |
| `feature-onboarding` · `feature-chat` · `feature-settings` · `app` | ✅ 三个界面 + 导航 + 手工装配 |
| proot 沙箱 · 任务面 · 搜索 / WebView · Shizuku · 自动化 | ⏳ M1–M5 |

### 怎么构建

```bash
# JDK 21（Android Studio 自带 JBR 即可）
export JAVA_HOME=/path/to/jbr

./gradlew assembleDebug          # 出包
./gradlew lint testDebugUnitTest # 质量门
```

`local.properties` 里指向本机 Android SDK；该文件不入版本库。

### 第一次跑起来

1. 装包后首启屏会让你选一个供应商并粘贴 API Key（Key 只进 Keystore，不进日志）。
2. 接着选一个目录作为工作区；不选也能用，会回落到应用私有工作区（有明确说明，不静默）。
3. 进聊天面，让它读一个文件试试 —— 工具调用会以「活动卡」出现在消息流里。

---

## 怎么用（给接过这个项目的人和 Agent）

1. **先读 `agent-prompt.md`** —— 这是项目启动简报（v2），包含角色约定、技术架构、
   功能规格、验收标准、里程碑与工程约定。它是给 Agent 的**主指令**。
2. **再读 `docs/` 全套** —— 设计文档集是**事实来源**；提示词只是转述。
   两者冲突时以 `docs/` 为准；两者都没覆盖的，问人，不要自行发明。
3. **动手前先对齐** —— 产出《理解与计划》（仓库结构与模块图 / 里程碑拆解 / 问题清单），
   确认后再写代码。M0 的计划见 `docs/plans/m0.md`。

---

## 文件索引

| 路径 | 是什么 | 什么时候读 |
|---|---|---|
| `agent-prompt.md` | **开发启动提示词 v2**：给实现 Agent 的主简报（24.5KB） | 第一个读，通读 |
| `docs/v1-blueprint.md` | 总蓝图：定位 / 能力边界 / 技术选型 / 交互 / 美术适配 / 里程碑 / 风险 / 待定项 | 需要"为什么这么定"时 |
| `docs/visual-spec.md` | 视觉规格：色板 token、字阶、圆角与网格、组件清单、双主题检查表 | 写任何 UI 前 |
| `docs/motion-spec.md` | 动效规格：时长/弹簧令牌、10 组关键转场、无跳变验收、Compose API 映射 | 写任何动画前 |
| `docs/page-specs.md` | 页面规格：首启两屏 / 任务详情页 / composer / 设置·应用图标（逐行文字规格） | 实现具体页面时 |
| `docs/design-refs.md` | 设计资源选型：库清单与淘汰理由、灵感站、字体图标 | 挑第三方库时 |
| `icons/pixel-final/` | 图标资产：全尺寸双版本 + 自适应图层 + 切换实现说明 | 打包 App / 做设置页时 |
| `icons/pixel-icon-prompts.md` | 图标生图提示词存档（造型来源，非交付物） | 想改图标造型时 |

---

## 三条不能踩的线

1. **先对齐，再动工**：产出《理解与计划》并获人类确认前，不写业务代码。
2. **状态永不丢**：竖横切换、旋转、后台、进程重启，任务与对话状态必须原样恢复。
3. **用户数据不出设备**：文件、命令、模型的敏感内容默认本地处理；联网只走用户配置的模型与搜索后端。

---

## 尚未拍板的开放问题

见 `agent-prompt.md` 附录 B。已拍板：minSdk 29、MIT、`com.quine.app`、公开开发。
仍待定：沙箱发行版（M1 前）、默认搜索后端（M2 前）、中文字体配对（M5 前）、
Rive 资产路径（M4 前）、图标第三选项（M5 前）。

---

*设计文档版本：2026-09-17 · 文档集 v0.1，启动提示词 v2 · M0 骨架 2026-09-18*
