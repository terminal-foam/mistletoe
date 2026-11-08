#!/usr/bin/env python3
"""生成霜刃刀光预置贴图 wiring/frost_slash.png（32x32，纯标准库）。

【未绑定素材】本贴图只是「图形就绪」，运行时没有任何代码加载它，
见 wiring/FrostSlashBinding.java 的 TODO 清单与 out/manifest.json。

形状依据（反编译实测）：
  * DriveRenderer 用 slashblade:model/util/ss.png，尺寸 32x32；
  * drive.obj 只有 12 个 vt：u ∈ [0.069, 0.675]，v_obj ∈ [0.304, 0.604]；
  * WavefrontObject.parseTextureCoordinate 做 v = 1 - v_obj，
    两次翻转抵消后，图像行落在 y/H ∈ [0.304, 0.604]。
因此有效绘制窗口约为 x ∈ [2, 22]、y ∈ [10, 20]（32x32）。
窗口外保持透明，避免被 GL_REPEAT 或误采样拖出杂色。

刀光画成一道由粗到细的冷白→冰蓝月牙，两端淡出；颜色会被
BladeRenderState.setCol(0xDDEFFF | alpha) 再乘一次，所以底色保持近白。
"""
from __future__ import annotations

import math
from pathlib import Path

from png_util import save_png

HERE = Path(__file__).resolve().parent
OUT = HERE / "wiring" / "frost_slash.png"
W = H = 32

# drive.obj 有效 UV 窗口
U0, U1 = 0.069, 0.675
V0, V1 = 0.304, 0.604

buf = bytearray(W * H * 4)
for y in range(H):
    v = (y + 0.5) / H
    for x in range(W):
        u = (x + 0.5) / W
        # 归一化到窗口内
        if not (U0 <= u <= U1 and V0 <= v <= V1):
            continue
        s = (u - U0) / (U1 - U0)          # 0..1 沿刀光长度
        t = (v - V0) / (V1 - V0)          # 0..1 横向
        # 月牙：中心脊随 s 弯曲，宽度两头收
        ridge = 0.52 + 0.20 * math.sin(math.pi * s)
        half = 0.10 + 0.26 * math.sin(math.pi * s) ** 0.7
        d = abs(t - ridge) / max(half, 1e-6)
        if d > 1.0:
            continue
        core = (1.0 - d) ** 1.3
        fade = min(1.0, s * 6.0, (1.0 - s) * 6.0)
        a = int(255 * core * fade)
        if a <= 0:
            continue
        # 刃口偏白、外缘偏冰蓝，带一条高亮芯
        r = int(214 + 38 * core)
        g = int(234 + 20 * core)
        b = 255
        o = (y * W + x) * 4
        buf[o:o + 4] = bytes((r, g, b, a))

save_png(OUT, W, H, buf)
print("  %s  %d B  UV窗口 u[%.3f,%.3f] v[%.3f,%.3f]" %
      (OUT.name, OUT.stat().st_size, U0, U1, V0, V1))
print("  状态：NOT WIRED —— 需 wiring/FrostSlashBinding.java 落定后方可生效")
