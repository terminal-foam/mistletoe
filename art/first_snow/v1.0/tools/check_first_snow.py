#!/usr/bin/env python3
"""初雪 first_snow 美术资源验收（离线，纯标准库）。

对应 docs/初雪-美术资源交付与接入清单.md 第 3/4/7 节中可用静态手段验证的部分：
  1. 13 个分组、组名、索引/UV 越界（check_ingame.py 另跑一遍做穿模/朝向检查）
  2. 与基准 dawn.obj 的几何血缘：只允许 blade 组镡段与 item_* 图标镡有差异
  3. 16 格图集全覆盖，特别是旧 first_snow.png 里纯白的 5 格
  4. 配色禁令：无大面积暖金 / 深绿
  5. 图标三态的坐标跨度、Z 层次与 +Z 面朝向
  6. 生成 16x16 缩略图对照（清单第 88 行要求的小尺寸辨识度参考）

用法：python check_first_snow.py
"""
from __future__ import annotations

import math
from collections import Counter, defaultdict
from pathlib import Path

from render_jar013 import decode_png, save_png, text

HERE = Path(__file__).resolve().parent
BASE = Path("E:/dsh/terminalblade/src/main/resources/assets/mistletoe/model/named/dawn.obj")
OBJ = HERE / "first_snow" / "first_snow.obj"
PNG = HERE / "first_snow" / "first_snow.png"
ICON = HERE / "first_snow" / "demo_icon.png"
OUT = HERE / "first_snow" / "icon16_preview.png"

GROUPS13 = [
    "blade", "blade_luminous", "blade_damaged", "blade_damaged_luminous",
    "sheath", "sheath_luminous", "effect",
    "item_blade", "item_blade_luminous",
    "item_damaged", "item_damaged_luminous",
    "item_bladens", "item_bladens_luminous",
]
TILE_NAMES = [
    ["blade", "blade_luminous", "handle", "tsuba"],
    ["sheath", "sheath_luminous", "leaf", "berry"],
    ["effect", "p_leaf", "p_berry", "emblem"],
    ["slash_ring", "slash_sphere", "branch", "habaki"],
]
WAS_BLANK = {"blade_luminous", "effect", "p_berry", "slash_ring", "slash_sphere"}

fails, warns = [], []


def load_obj(path):
    V, VT, F, VT_USE = [], [], [], []
    cur = None
    order = []
    for raw in path.read_text(encoding="utf-8").splitlines():
        s = raw.strip()
        if s.startswith("v "):
            a = s.split()
            V.append(tuple(round(float(x), 3) for x in a[1:4]))
        elif s.startswith("vt "):
            a = s.split()
            VT.append(tuple(round(float(x), 5) for x in a[1:3]))
        elif s.startswith("g "):
            cur = s[2:].strip()
            if cur not in order:
                order.append(cur)
        elif s.startswith("f ") and cur:
            toks = [t.split("/") for t in s.split()[1:]]
            vi = [int(t[0]) - 1 for t in toks]
            ti = [int(t[1]) - 1 for t in toks if len(t) > 1 and t[1]]
            VT_USE.extend(ti)
            pts = tuple(V[i] for i in vi)
            uvs = tuple(VT[i] for i in ti)
            F.append((cur, pts, uvs, vi, ti))
    return V, VT, F, VT_USE, order


print("=" * 78)
print("初雪 first_snow 离线验收")
print("=" * 78)

vb, vtb, fb, ub, ob = load_obj(BASE)
vs, vts, fs, us, os_ = load_obj(OBJ)
print("[1] 文件与分组")
print("    基准 dawn.obj      v=%d vt=%d f=%d 组=%d" % (len(vb), len(vtb), len(fb), len(ob)))
print("    初雪 first_snow.obj v=%d vt=%d f=%d 组=%d" % (len(vs), len(vts), len(fs), len(os_)))
if sorted(os_) != sorted(GROUPS13):
    fails.append("输出组不是清单要求的 13 组: %s" % sorted(os_))
else:
    print("    13 组名称/数量: OK")
if sorted(ob) != sorted(os_):
    fails.append("与 dawn.obj 的组名集合不一致")
else:
    print("    与 dawn.obj 组名集合一致: OK")

bad_idx = [i for i, (_, _, _, vi, ti) in enumerate(fs)
           if any(x < 0 or x >= len(vs) for x in vi) or any(x < 0 or x >= len(vts) for x in ti)]
