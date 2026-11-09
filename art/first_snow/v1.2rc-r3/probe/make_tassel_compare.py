#!/usr/bin/env python3
"""r2（只有贴面飘带示意）vs r3（立体流苏 + 冰珠）局部放大对照。

两张整刀预览都是同一视角 / 同一 SCALE，取绑绳处同一块像素窗口放大，
避免「并排两张整刀图根本看不出区别」。
"""
from __future__ import annotations

import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from png_util import decode_png, save_png, text  # noqa: E402

BEFORE = HERE / "out" / "r2_no_tassel.png"
AFTER = HERE / "pipeline_snow" / "out" / "demo_render_sheathed.png"
DEST = HERE / "out" / "tassel_compare.png"

# 挂点在整刀预览里约 (345, 114)，流苏垂到 y≈185
OX, OY, CW, CH, S = 292, 86, 132, 132, 3
PW, PH = CW * S, CH * S
PAD, TOP = 16, 40
W = PAD * 3 + PW * 2
H = TOP + PH + 30
BG = (232, 236, 242, 255)
INK = (40, 56, 76)


def crop_scale(path):
    w, h, rows = decode_png(Path(path).read_bytes())
    block = []
    for j in range(CH):
        row = rows[OY + j]
        line = bytearray()
        for i in range(CW):
            line.extend(row[(OX + i) * 4:(OX + i + 1) * 4] * S)
        for _ in range(S):
            block.append(bytes(line))
    return block


def blit(buf, block, x, y):
    for j, line in enumerate(block):
        off = ((y + j) * W + x) * 4
        buf[off:off + len(line)] = line


buf = bytearray(bytes(BG) * (W * H))
text(buf, W, H, PAD, 12, "FIRST SNOW / TASSEL: FLAT DECAL  ->  3D GEOMETRY", INK, 2)
for k, (path, label) in enumerate((
        (BEFORE, "R2  /  FLAT DECAL ONLY"),
        (AFTER, "R3  /  3D TASSEL + BEADS"))):
    x = PAD + k * (PW + PAD)
    blit(buf, crop_scale(path), x, TOP)
    text(buf, W, H, x, TOP + PH + 8, label, INK, 2)

save_png(DEST, W, H, buf)
print("wrote", DEST, DEST.stat().st_size, "B", W, "x", H)
