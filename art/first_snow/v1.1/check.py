#!/usr/bin/env python3
"""初雪 first_snow 离线验收（自包含，纯标准库）。

相对 v1.0 的变化（对应评审意见）：
  * 基准 dawn.obj 从硬编码 E:/dsh/... 改为包内 ref/dawn.obj，
    可用 --baseline <path> 覆盖；基准不存在时血缘检查降级为 WARN 而非崩溃。
  * 几何血缘规则改为「刀身 x<-16.6 逐三角形零改动」，镡/柄/装饰带
    允许按规格收敛 —— v1.1 有意删除悬空枝叶白果。
  * 新增 [3] 造型收敛专项：镡盘半径、悬空装饰面数、图标细节面数。
  * 新增 [4] 16x16 辨识度**量化**：两态差异像素比、主体/背板明度差，
    不再只出一张让人自己看的缩略图。
  * 输出 atlas_usage.json，逐格标注 OBJ 是否真的采样，供 manifest 判定绑定状态。

用法：
    python check.py
    python check.py --baseline /path/to/dawn.obj
"""
from __future__ import annotations

import argparse
import json
import math
from collections import Counter, defaultdict
from pathlib import Path

from png_util import decode_png, save_png, text

HERE = Path(__file__).resolve().parent
OUT = HERE / "out"
OUT.mkdir(parents=True, exist_ok=True)
BASE = HERE / "ref" / "dawn.obj"
OBJ = OUT / "first_snow.obj"
PNG = OUT / "first_snow.png"
ICON = OUT / "demo_icon.png"
OUT16 = OUT / "icon16_preview.png"
USAGE = OUT / "atlas_usage.json"

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

# 运行时绑定状态（与图集是否画了内容无关，见 manifest.json）
BINDING = {
    "blade":            ("ACTIVE", "blade / blade_damaged / item_* 实体组"),
    "blade_luminous":   ("ACTIVE", "*_luminous 加色层"),
    "handle":           ("ACTIVE", "柄卷 / item_* 柄"),
    "tsuba":            ("ACTIVE", "blade 组镡盘 / item_* 镡"),
    "sheath":           ("ACTIVE", "sheath 组 + item_* 鞘"),
    "sheath_luminous":  ("ACTIVE", "sheath_luminous / item_*_luminous"),
    "leaf":             ("DESIGN_ONLY", "贴图纹样；v1.1 已无悬空叶几何"),
    "berry":            ("DESIGN_ONLY", "贴图纹样；v1.1 已无白果几何"),
    "effect":           ("CONDITIONAL", "effect 组；本体仅右键 isCharged() 触发，Super SA 不触发"),
    "p_leaf":           ("NOT_WIRED", "粒子贴图走独立 particle JSON，主图集此格无加载入口"),
    "p_berry":          ("NOT_WIRED", "同上"),
    "emblem":           ("ACTIVE", "item_* 菱形背板（仅 SHEATHED）"),
    "slash_ring":       ("NOT_WIRED", "霜刃走 DriveRenderer 写死的 slashblade:model/util/ss.png"),
    "slash_sphere":     ("NOT_WIRED", "同上"),
    "branch":           ("ACTIVE", "item_* 描边 / BROKEN 背板 / 断面"),
    "habaki":           ("ACTIVE", "鎺 / 鞘尻 / 断口"),
}

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


def lum(px):
    return 0.2126 * px[0] + 0.7152 * px[1] + 0.0722 * px[2]


print("=" * 78)
print("初雪 first_snow v1.1 离线验收（自包含流水线）")
print("=" * 78)

ap = argparse.ArgumentParser()
ap.add_argument("--baseline", default=None, help="几何血缘基准 dawn.obj 路径")
a = ap.parse_args()
base = Path(a.baseline) if a.baseline else BASE

vs, vts, fs, us, os_ = load_obj(OBJ)
print("[1] 文件与分组")
print("    目标 %s  v=%d vt=%d f=%d 组=%d" %
      (OBJ.name, len(vs), len(vts), len(fs), len(os_)))
