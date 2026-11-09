#!/usr/bin/env python3
"""把 v1.1 圆钝六角镡与雪花镡候选版的同视角 3D 特写并排合成对照图。"""
from pathlib import Path

from png_util import decode_png, save_png, text

HERE = Path(__file__).resolve().parent
OUT = HERE / "out"
OUT.mkdir(parents=True, exist_ok=True)

PAIRS = [
    (HERE / "pipeline_v11" / "out" / "demo_tsuba_closeup.png",
     "V1.1  ROUND HEX", "DEPTH 14% / LOOKS LIKE A DISC"),
    (HERE / "pipeline_snow" / "out" / "demo_tsuba_closeup.png",
     "SNOW  / STANDARD", "DEPTH 32% / SIX PETALS + V NOTCHES"),
]

BG = (238, 242, 247, 255)
INK = (40, 60, 82, 255)
SUB = (110, 135, 160, 255)
W, H = 840, 480
buf = bytearray(bytes(BG) * (W * H))

for i, (path, label, note) in enumerate(PAIRS):
    w, h, rows = decode_png(path.read_bytes())
    assert w == 400 and h == 400, (path, w, h)
    x0, y0 = 20 + i * 410, 56
    for y in range(h):
        o = ((y0 + y) * W + x0) * 4
        buf[o:o + w * 4] = rows[y]
    text(buf, W, H, x0 + 6, 20, label, INK, 2)
    text(buf, W, H, x0 + 6, 40, note, SUB, 1)

text(buf, W, H, 20, 462,
     "SAME ORTHO VIEW / SAME SCALE / SAME ATLAS UV / R 9.6 / NO FLOATING PARTS",
     SUB, 1)

p = OUT / "tsuba_closeup_compare.png"
save_png(p, W, H, buf)
print("wrote", p, p.stat().st_size, "B")
