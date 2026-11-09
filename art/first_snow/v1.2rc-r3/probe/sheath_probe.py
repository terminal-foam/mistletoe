#!/usr/bin/env python3
"""从 1024² 图集裁出 sheath / sheath_luminous 分区并放大，便于核对纹样。"""
import sys
from pathlib import Path

from png_util import decode_png, save_png, text

HERE = Path(__file__).resolve().parent
OUT = HERE / "out"
OUT.mkdir(parents=True, exist_ok=True)


def crop_tile(atlas_path, col, row, scale, dst, label):
    w, h, rows = decode_png(Path(atlas_path).read_bytes())
    assert w == 1024 and h == 1024, (w, h)
    x0, y0, T = col * 256, row * 256, 256
    W = H = T * scale
    buf = bytearray(bytes((26, 36, 52, 255)) * (W * H))
    for y in range(T):
        line = rows[y0 + y][x0 * 4:(x0 + T) * 4]
        big = bytearray()
        for x in range(T):
            big += line[x * 4:x * 4 + 4] * scale
        for _ in range(scale):
            o = (y * scale + _) * W * 4
            buf[o:o + W * 4] = big
    save_png(Path(dst), W, H, buf)
    print("wrote", dst, Path(dst).stat().st_size, "B")
    return buf, W, H


if __name__ == "__main__":
    atlas = sys.argv[1] if len(sys.argv) > 1 else str(
        HERE / "pipeline_snow" / "out" / "demo_atlas.png")
    tag = sys.argv[2] if len(sys.argv) > 2 else "before"
    crop_tile(atlas, 0, 1, 3, OUT / ("sheath_tile_%s.png" % tag), tag)