print("    基准 %s  %s" % (base, "存在" if base.is_file() else "缺失"))
if sorted(os_) != sorted(GROUPS13):
    fails.append("输出组不是清单要求的 13 组: %s" % sorted(os_))
else:
    print("    13 组名称/数量: OK")

bad_idx = [i for i, (_, _, _, vi, ti) in enumerate(fs)
           if any(x < 0 or x >= len(vs) for x in vi) or any(x < 0 or x >= len(vts) for x in ti)]
bad_uv = [(u, v) for u, v in vts if not (0.0 <= u <= 1.0 and 0.0 <= v <= 1.0)]
print("    索引越界 %d 处 / UV 越界 %d 处" % (len(bad_idx), len(bad_uv)))
if bad_idx or bad_uv:
    fails.append("索引或 UV 越界")

# ---------------------------------------------------------------- 2. 几何血缘
print("\n[2] 几何血缘：刀身/鞘逐三角形零改动，镡与装饰带允许按规格收敛")
if base.is_file():
    vb, vtb, fb, ub, ob = load_obj(base)
    print("    基准 v=%d vt=%d f=%d 组=%d" % (len(vb), len(vtb), len(fb), len(ob)))
    if sorted(ob) != sorted(GROUPS13):
        warns.append("基准与目标的组名集合不一致，血缘比对可能失真")
    sig_a = Counter((g, p, uv) for g, p, uv, _, _ in fb)
    sig_s = Counter((g, p, uv) for g, p, uv, _, _ in fs)
    removed = sig_a - sig_s
    added = sig_s - sig_a
    print("    基准独有三角形 %d / 目标新增三角形 %d" %
          (sum(removed.values()), sum(added.values())))
    body_touch, allowed, illegal = 0, 0, []
    for (g, pts, uvs), n in list(removed.items()) + list(added.items()):
        xs = [p[0] for p in pts]
        # 刀身主体（鞘口 x=-17 以内）必须逐三角形完全一致
        if g in ("blade", "blade_luminous", "blade_damaged", "blade_damaged_luminous",
                 "sheath", "sheath_luminous", "effect") and min(xs) < -16.6:
            body_touch += n
            illegal.append((g, [round(x, 1) for x in xs]))
        else:
            allowed += n
    print("    镡/柄/装饰带（允许收敛）: %d" % allowed)
    print("    刀身/鞘/发光层/effect（禁止改动）: %d" % body_touch)
    for g, xs in illegal[:6]:
        print("      ! %s x=%s" % (g, xs))
    if illegal:
        fails.append("有 %d 个三角形在刀身/鞘区域被改动" % body_touch)
    print("    v1.1 说明：删除悬空枝叶白果 + 缩小镡盘属于规格要求的造型收敛，")
    print("               落在镡/柄装饰带，不计为血缘回归。")
else:
    warns.append("基准 %s 不存在，跳过血缘比对（可用 --baseline 指定）" % base)

# ---------------------------------------------------------------- 3. 造型收敛
print("\n[3] 造型收敛专项（评审第 1/2 条）")
guard = [p for g, pp, _, _, _ in fs if g == "blade" for p in pp
         if -16.6 <= p[0] <= -10.4]
max_r = max((math.hypot(p[1], p[2]) for p in guard), default=0)
print("    镡盘最大半径 %.2f（v1.0 = 15.00，规格要求小型）" % max_r)
if max_r > 10.5:
    fails.append("镡盘半径 %.1f 仍过大，未收敛到小型六角" % max_r)

# 悬空装饰：柄侧 x>-10.4 且 |y|>12 或 |z|>8 的 blade 三角形
floating = [(g, pp) for g, pp, _, _, _ in fs
            if g == "blade" and min(p[0] for p in pp) > -10.4
            and (max(abs(p[1]) for p in pp) > 12 or max(abs(p[2]) for p in pp) > 8)]
print("    镡外悬空装饰三角形 %d（期望 0）" % len(floating))
if floating:
    fails.append("仍有 %d 个悬空枝叶/白果三角形" % len(floating))

