#!/usr/bin/env python3
"""立体流苏 / 冰珠专项验收（纯标准库，读 out/first_snow.obj）。

为什么需要独立检查器：挂件必须并进 sheath 组才会被 SlashBlade 渲染，
因此 check_ingame.py 的 blade ⊂ sheath 包络统计会被挂件顶点一起算进去，
check.py 的「鞘逐三角形零改动」也会把它们当回归。两者都不适合判断
「新增的悬挂几何有没有插进鞘壁 / 刀身」，这里按几何直接验。

判据：
  [1] 挂件只在 sheath 组，且只采样 leaf / berry 两格
  [2] 所有挂件顶点在鞘椭圆之外  q=(dy/sw)^2+(z/sz)^2 >= 0.97
  [3] 所有挂件顶点不与 blade 的 (y,z) 截面相交
  [4] 冰珠 / 结扣是闭合网格（每条无向边恰好被 2 个三角形共享）
  [5] 流苏带是双面（每个 quad 的正反两个三角形法线相反）

产出 out/tassel_check.json；任一 FAIL 时以 exit 1 让 build_all.py 中断。
"""
from __future__ import annotations

import json
import math
from collections import Counter, defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE / "out"
OUT.mkdir(parents=True, exist_ok=True)
OBJ = OUT / "first_snow.obj"
REPORT = OUT / "tassel_check.json"

fails = []
report = {"artifact": "mistletoe:first_snow", "subject": "立体下垂流苏 + 冰珠"}


def tile_of(uv):
    """全局 UV -> 分区名（只关心挂件会用到的两格）。"""
    u, v = uv
    if 0.50 < u <= 0.75 and 0.50 < v < 0.75:
        return "leaf"
    if 0.75 < u < 1.0 and 0.50 < v < 0.75:
        return "berry"
    return None


def cross(a, b, c):
    u = (b[0] - a[0], b[1] - a[1], b[2] - a[2])
    v = (c[0] - a[0], c[1] - a[1], c[2] - a[2])
    return (u[1] * v[2] - u[2] * v[1],
            u[2] * v[0] - u[0] * v[2],
            u[0] * v[1] - u[1] * v[0])


def key(p):
    return tuple(round(q, 3) for q in p)


V, VT, TRI = [], [], []
cur = None
glines = Counter()
for raw in OBJ.read_text(encoding="utf-8").splitlines():
    s = raw.strip()
    if s.startswith("v "):
        a = s.split()
        V.append(tuple(round(float(x), 3) for x in a[1:4]))
    elif s.startswith("vt "):
        a = s.split()
        VT.append((float(a[1]), float(a[2])))
    elif s.startswith("g "):
        cur = s[2:].strip()
        glines[cur] += 1
    elif s.startswith("f ") and cur:
        toks = [t.split("/") for t in s.split()[1:]]
        pts = tuple(V[int(t[0]) - 1] for t in toks)
        uvs = tuple(VT[int(t[1]) - 1] for t in toks if len(t) > 1 and t[1])
        TRI.append((cur, pts, uvs, cross(*pts[:3])))

print("=" * 78)
print("立体流苏 / 冰珠专项验收   ", OBJ.name)
print("=" * 78)
print("  三角形 %d  /  sheath 组段数 %d" % (len(TRI), glines["sheath"]))

# ---------------------------------------------------------------- [1] 归属与 UV
addon, shell, foreign = [], [], []
for g, pts, uvs, n in TRI:
    tiles = {tile_of(q) for q in uvs}
    if len(tiles) == 1 and next(iter(tiles)) in ("leaf", "berry"):
        if g == "sheath":
            addon.append((g, pts, uvs, n, next(iter(tiles))))
        else:
            foreign.append((g, next(iter(tiles))))
    elif g == "sheath":
        shell.append((g, pts, uvs, n))

by_tile = Counter(t for *_, t in addon)
print("\n[1] 归属与 UV")
print("    挂件三角形 %d  (leaf 带子 %d / berry 珠与结扣 %d)" %
      (len(addon), by_tile["leaf"], by_tile["berry"]))
print("    非 sheath 组里的挂件 UV 采样: %d" % len(foreign))
print("    sheath 组段数: %d（同名组必须唯一，HashMap.put 只留最后一个）" %
      glines["sheath"])
if not addon:
    fails.append("没有找到立体挂件几何")
if by_tile["leaf"] < 8 or by_tile["berry"] < 8:
    fails.append("带子或冰珠几何数量异常: %s" % dict(by_tile))
if foreign:
    fails.append("挂件 UV 出现在非 sheath 组: %s" % foreign[:5])
if glines["sheath"] != 1:
    fails.append("sheath 组出现 %d 段，游戏里只有最后一段会显示" % glines["sheath"])

# ---------------------------------------------------------------- [2] 鞘椭圆外部
print("\n[2] 挂件是否在鞘椭圆之外（静止纳刀 blade 完整藏在鞘里）")
shell_pts = [p for _, pts, _, _ in shell for p in pts]
blade_pts = [p for g, pts, _, _ in TRI if g == "blade" for p in pts]


def buckets(points, step=10.0):
    d = defaultdict(list)
    for x, y, z in points:
        d[round(x / step) * step].append((y, z))
    return d