bad_uv = [(u, v) for u, v in vts if not (0.0 <= u <= 1.0 and 0.0 <= v <= 1.0)]
print("    索引越界 %d 处 / UV 越界 %d 处" % (len(bad_idx), len(bad_uv)))
if bad_idx or bad_uv:
    fails.append("索引或 UV 越界")

# ---------------------------------------------------------------- 2. 几何血缘
print("\n[2] 几何血缘：只允许镡段与图标镡改动")
sig_a = Counter((g, p, uv) for g, p, uv, _, _ in fb)
sig_s = Counter((g, p, uv) for g, p, uv, _, _ in fs)
removed = sig_a - sig_s
added = sig_s - sig_a
print("    dawn 独有三角形 %d / 初雪新增三角形 %d" % (sum(removed.values()), sum(added.values())))
allowed, illegal = 0, []
for (g, pts, uvs), n in list(removed.items()) + list(added.items()):
    xs = [p[0] for p in pts]
    zs = [p[2] for p in pts]
    if g.startswith("item_"):
        allowed += n
    elif g == "blade" and all(-16.6 <= x <= -10.4 for x in xs) and max(zs) <= 15.1:
        allowed += n
    else:
        illegal.append((g, [round(x, 1) for x in xs]))
print("    落在允许区（刀镡 x∈[-16.6,-10.4] / item_* 图标）: %d" % allowed)
print("    落在禁止区（刀身 / 鞘 / 发光层 / effect）: %d" % len(illegal))
for g, xs in illegal[:6]:
    print("      ! %s x=%s" % (g, xs))
if illegal:
    fails.append("有 %d 个三角形在非镡区域被改动" % len(illegal))

# 三态图标的 Z 层次与跨度（清单第 70 行）
print("\n[3] 图标三态")
for g in ("item_blade", "item_bladens", "item_damaged"):
    pts = [p for gg, pp, _, _, _ in fs if gg == g for p in pp]
    if not pts:
        fails.append("%s 组为空" % g)
        continue
    span = max(max(p[0] for p in pts) - min(p[0] for p in pts),
               max(p[1] for p in pts) - min(p[1] for p in pts))
    z0, z1 = min(p[2] for p in pts), max(p[2] for p in pts)
    print("    %-16s 跨度 %6.1f  z[%.1f, %.1f]" % (g, span, z0, z1))
    if not (140 <= span <= 160 and 1.0 <= z0 <= 5.0 and z1 <= 5.0):
        fails.append("%s 跨度/Z 层次偏离基准" % g)

# ---------------------------------------------------------------- 4. 图集 16 格
print("\n[4] 图集 16 格覆盖（1024² / 每格 256²）")
w, h, rows = decode_png(PNG.read_bytes())
print("    尺寸 %dx%d  %d 字节" % (w, h, PNG.stat().st_size))
if (w, h) != (1024, 1024):
    fails.append("图集不是 1024x1024")
tile_stat = {}
for r in range(4):
    cells = []
    for c in range(4):
        ink = tot = 0
        cols = set()
        alphas = set()
        alpha_drawn = 0
        for y in range(r * 256, (r + 1) * 256, 4):
            row = rows[y]
            for x in range(c * 256, (c + 1) * 256, 4):
                o = x * 4
                px = (row[o], row[o + 1], row[o + 2], row[o + 3])
                tot += 1
                cols.add(px[:3])
                alphas.add(px[3])
                if px[3] > 8:
                    alpha_drawn += 1
                    if not (px[0] > 248 and px[1] > 248 and px[2] > 248):
                        ink += 1
        name = TILE_NAMES[r][c]
        # 粒子 / 发光格是「RGB 恒定 + alpha 遮罩」，颜色数为 1 属正常；
        # 判定要看「可见区域占比」和「alpha 是否有层次」，不能只看颜色数。
        tile_stat[name] = (100 * alpha_drawn / tot, len(cols), len(alphas))
        cells.append("%-17s ink%5.1f%% c%4d a%3d" %
                     (name, 100 * ink / tot, len(cols), len(alphas)))
    print("    " + " | ".join(cells[:2]))
    print("    " + " | ".join(cells[2:]))