# 图标细节：细枝/叶/白果分区在 item_* 中的采样应为 0
detail = defaultdict(int)
for g, pp, uvs, _, _ in fs:
    if not g.startswith("item_"):
        continue
    for uv in uvs:
        c, r = int(uv[0] * 4), int((1 - uv[1]) * 4)
        if 0 <= r < 4 and 0 <= c < 4:
            name = TILE_NAMES[r][c]
            if name in ("leaf", "berry", "p_leaf", "p_berry"):
                detail[name] += 1
print("    item_* 采样枝叶/白果分区次数 %d（期望 0）" % sum(detail.values()))
if detail:
    fails.append("图标仍含枝叶/白果细节: %s" % dict(detail))

# ---------------------------------------------------------------- 4. 图标三态
print("\n[4] 图标三态")
for g in ("item_blade", "item_bladens", "item_damaged"):
    pts = [p for gg, pp, _, _, _ in fs if gg == g for p in pp]
    if not pts:
        fails.append("%s 组为空" % g)
        continue
    span = max(max(p[0] for p in pts) - min(p[0] for p in pts),
               max(p[1] for p in pts) - min(p[1] for p in pts))
    z0, z1 = min(p[2] for p in pts), max(p[2] for p in pts)
    print("    %-16s 跨度 %6.1f  z[%.1f, %.1f]" % (g, span, z0, z1))
    if not (140 <= span <= 175 and 1.0 <= z0 <= 5.0 and z1 <= 5.0):
        fails.append("%s 跨度/Z 层次偏离基准" % g)

# ---------------------------------------------------------------- 5. 图集 16 格
print("\n[5] 图集 16 格覆盖（1024² / 每格 256²）")
w, h, rows = decode_png(PNG.read_bytes())
print("    尺寸 %dx%d  %d 字节" % (w, h, PNG.stat().st_size))
if (w, h) != (1024, 1024):
    fails.append("图集不是 1024x1024")
tile_stat = {}
for r in range(4):
    cells = []
    for c in range(4):
        ink = tot = 0
        cols, alphas = set(), set()
        alpha_drawn = 0
        for y in range(r * 256, (r + 1) * 256, 4):
            row = rows[y]
            for x in range(c * 256, (c + 1) * 256, 4):
                o = x * 4
                px = (row[o], row[o + 1], row[o + 2], row[o + 3])
                tot += 1
                cols.add(px[:3]); alphas.add(px[3])
                if px[3] > 8:
                    alpha_drawn += 1
                    if not (px[0] > 248 and px[1] > 248 and px[2] > 248):
                        ink += 1
        name = TILE_NAMES[r][c]
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
        fails.append("%s 格仍是空白/无渐变" % name)

# ---------------------------------------------------------------- 5b. 分区实际采样
usage = {}
for g, pp, uvs, _, _ in fs:
    for uv in uvs:
        c, r = int(uv[0] * 4), int((1 - uv[1]) * 4)
        if 0 <= r < 4 and 0 <= c < 4:
            usage.setdefault(TILE_NAMES[r][c], {"faces": 0, "groups": set()})
            usage[TILE_NAMES[r][c]]["faces"] += 1
            usage[TILE_NAMES[r][c]]["groups"].add(g)
atlas_usage = {}
for name, (status, note) in BINDING.items():
    u = usage.get(name)
    atlas_usage[name] = {
        "tile": name,
        "obj_samples": u["faces"] if u else 0,
        "obj_groups": sorted(u["groups"]) if u else [],
        "binding": status,
        "note": note,
    }
USAGE.write_text(json.dumps(atlas_usage, ensure_ascii=False, indent=2), encoding="utf-8")
nw = [n for n, d in atlas_usage.items() if d["binding"] in ("NOT_WIRED", "DESIGN_ONLY")]
print("    已写 %s：%d 格有 OBJ 采样；%d 格为预置/未绑定 %s" %
      (USAGE.name, len(usage), len(nw), nw))

