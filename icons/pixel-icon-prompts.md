# Quine 图标 · 像素风 AI 生图提示词（>_ 方向）

> 2026-09-17 · 方向：用户选定「提示符 >_ + 品牌蓝」，要求：`>` 笔画加粗
> 色值：黑 #0D0D0D ｜ 蓝 #6495ED ｜ 白 #FFFFFF

## 一、主提示词（浅色版）

**English：**

```
Pixel art app icon, 16-bit style. Chunky square pixels, crisp sharp edges, no anti-aliasing, flat colors. White rounded-square (squircle) tile. Centered: a bold right-pointing chevron symbol ">" drawn with very thick blocky pixels (stroke weight equal to about a quarter of the glyph height), in near-black #0D0D0D; at the baseline below-right, a thick horizontal underscore bar in cornflower blue #6495ED. Minimal retro terminal aesthetic, high contrast, generous margins, centered composition. No gradients, no shadows, no gloss, no text. 1:1 square, pixel-perfect.
```

**中文（即梦 / 可灵 / 星流等）：**

```
像素风 App 图标，16-bit 像素艺术；方块像素、边缘锐利、无抗锯齿、纯色平涂。白色圆角方块底；画面正中一个很粗的黑色右向尖括号「>」（笔画要粗：厚度约为字形高度的四分之一，由大方块像素构成），右下角基线位置一条很粗的横线下划线，矢车菊蓝 #6495ED。极简复古终端风、高对比、留白充足、居中构图；无渐变、无阴影、无光泽、无文字。1:1 方形，像素级锐利。
```

## 二、变体（替换主提示词中的相应句子）

1. **深色版**：`on a black rounded-square tile (#0D0D0D): the chevron in white, the underscore bar in cornflower blue #6495ED`
   （中文：黑色圆角方块底 #0D0D0D；尖括号为白色，下划线为矢车菊蓝 #6495ED）
2. **纯黑白版**：`fully monochrome: near-black chevron and underscore on a white tile, no accent color`
   （中文：纯黑白：白底黑色尖括号+黑色下划线，不加彩色）
3. **极粗版**：`make the chevron extremely bold — heavier than any font weight, like a blocky logo mark`
   （中文：尖括号做到极粗——比任何字重都重，像块状 logo）

## 三、负向提示词

**EN：** `anti-aliasing, blurry edges, soft focus, gradients, drop shadows, 3D render, glossy highlights, glow, thin strokes, rounded stroke ends, text, letters, numbers, watermark, extra symbols, noise, grain, perspective, mockup`

**中文：** `抗锯齿、边缘模糊、柔焦、渐变、投影、3D 渲染、高光、发光、细线条、圆头笔触、文字、字母、数字、水印、多余符号、噪点、颗粒、透视、样机`

## 四、使用贴士

- **Midjourney**：`--ar 1:1 --style raw --stylize 0`；**像素专门模型更佳**：Retro Diffusion、SD 像素风 LoRA；**GPT-image / 即梦** 用中文长句即可
- **想要"真像素"**：AI 出图常带糊边——生成后先缩到 32×32（最近邻），再放大回 1024（最近邻），立刻干净
- 多摇几张挑最正的一张
- 生成完发回来：我按结果**复刻精确像素稿 / 矢量版 + 自适应图标分层**（上架用）
