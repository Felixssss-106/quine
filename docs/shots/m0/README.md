# M0 截图

M0 在模拟器（AVD `AILifeTest`，Android 16 / API 36）上实跑后截取，
对应 `agent-prompt.md` §6 DoD 第 4 条。

| 文件 | 内容 |
|---|---|
| `onboarding-light.png` | 首启屏一（浅色）：大标题「你好。」+ 供应商 chips（DeepSeek 选中）+ 禁用的「继续」 |
| `onboarding-dark.png` | 首启屏一（深色）：同一屏，主题跟随系统；`ink` 翻转为亮块 |
| `chat-empty-light.png` | 聊天面空态：档位胶囊（标准选中）+ 空输入框 → 右钮为**语音态** |
| `chat-no-provider-light.png` | 未接模型时发送：错误条三段式（还没有接上模型 / 这条消息发不出去 / 去设置粘贴 Key）+ 重试 / 编辑 |
| `chat-streaming-light.png` | 流式中：等待首个内容时的**呼吸点**，右钮切到**停止态**（▪） |
| `chat-tool-call-light.png` | **M0 验收线**：用户气泡 → 流式吐字 → 活动卡「读了 notes/todo.md」→ 结果回灌后的 Markdown 列表 |
| `chat-landscape-light.png` | 横屏：单栏布局（双栏工程台属 M1 的工作区部分），草稿与历史在旋转后保持 |

## 尚未拍摄

- 设置 → 外观 → 应用图标的深浅切换（`activity-alias` 切换后启动器图标需要重启启动器才刷新）
- 桌面图标两版（`launcher-light.png` / `launcher-dark.png`）
- 真机（Android 14+）上的同一组截图