# ---------------------------------------------------------------- 6. 配色禁令
print("\n[6] 配色禁令（大面积暖金 / 深绿）")
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

# ---------------------------------------------------------------- 7. 16x16 量化
print("\n[7] 16x16 辨识度量化（评审第 2 条）")
iw, ih, irows = decode_png(ICON.read_bytes())


def box16(ox, oy, n=16, size=168):
    out = []
    for j in range(n):
        line = []
        for i in range(n):
            x0 = ox + int(i * size / n)
            x1 = ox + max(int(i * size / n) + 1, int((i + 1) * size / n))
            y0 = oy + int(j * size / n)
            y1 = oy + max(int(j * size / n) + 1, int((j + 1) * size / n))
            acc = [0, 0, 0]
            cnt = 0
            for yy in range(y0, min(y1, oy + size)):
                row = irows[yy]
                for xx in range(x0, min(x1, ox + size)):
                    o = xx * 4
                    acc[0] += row[o]; acc[1] += row[o + 1]; acc[2] += row[o + 2]
                    cnt += 1
            line.append(tuple(v // cnt for v in acc))
        out.append(line)
    return out


PAD, PW, BIG, SCALE = 16, 168, 16 * 12, 12
W = PAD * 3 + PW + BIG
H = 44 + 2 * (BIG + 30)
buf = bytearray(bytes((232, 236, 242, 255)) * (W * H))
INK = (40, 56, 76)
text(buf, W, H, PAD, 12, "FIRST SNOW V1.1 / 16X16 CHECK", INK, 2)
panes = [(18, 18, "SHEATHED"), (18 + 168 + 18, 18, "BROKEN")]
smalls = []
for k, (ox, oy, label) in enumerate(panes):
    ydst = 44 + k * (BIG + 30)
    for j in range(PW):
        row = irows[oy + j]
        o = (ydst + j) * W * 4 + PAD * 4
        buf[o:o + PW * 4] = row[ox * 4:(ox + PW) * 4]
    small = box16(ox, oy)
    smalls.append(small)
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
save_png(OUT16, W, H, buf)

# 两态差异：16x16 下 mean |ΔL|，以及差异像素（ΔL>18）占比
diffs, changed = [], 0
for j in range(16):
    for i in range(16):
        d = abs(lum(smalls[0][j][i]) - lum(smalls[1][j][i]))
        diffs.append(d)
        if d > 18:
            changed += 1
mean_diff = sum(diffs) / len(diffs)
changed_pct = 100 * changed / 256
print("    两态 16x16 平均明度差 %.1f / 255，差异像素(>18) %.1f%%" %
      (mean_diff, changed_pct))
if mean_diff < 12 or changed_pct < 12:
    fails.append("16x16 两态差异不足：mean=%.1f changed=%.1f%%" % (mean_diff, changed_pct))

# 主体/背板明度差：只量中央菱形区域（避开大面积浅灰 GUI 背景），
# 用 p90 - p10 表示「亮银刀 vs 深冷灰背板」在 16x16 下的实际层次。
inner = sorted(lum(smalls[0][j][i]) for j in range(3, 13) for i in range(3, 13))
p10 = inner[len(inner) // 10]
p90 = inner[len(inner) * 9 // 10]
contrast = p90 - p10
print("    中央菱形区 16x16 明度 p10=%.0f  p90=%.0f  层次 %.0f / 255" %
      (p10, p90, contrast))
if contrast < 55:
    fails.append("主体与背景明度层次不足 %.0f" % contrast)

# ---------------------------------------------------------------- 汇总
print("\n" + "=" * 78)
if fails:
    print("FAIL %d 项" % len(fails))
    for f in fails:
        print("  -", f)
    raise SystemExit(1)
print("PASS  13 组 / 血缘 / 造型收敛 / 16 格 / 配色 / 16x16 量化")
for w_ in warns:
    print("  WARN", w_)
print("\n注意：GUI 的本体耐久菱形层不受主图集控制；")
print("      slash_ring / slash_sphere / p_* 为预置素材，未绑定运行时（见 manifest.json）。")