SB, BB = buckets(shell_pts), buckets(blade_pts)
skeys = sorted(SB)
bkeys = sorted(BB)
addon_pts = [p for _, pts, _, _, _ in addon for p in pts]
min_q, worst = 1e9, None
for x, y, z in addon_pts:
    k = min(skeys, key=lambda q: abs(q - x))
    ys = [q[0] for q in SB[k]]
    zs = [q[1] for q in SB[k]]
    cy = (min(ys) + max(ys)) / 2
    sw = max((max(ys) - min(ys)) / 2, 1e-3)
    sz = max(max(abs(q) for q in zs), 1e-3)
    q = ((y - cy) / sw) ** 2 + (z / sz) ** 2
    if q < min_q:
        min_q, worst = q, (round(x, 1), round(y, 1), round(z, 1))
print("    挂件顶点 %d，最小 q = %.3f  at %s  (要求 >= 0.97)" %
      (len(addon_pts), min_q, worst))
if min_q < 0.97:
    fails.append("挂件插入鞘壁：最小 q=%.3f < 0.97" % min_q)

# ---------------------------------------------------------------- [3] 与 blade 不相交
print("\n[3] 与 blade 刀身截面的关系")
hits = 0
min_gap = 1e9
for x, y, z in addon_pts:
    k = min(bkeys, key=lambda q: abs(q - x))
    ys = [q[0] for q in BB[k]]
    zs = [q[1] for q in BB[k]]
    # 挂件整体在刀身的 +z 外侧或 -y 下方；落进刀身 (y,z) 盒即判穿模
    if min(ys) - 0.3 <= y <= max(ys) + 0.3 and min(zs) - 0.3 <= z <= max(zs) + 0.3:
        hits += 1
    min_gap = min(min_gap, z - max(zs), min(ys) - y)
# 只比挂件所在 x 区间内的刀身段；blade 组还含镡/柄（z 更大），
# 拿全组 zmax 比会得出「挂件比 blade 更靠内」的错误印象。
ax = [p[0] for p in addon_pts]
blade_local = [p for p in blade_pts
               if min(ax) - 12 <= p[0] <= max(ax) + 12]
blade_zmax = max(p[2] for p in blade_local)
addon_zmin = min(p[2] for p in addon_pts)
print("    落在 blade (y,z) 盒内的挂件顶点 %d（期望 0）" % hits)
print("    同区间 z：blade 最大 %.2f / 挂件最小 %.2f / 间隙 %.2f" %
      (blade_zmax, addon_zmin, addon_zmin - blade_zmax))
if hits:
    fails.append("%d 个挂件顶点与刀身截面重叠" % hits)
if addon_zmin <= blade_zmax:
    fails.append("挂件整体没有位于刀身 +z 外侧")

# ---------------------------------------------------------------- [4] 冰珠闭合
print("\n[4] 冰珠 / 结扣闭合性（游戏背面剔除下不会漏面）")
edges = Counter()
for _, pts, _, _, tile in addon:
    if tile != "berry":
        continue
    for i in range(3):
        a, b = key(pts[i]), key(pts[(i + 1) % 3])
        edges[tuple(sorted((a, b)))] += 1
open_edges = [e for e, n in edges.items() if n != 2]
print("    berry 三角形的无向边 %d 条，非 2 次共享 %d 条" %
      (len(edges), len(open_edges)))
if open_edges:
    fails.append("冰珠/结扣不是闭合网格：%d 条开放边" % len(open_edges))

# ---------------------------------------------------------------- [5] 带子双面
print("\n[5] 流苏带双面（Minecraft 剔除背面，单面带会半边消失）")
pairs = defaultdict(list)
for _, pts, _, n, tile in addon:
    if tile != "leaf":
        continue
    pairs[frozenset(key(p) for p in pts)].append(n)
bad_pairs = [k for k, ns in pairs.items()
             if len(ns) != 2 or sum(a * b for a, b in zip(ns[0], ns[1])) >= 0]
print("    leaf 三角形 %d，顶点组合 %d 组，非正反成对 %d 组" %
      (by_tile["leaf"], len(pairs), len(bad_pairs)))
if bad_pairs:
    fails.append("%d 组带子三角形没有配对反向法线" % len(bad_pairs))

report.update({
    "addon_triangles": len(addon),
    "leaf_triangles": by_tile["leaf"],
    "berry_triangles": by_tile["berry"],
    "sheath_group_segments": glines["sheath"],
    "min_shell_ellipse_q": round(min_q, 4),
    "min_q_at": worst,
    "blade_overlap_vertices": hits,
    "blade_zmax": round(blade_zmax, 3),
    "addon_zmin": round(addon_zmin, 3),
    "berry_open_edges": len(open_edges),
    "ribbon_unpaired_triangles": len(bad_pairs),
    "fails": fails,
    "status": "PASS" if not fails else "FAIL",
    "note": "挂件属于 sheath 组；拔刀态 sheath 不渲染，挂件随之隐藏，符合「挂在鞘上」语义。",
})
REPORT.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
print("\n  已写 %s" % REPORT.name)
print("=" * 78)
if fails:
    print("FAIL %d 项" % len(fails))
    for f in fails:
        print("  -", f)
    raise SystemExit(1)
print("PASS  挂件只在 sheath 组 / 全在鞘椭圆外 / 不碰刀身 / 冰珠闭合 / 带子双面")
