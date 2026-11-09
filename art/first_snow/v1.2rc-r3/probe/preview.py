#!/usr/bin/env python3
"""初雪刀镡造型探针（纯标准库，独立于 v1.1 交付流水线）。

回答一个问题：在半径 9.6 的**实心**镡盘里，能不能做出真正的六瓣雪花，
同时满足规格书三条硬约束：
  1) 小型、简洁的雪花/六角意象；
  2) 避免细小悬空尖刺（轮廓可以起伏，但不能有外伸细枝/小球）；
  3) 轮廓在物品栏缩小显示时仍然清晰。

产出 out/tsuba_compare.png：4 档轮廓正视图 + 各自的 16x16 缩小剪影。
这里画的是造型示意；轮廓函数与真实 OBJ 几何共用同一条公式。
"""
import math
from pathlib import Path

from png_util import save_png, text

HERE = Path(__file__).resolve().parent
OUT = HERE / "out"
OUT.mkdir(parents=True, exist_ok=True)

R = 9.6                      # 与 make_first_snow.py 的 TSUBA_R 一致
BG = (26, 36, 52)
INK = (214, 228, 242)
SUB = (140, 166, 192)


def hex_v11(th):
    """v1.1 现状：圆钝六角，起伏仅 14%，肉眼接近圆盘。"""
    return 0.93 + 0.07 * math.cos(6 * th)


def snow(th, depth, power):
    """六瓣雪瓣：瓣顶宽平、瓣间深 V 谷，全程连续实心、无悬空端点。

    s = 0..1 的六次平滑波；power<1 让瓣顶变宽、谷口收窄。
    depth 控制谷有多深，是「雪花感」的主旋钮。
    """
    s = 0.5 + 0.5 * math.cos(6 * th)
    return (1.0 - depth) + depth * (s ** power)


STYLES = [
    ("V1.1 ROUND HEX", hex_v11, "DEPTH 0.14 / NO SNOWFLAKE"),
    ("SNOW / SOFT", lambda th: snow(th, 0.20, 0.85), "DEPTH 0.20 / SUBTLE"),
    ("SNOW / STANDARD", lambda th: snow(th, 0.32, 0.70), "DEPTH 0.32 / RECOMMENDED"),
    ("SNOW / CONCEPT", lambda th: snow(th, 0.40, 0.60), "DEPTH 0.40 / NEAR REF 03"),
]

CW, W, H = 250, 1000, 400
PX = 92                       # 盘半径像素
CY = 186


def disc_color(r, th, edge):
    """冷银底 + 冰蓝嵌面 + 六向主枝与贴面侧枝 + 中心六角芯。"""
    # 到最近一条主枝轴的角距（0 = 瓣中心）
    d = abs(((th + math.pi / 6) % (math.pi / 3)) - math.pi / 6)
    b = int(205 - 55 * r)
    c = (b - 12, b + 2, b + 20)

    # 瓣内冰蓝珐琅嵌面：只在瓣中心附近，随轮廓收窄
    if 0.42 < r < 0.86 * edge and d < 0.135:
        c = (150, 190, 224)
    # 六道主枝（贴盘面浅浮雕，不悬空）
    if 0.16 < r < 0.90 * edge and d < 0.050:
        c = (238, 248, 255)
    # 每臂一对侧枝：从主枝斜出，角度随半径增大
    if 0.30 < r < 0.66 and abs(d - (0.10 + 0.62 * (r - 0.30))) < 0.045:
        c = (206, 232, 250)
    # 中心六角芯
    if r < 0.12:
        c = (236, 246, 255)
    elif r < 0.17:
        c = (110, 140, 170)
    # 外缘：暗描边 + 冷白亮边
    gap = edge - r
    if gap < 0.055:
        c = (88, 116, 146)
    if gap < 0.028:
        c = (232, 244, 255)
    return c


def main():
    buf = bytearray(BG * (W * H))
    for i, (name, prof, note) in enumerate(STYLES):
        cx = CW * i + CW // 2
        text(buf, W, H, cx - 84, 26, name, INK, 2)
        text(buf, W, H, cx - 96, 52, note, SUB, 1)

        # 正视图：逐像素按极坐标轮廓填充
        for py in range(CY - PX - 2, CY + PX + 3):
            for px in range(cx - PX - 2, cx + PX + 3):
                if not (0 <= px < W and 0 <= py < H):
                    continue
                nx, ny = (px - cx) / PX, (py - CY) / PX
                r = math.hypot(nx, ny)
                if r > 1.05:
                    continue
                th = math.atan2(ny, nx)
                edge = prof(th)
                if r > edge:
                    continue
                col = disc_color(r, th, edge)
                o = (py * W + px) * 4
                buf[o:o + 4] = bytes(col) + b"\xff"

        # 16x16 缩小剪影：把正视图按覆盖率降采样，再放大 4 倍
        icon = [[0.0] * 16 for _ in range(16)]
        for iy in range(16):
            for ix in range(16):
                hits = 0
                for sy in range(8):
                    for sx in range(8):
                        nx = ((ix + (sx + 0.5) / 8) / 16 * 2 - 1) * 1.04
                        ny = ((iy + (sy + 0.5) / 8) / 16 * 2 - 1) * 1.04
                        r = math.hypot(nx, ny)
                        edge = prof(math.atan2(ny, nx))
                        if r <= edge:
                            hits += 1
                icon[iy][ix] = hits / 64.0
        ox, oy, s = cx - 32, 300, 4
        for iy in range(16):
            for ix in range(16):
                v = icon[iy][ix]
                col = (26, 36, 52) if v <= 0 else (
                    int(70 + 160 * v), int(96 + 150 * v), int(122 + 130 * v))
                for dy in range(s):
                    for dx in range(s):
                        px, py = ox + ix * s + dx, oy + iy * s + dy
                        o = (py * W + px) * 4
                        buf[o:o + 4] = bytes(col) + b"\xff"
        text(buf, W, H, cx - 42, 372, "16 X 16", SUB, 1)

    p = OUT / "tsuba_compare.png"
    save_png(p, W, H, buf)
    print("wrote", p, p.stat().st_size, "B")
    for name, prof, _ in STYLES:
        rs = [prof(k * math.pi / 180) * R for k in range(360)]
        print("  %-17s r_min %.2f  r_max %.2f  depth %.0f%%"
              % (name, min(rs), max(rs), (1 - min(rs) / max(rs)) * 100))


if __name__ == "__main__":
    main()
