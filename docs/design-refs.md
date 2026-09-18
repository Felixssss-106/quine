# 设计资源短名单（UI 库 / 动效库 / 灵感站）

> 2026-09-15 · 为「安卓 Agent 工具」挑选 · 状态：待过目
> 筛选标准：①能用在 Compose/Android（或对设计有直接参考价值）②维护活跃（附 ★ 与更新时间）③气质贴「双面人：竖屏亲和 × 横屏工程台 + 深色签名」

## 1. 设计系统
> 结论：没有成熟的第三方 Compose 设计系统可依赖——基础用官方的，双面人语言自研。

- **Material 3 Expressive** — 官方下一代基调（新组件 / 形状系统 / 弹簧动效）https://m3.material.io
- **Now in Android** — Google 官方示范 app（★21815，持续活跃）https://github.com/android/nowinandroid
- **Compose Samples** — 官方样例合集（★23461）https://github.com/android/compose-samples
- 仅参考：kiwi.com Orbit（★183，2025-05 后低活跃）、quack-quack（★117）

## 2. 动效库
- **Rive** — 状态机动画：一个文件含"待机/呼吸/工作/完成/出错"多状态，最适合"agent 活着"的拟人化表达 https://rive.app（Android 库 ★537，2026-09 仍高频更新）
- **Lottie** — 矢量微动效播放（★35722）+ 素材市场 https://lottiefiles.com
- **material-motion-compose** — Material Motion 转场三件套（shared axis / fade through / container transform）（★662）https://github.com/fornewid/material-motion-compose
- **compose-shimmer** — 骨架屏加载态（★1081）
- **Konfetti** — 完成庆祝彩纸（★3390，克制使用）
- **koreography** — 动画编排 DSL（把多段动画"编舞"，★219）
- 官方能力：Compose 动画指南 https://developer.android.com/develop/ui/compose/animation （含 SharedTransitionLayout 共享元素）
- 备用：compottie（多平台 Lottie 重写版，★682）

## 3. 场景库（界面会直接用）
- **sora-editor** — Android 代码编辑器（文件编辑 / diff 展示底子，★1424，活跃）
- **multiplatform-markdown-renderer**（★1072）— LLM 消息 Markdown 渲染
- **compose-richtext**（★990）— 富文本 / 代码块
- **Vico**（★3173）— 图表（token 消耗、任务统计）
- **Reorderable**（★1347）— 拖拽排序（任务列表 / 面板）
- **Balloon**（★4008）— 气泡提示（讲解式 UI）
- **Haze**（★2527，更新频繁）— 毛玻璃 / 背景模糊（现代分层感）

## 4. 灵感网站
- 真机界面：**Mobbin**、**Refero**（refero.design，已支持 MCP）、**Page Flows**
- 动效：**AppMotion**（appmotion.design）、Dribbble Motion 标签、LottieFiles 浏览区、Rive Community
- 综合审美：Awwwards、Godly、Layers
- 国内：站酷、花瓣、UI 中国
- 工具型密度规范参考：GitHub Primer、Atlassian Design System、IBM Carbon、Shopify Polaris
- 气质对标（成品）：Warp / Termius / Blink（移动端子）、Linear / Raycast / Zed（现代工具）、ChatGPT / Claude App（对话面）
- 注：国际站点部分需要科学上网

## 5. 字体 / 图标
- 等宽：**Maple Mono**（★28.9k，圆角 + 连字 + 中文支持，首推）、JetBrains Mono、Geist Mono
- 图标：**Lucide**（★24.5k）、Material Symbols

## 先看这 5 个
1. m3.material.io — 定基调
2. rive.app — 人格化动效
3. material-motion-compose — 转场规范
4. Mobbin / Refero — 大量看真机，找「双面人」的答案
5. Now in Android — 看动效在真实代码里的样子
