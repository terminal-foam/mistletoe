#!/usr/bin/env python3
"""刀鞘纹样 before/after 对照：v1.1 继承的满装饰鞘 vs v1.2rc 初雪专属克制鞘。"""
from pathlib import Path

from png_util import decode_png, save_png, text

HERE = Path(__file__).resolve().parent
OUT = HERE / "out"

PAIRS = [
    (OUT / "sheath_tile_v11.png", "V1.1  INHERITED SAYA",
     "5 HOOPS / FULL WRAP / FULL KNOT / 4 SPRIGS / NO BLANK"),
    (OUT / "sheath_tile_after.png", "V1.2RC  FIRST SNOW SAYA",
     "3 HOOPS / ONE FROSTED SPRIG / LOCAL BOW / LACQUER BLANK"),
]

W, H = 1620, 848
BG = (238, 242, 247, 255)
INK = (40, 60, 82, 255)
SUB = (110, 135, 160, 255)
buf = bytearray(bytes(BG) * (W * H))

for i, (path, label, note) in enumerate(PAIRS):
    w, h, rows = decode_png(path.read_bytes())
    assert w == 768 and h == 768
    x0, y0 = 20 + i * 800, 60
    for y in range(h):
        o = ((y0 + y) * W + x0) * 4
        buf[o:o + w * 4] = rows[y]
    text(buf, W, H, x0, 16, label, INK, 2)
    text(buf, W, H, x0, 38, note, SUB, 1)

text(buf, W, H, 20, 832,
     "256PX TILE / X = ALONG SAYA / Y = AROUND SECTION / SPEC 2.2: SPARSE, FINE-LINED, NOT FULL-LENGTH",
     SUB, 1)

p = OUT / "sheath_compare.png"
save_png(p, W, H, buf)
print("wrote", p, p.stat().st_size, "B")