for name in sorted(WAS_BLANK):
    filled, colors, alphas = tile_stat[name]
    print("    曾空白格 %-16s 可见区域 %5.1f%%  颜色数 %3d  alpha 层次 %3d" %
          (name, filled, colors, alphas))
    if filled < 5 or alphas < 3:
        fails.append("%s 格仍是空白/无渐变，未按清单补齐" % name)

# ---------------------------------------------------------------- 5. 配色禁令
print("\n[5] 配色禁令（大面积暖金 / 深绿）")
warm = green = total = 0
for y in range(0, h, 4):
    row = rows[y]
    for x in range(0, w, 4):
        o = x * 4
        if row[o + 3] < 8:
            continue
        r, g, b = row[o], row[o + 1], row[o + 2]
        total += 1
        if r > g + 18 and g > b + 10:
            warm += 1
        if g > r + 22 and g > b + 18:
            green += 1
print("    暖金/黄 %.3f%%   饱和绿 %.3f%%  (采样 %d 像素)" %
      (100 * warm / total, 100 * green / total, total))
if 100 * warm / total > 1.0 or 100 * green / total > 1.0:
    fails.append("图集仍有大面积暖金或深绿")

# ---------------------------------------------------------------- 6. 16x16 缩略图
print("\n[6] 16x16 缩略图对照（清单第 88 行）")
iw, ih, irows = decode_png(ICON.read_bytes())


def box16(ox, oy, n=16, size=168):
    out = []
    for j in range(n):
        line = []
        for i in range(n):
            x0, x1 = ox + int(i * size / n), ox + max(int(i * size / n) + 1, int((i + 1) * size / n))
            y0, y1 = oy + int(j * size / n), oy + max(int(j * size / n) + 1, int((j + 1) * size / n))
            rs = gs = bs = cnt = 0
            for yy in range(y0, min(y1, oy + size)):
                row = irows[yy]
                for xx in range(x0, min(x1, ox + size)):
                    o = xx * 4
                    rs += row[o]; gs += row[o + 1]; bs += row[o + 2]; cnt += 1
            line.append((rs // cnt, gs // cnt, bs // cnt))
        out.append(line)
    return out


PAD, PW, BIG, SCALE = 16, 168, 16 * 12, 12
W = PAD * 3 + PW + BIG
H = 44 + 2 * (BIG + 30)
buf = bytearray(bytes((232, 236, 242, 255)) * (W * H))
INK = (40, 56, 76)
text(buf, W, H, PAD, 12, "FIRST SNOW  /  16X16 CHECK", INK, 2)
panes = [(18, 18, "SHEATHED"), (18 + 168 + 18, 18, "BROKEN")]  # demo_icon.png 的两格
for k, (ox, oy, label) in enumerate(panes):
    ydst = 44 + k * (BIG + 30)
    # 原尺寸面板
    for j in range(PW):
        row = irows[oy + j]
        o = (ydst + j) * W * 4 + PAD * 4
        buf[o:o + PW * 4] = row[ox * 4:(ox + PW) * 4]
    # 16x16 放大面板
    small = box16(ox, oy)
    xoff = PAD * 2 + PW
    for j in range(16):
        for i in range(16):
            c = small[j][i]
            px = bytes((c[0], c[1], c[2], 255))
            for dy in range(SCALE):
                o = ((ydst + j * SCALE + dy) * W + xoff + i * SCALE) * 4
                for dx in range(SCALE):
                    oo = o + dx * 4
                    buf[oo:oo + 4] = px
    text(buf, W, H, PAD, ydst + BIG + 4, label + "  ORIGINAL", INK, 1)
    text(buf, W, H, xoff, ydst + BIG + 4, label + "  16X16", INK, 1)
save_png(OUT, W, H, buf)

# ---------------------------------------------------------------- 汇总
print("\n" + "=" * 78)
if fails:
    print("FAIL %d 项" % len(fails))
    for f in fails:
        print("  -", f)
    raise SystemExit(1)
print("PASS  13 组 / 几何血缘 / 16 格覆盖 / 配色禁令 / 图标三态全部通过")
if warns:
    for w_ in warns:
        print("  WARN", w_)
print("\n注意：本体耐久菱形层（GUI 才有）不受主图集控制，见清单第 84 行；")
print("      16x16 最终辨识度、不同 GUI 缩放与真实游戏截图仍需 runClient 人工验收。")
