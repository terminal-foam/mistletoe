#!/usr/bin/env python3
"""SlashBlade Resharped 1.21.1 —— 进游戏前的模型穿模 / 渲染层静态检查。

用法:
    python check_ingame.py <model.obj> [<model2.obj> ...]

检查依据（全部来自 SlashBladeResharped-2.1.9 的字节码，不是猜测）：
  * LayerMainBlade.renderMainBlade 按 hardpointA 画 blade/blade_damaged，
    按 hardpointB 画 sheath；纳刀态两者在世界空间重合，因此刀身必须完整
    落在刀鞘内，否则就是截图里那种楔形穿模。
  * BladeRenderState.renderOverrided -> WavefrontObject.tessellateOnly(String...)
    每次只画一个固定组名；getGroupObjectMap() 是 HashMap.put，同名组只保留
    最后一个；组名缺失时静默跳过（不崩，但也不显示）。
  * 解析器正则 ([go]( [\\w\\d.]+) *\\n)：组名只能含字母/数字/下划线/点。
  * renderOverridedLuminous / renderChargeEffect 用加色混合画 *_luminous
    与 effect；这些层若完全埋在实体内部会 z-fighting 或直接看不见。
  * SlashBladeTEISR 用 0.008 缩放画 item_* 组，坐标必须留在原点附近。
  * Minecraft 实体渲染剔除背面：单面管状组若整根法线朝内，游戏里该组
    会完全消失，而软件预览光栅器不剔除、永远看不出来（曾导致刀鞘整根
    不可见，静止纳刀态只看到裸露刀身 + 双面金线）。

判定:
  FAIL —— 几何上会穿模 / 组名会让解析器抛异常（必须修）
  WARN —— 不会崩，但游戏里会有部件不显示或闪烁（建议修）
  PASS —— 结构合法，且所有状态组合下实体组互不穿透
"""
from __future__ import annotations

import math
import re
import sys
from collections import defaultdict
from pathlib import Path

# 渲染层实际会请求的组名
REQUIRED = [
    "blade", "blade_damaged", "blade_luminous", "blade_damaged_luminous",
    "sheath", "sheath_luminous", "effect",
    "item_blade", "item_bladens", "item_damaged",
]
# 会被渲染层忽略的附加同族组（不报错，仅提示）
OPTIONAL = ["item_blade_luminous", "item_bladens_luminous",
            "item_damaged_luminous", "blade_fragment"]
# 由客户端订阅 RenderOverrideEvent 显式 tessellateOnly 的自定义组。
# 本体渲染层不会请求它们，但它们不是"死组"：MistletoeGrowthRenderer 会按
# Super SA 蓄力进度逐个绘制 grow_00.. / grow_lum_00..，故不计入 WARN。
EVENT_RENDERED_PREFIXES = ("grow_",)
SOLID = {"blade", "blade_damaged", "sheath", "item_blade",
         "item_bladens", "item_damaged"}
SHELL_PARENT = {
    "blade_luminous": "blade",
    "blade_damaged_luminous": "blade_damaged",
    "sheath_luminous": "sheath",
    "effect": "sheath",
}
NAME_RE = re.compile(r"^[A-Za-z0-9_.]+$")


def parse(path: Path):
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    V, VT, VN = [], [], []
    faces = defaultdict(set)
    gorder, glines = [], defaultdict(int)
    cur = None
    bad_index = []
    mtl = 0
    for n, raw in enumerate(lines, 1):
        l = raw.strip()
        if not l or l.startswith("#"):
            continue
        p = l.split()
        tag = p[0]
        if tag == "v":
            try:
                V.append(tuple(float(x) for x in p[1:4]))
            except ValueError:
                pass
        elif tag == "vt":
            VT.append(p[1:3])
        elif tag == "vn":
            VN.append(p[1:4])
        elif tag in ("g", "o"):
            cur = p[1] if len(p) > 1 else ""
            glines[cur] += 1
            if cur not in gorder:
                gorder.append(cur)
        elif tag in ("mtllib", "usemtl"):
            mtl += 1
        elif tag == "f" and cur is not None:
            for tok in p[1:]:
                try:
                    i = int(tok.split("/")[0])
                except ValueError:
                    bad_index.append((n, tok, "v"))
                    continue
                # 负索引按 OBJ 规范换算
                j = i if i > 0 else len(V) + i + 1
                if not (1 <= j <= len(V)):
                    bad_index.append((n, tok, "v"))
                else:
                    faces[cur].add(j)
                parts = tok.split("/")
                if len(parts) > 1 and parts[1]:
                    try:
                        k = int(float(parts[1]))
                        kk = k if k > 0 else len(VT) + k + 1
                        if not (1 <= kk <= len(VT)):
                            bad_index.append((n, tok, "vt"))
                    except ValueError:
                        bad_index.append((n, tok, "vt"))
                if len(parts) > 2 and parts[2]:
                    try:
                        k = int(float(parts[2]))
                        kk = k if k > 0 else len(VN) + k + 1
                        if not (1 <= kk <= len(VN)):
                            bad_index.append((n, tok, "vn"))
                    except ValueError:
                        bad_index.append((n, tok, "vn"))
    pts = {g: [V[i - 1] for i in idx if 1 <= i <= len(V)]
           for g, idx in faces.items()}
    # 保留三角形顶点顺序，用于判断面朝向（set 会丢掉绕序信息）
    tris = defaultdict(list)
    cur = None
    for raw in lines:
        l = raw.strip()
        if not l or l.startswith("#"):
            continue
        p = l.split()
        if p[0] in ("g", "o"):
            cur = p[1] if len(p) > 1 else ""
        elif p[0] == "f" and cur is not None:
            ids, ok = [], True
            for tok in p[1:]:
                try:
                    i = int(tok.split("/")[0])
                    j = i if i > 0 else len(V) + i + 1
                    if not (1 <= j <= len(V)):
                        ok = False
                        break
                    ids.append(j)
                except ValueError:
                    ok = False
                    break
            if ok and len(ids) >= 3:
                for k in range(1, len(ids) - 1):
                    tris[cur].append((V[ids[0] - 1], V[ids[k] - 1], V[ids[k + 1] - 1]))
    return dict(lines=lines, V=V, VT=VT, VN=VN, faces=faces, pts=pts,
                gorder=gorder, glines=glines, bad_index=bad_index, mtl=mtl,
                tris=tris)


def orientation(d, group, step=10.0):
    """按 x 分桶估计截面中心，统计外表面法线朝外 / 朝内的比例。

    Minecraft 的实体渲染会剔除背面；单面管壁若整根朝内，游戏里该组会
    完全消失（软件预览器不剔除，永远看不出这个问题）。
    """
    tris = d["tris"].get(group, [])
    if len(tris) < 20:
        return None
    buckets = defaultdict(list)
    for a, b, c in tris:
        for q in (a, b, c):
            buckets[round(q[0] / step)].append(q)
    centers = {k: (sum(q[1] for q in vs) / len(vs),
                    sum(q[2] for q in vs) / len(vs))
               for k, vs in buckets.items()}
    keys = sorted(centers)
    out = inn = flat = 0
    for a, b, c in tris:
        u = (b[0] - a[0], b[1] - a[1], b[2] - a[2])
        v = (c[0] - a[0], c[1] - a[1], c[2] - a[2])
        n = (u[1] * v[2] - u[2] * v[1],
             u[2] * v[0] - u[0] * v[2],
             u[0] * v[1] - u[1] * v[0])
        L = math.sqrt(sum(q * q for q in n)) or 1.0
        cx = (a[0] + b[0] + c[0]) / 3
        cy = (a[1] + b[1] + c[1]) / 3
        cz = (a[2] + b[2] + c[2]) / 3
        k = min(keys, key=lambda q: abs(q - round(cx / step)))
        cy0, cz0 = centers[k]
        dot = (n[1] * (cy - cy0) + n[2] * (cz - cz0)) / L
        if abs(dot) < 0.05:
            flat += 1
        elif dot > 0:
            out += 1
        else:
            inn += 1
    return out, inn, flat


def bbox(pts):
    if not pts:
        return None
    return [(min(p[k] for p in pts), max(p[k] for p in pts)) for k in range(3)]


def x_buckets(pts, step=10.0, lo=None, hi=None):
    """按 x 分桶，返回 {bucket: [(y,z)...]}，可限定 x 区间。"""
    d = defaultdict(list)
    for x, y, z in pts:
        if (lo is not None and x < lo) or (hi is not None and x > hi):
            continue
        d[round(x / step) * step].append((y, z))
    return d


def containment(child_pts, parent_pts, step=10.0, label=""):
    """child 是否在 parent 的 (y,z) 截面内。返回 (最大越界量, 采样桶数, 明细)。"""
    pb = bbox(parent_pts)
    if pb is None or not child_pts:
        return None
    # 只看两者共有的刀身段，排除镡/柄/鞘口
    cb = bbox(child_pts)
    lo = max(pb[0][0], cb[0][0]) + 2
    hi = min(pb[0][1], cb[0][1]) - 2
    if hi <= lo:
        return None
    C, P = x_buckets(child_pts, step, lo, hi), x_buckets(parent_pts, step, lo, hi)
    common = sorted(set(C) & set(P))
    worst, details = -1e9, []
    for k in common:
        cys = [q[0] for q in C[k]]
        czs = [q[1] for q in C[k]]
        pys = [q[0] for q in P[k]]
        pzs = [q[1] for q in P[k]]
        cy, py_ = (min(cys) + max(cys)) / 2, (min(pys) + max(pys)) / 2
        cz, pz_ = (min(czs) + max(czs)) / 2, (min(pzs) + max(pzs)) / 2
        cw, pw_ = (max(cys) - min(cys)) / 2, (max(pys) - min(pys)) / 2
        cd, pd_ = (max(czs) - min(czs)) / 2, (max(pzs) - min(pzs)) / 2
        # 允许 0.5 单位容差（同面/共边不算穿模）
        vy = abs(cy - py_) + cw - pw_
        vz = abs(cz - pz_) + cd - pd_
        v = max(vy, vz)
        worst = max(worst, v)
        details.append((k, v, vy, vz))
    return (worst, len(common), details)


def shell_check(shell, parent, pts, step=10.0):
    """逐点比较加色层与实体的 (y,z) 截面。

    实体截面用同一 x 桶内的中心 + 半轴近似成椭圆；层点判据
    (dy/hy)^2+(dz/hz)^2 > 1 即在实体表面之外。贴面层允许 ±0.06 容差。
    """
    sp, pp = pts.get(shell), pts.get(parent)
    if not sp or not pp:
        return None
    sb, pb = bbox(sp), bbox(pp)
    lo, hi = max(sb[0][0], pb[0][0]) + 2, min(sb[0][1], pb[0][1]) - 2
    if hi <= lo:
        return "无重叠区间"
    S, P = x_buckets(sp, step, lo, hi), x_buckets(pp, step, lo, hi)
    buried = surface = outside = mixed = 0
    for k in set(S) & set(P):
        pys = [q[0] for q in P[k]]
        pzs = [q[1] for q in P[k]]
        cy, cz = (min(pys) + max(pys)) / 2, (min(pzs) + max(pzs)) / 2
        hy = max((max(pys) - min(pys)) / 2, 1e-3)
        hz = max((max(pzs) - min(pzs)) / 2, 1e-3)
        vals = [((y - cy) / hy) ** 2 + ((z - cz) / hz) ** 2 for y, z in S[k]]
        n = len(vals)
        buried += sum(v < 0.94 for v in vals)
        surface += sum(0.94 <= v <= 1.10 for v in vals)
        outside += sum(v > 1.10 for v in vals)
    tot = buried + surface + outside
    if not tot:
        return "无采样点"
    if buried / tot > 0.5:
        return f"大部分埋在实体内（{buried}/{tot} 点）—— 游戏里会被遮住 / z-fighting"
    if outside / tot > 0.5:
        return f"整体包在实体外（{outside}/{tot} 点，OK）"
    if surface / tot > 0.5:
        return f"贴在实体表面（{surface}/{tot} 点，OK）"
    return (f"混合：内 {buried} / 面 {surface} / 外 {outside} 点"
            f"（{'有内嵌段，可能闪烁' if buried > outside else '基本贴面'}）")


def check(path: Path):
    print("=" * 78)
    print("模型:", path)
    print("=" * 78)
    if not path.exists():
        print("  FAIL  文件不存在\n")
        return 1
    d = parse(path)
    pts, fails, warns = d["pts"], [], []

    nfaces = sum(1 for l in d["lines"] if l.strip().startswith("f "))
    print(f"  规模: 顶点 {len(d['V'])}  UV {len(d['VT'])}  法线 {len(d['VN'])}  "
          f"面 {nfaces}  组 {len(d['gorder'])}")

    # --- 结构 ---
    print("\n[1] 渲染层结构")
    for g in REQUIRED:
        if g not in pts:
            (warns if g in ("sheath_luminous", "effect") else fails).append(
                f"缺少组 {g} —— 该状态下渲染层会静默跳过（部件消失）")
    print("  必需组:", ", ".join(f"{g}{'✓' if g in pts else '✗'}" for g in REQUIRED))
    dup = [g for g, n in d["glines"].items() if n > 1]
    if dup:
        fails.append(f"同名组出现多次 {dup} —— HashMap.put 只保留最后一个，其余几何不显示")
    print("  同名组:", ", ".join(f"{g}×{n}" for g, n in d["glines"].items() if n > 1) or "无（全部唯一）")
    illegal = [g for g in d["gorder"] if not NAME_RE.match(g)]
    if illegal:
        fails.append(f"非法组名 {illegal} —— 解析器正则只接受字母/数字/下划线/点")
    print("  非法组名:", illegal or "无")
    if d["bad_index"]:
        fails.append(f"面索引越界 {len(d['bad_index'])} 处，例: {d['bad_index'][:3]}")
    print("  索引越界:", len(d["bad_index"]))
    if d["mtl"]:
        warns.append(f"有 {d['mtl']} 行 mtllib/usemtl —— 引擎忽略，多贴图不会生效")
    dead = [g for g in d["gorder"]
            if g not in REQUIRED + OPTIONAL + list(SHELL_PARENT)
            and not g.startswith(EVENT_RENDERED_PREFIXES)]
    if dead:
        warns.append(f"渲染层永远不会请求的组: {dead}（写进 OBJ 也不会显示）")
    print("  不会渲染的组:", dead or "无")

    # --- 几何 ---
    print("\n[2] 实体组穿模检查（纳刀态 blade 与 sheath 世界空间重合）")
    r = containment(pts.get("blade", []), pts.get("sheath", []))
    if r is None:
        warns.append("blade / sheath 无共同刀身段，无法判定包裹关系")
        print("  blade ⊂ sheath: 无法判定")
    else:
        worst, n, det = r
        print(f"  blade ⊂ sheath: 采样 {n} 段，最大越界 {worst:+.2f} 单位 "
              f"({'包住' if worst <= 0.5 else '*** 穿模 ***'})")
        if worst > 0.5:
            fails.append(f"刀身穿出刀鞘 {worst:.2f} 单位（最严重 x≈{max(det, key=lambda t: t[1])[0]:.0f}）")
        print("    分段:", " ".join(f"x{k:.0f}:{v:+.1f}" for k, v, _, _ in det[::max(1, n // 8)][:9]))
    r2 = containment(pts.get("blade_damaged", []), pts.get("blade", []))
    if r2:
        worst2, n2, _ = r2
        print(f"  blade_damaged ⊂ blade: 采样 {n2} 段，最大越界 {worst2:+.2f} "
              f"({'同一把刀截短' if worst2 <= 0.5 else '形状不一致'})")
        if worst2 > 0.5:
            warns.append(f"破损刀与完好刀截面不一致（越界 {worst2:.2f}），切换时会跳形")

    print("\n[3] 发光层 / 充能壳与实体的关系")
    for s, p in SHELL_PARENT.items():
        if s not in pts:
            continue
        res = shell_check(s, p, pts)
        print(f"  {s:26s} vs {p:10s}: {res}")
        if res and ("埋在实体内" in res or "有内嵌段" in res):
            warns.append(f"{s}: {res}")

    print("\n[4] 物品栏坐标（SlashBladeTEISR 0.008 缩放）")
    for g in ("item_blade", "item_bladens", "item_damaged"):
        if g not in pts:
            continue
        b = bbox(pts[g])
        span = max(hi - lo for lo, hi in b)
        flag = "OK" if span <= 400 else "超出 GUI 视野"
        print(f"  {g:18s} 最大跨度 {span:7.1f}  {flag}")
        if span > 400:
            warns.append(f"{g} 跨度 {span:.0f}，物品栏里会被裁掉")

    print("\n[5] 物品栏图标质量（OBJ 几何画图标，不是 PNG）")
    # SlashBladeTEISR.renderIcon: GUI scale(0.008) -> item_* -> durability 菱形底板
    # (base/color 在 z≈0.1 / -1.9)。图标组必须是一把完整的斜置整刀：
    #   面数够多（不能退化成两根竖条）、整体在底板之前、全部朝 +Z。
    ICONS = ("item_blade", "item_bladens", "item_damaged")
    for g in ICONS + tuple(x + "_luminous" for x in ICONS):
        if g not in pts:
            (warns if g.endswith("_luminous") else fails).append(f"缺少图标组 {g}")
            print(f"  {g:26s} 缺失")
            continue
        p = pts[g]
        b = bbox(p)
        span = max(hi - lo for lo, hi in b)
        cx, cy = (b[0][0] + b[0][1]) / 2, (b[1][0] + b[1][1]) / 2
        zlo, zhi = b[2]
        tris = d["tris"].get(g, [])
        nf = len(tris)
        plus = sum(1 for a, bb, c in tris
                   if ((bb[0] - a[0]) * (c[1] - a[1])
                       - (bb[1] - a[1]) * (c[0] - a[0])) > 0)
        ratio = plus / max(1, nf)
        lum = g.endswith("_luminous")
        min_faces, min_span, max_off = (12, 20, 60) if lum else (30, 90, 45)
        ok = (nf >= min_faces and min_span <= span <= 400
              and abs(cx) <= max_off and abs(cy) <= max_off
              and zlo > 0.5 and ratio >= 0.95)
        print(f"  {g:26s} 面 {nf:4d}  跨度 {span:6.1f}  中心({cx:+4.0f},{cy:+4.0f})  "
              f"z[{zlo:.1f},{zhi:.1f}]  +Z面 {ratio:5.0%}  {'OK' if ok else '** 异常 **'}")
        if nf < min_faces:
            fails.append(f"{g} 只有 {nf} 个面 —— 图标退化成占位竖条，游戏里看不出是刀")
        if not (min_span <= span <= 400):
            fails.append(f"{g} 跨度 {span:.0f} 不合理，物品栏里不是太大就是太小")
        if zlo <= 0.5:
            fails.append(f"{g} 有几何落在 z≤0.5 —— 会被原版 durability 菱形底板挡住")
        if ratio < 0.95:
            fails.append(f"{g} 有 {1 - ratio:.0%} 的面背向 GUI —— 背面剔除后枝叶会消失")
        if abs(cx) > max_off or abs(cy) > max_off:
            warns.append(f"{g} 中心偏离原点 ({cx:.0f},{cy:.0f})，图标会偏出格子")
        if lum:
            parent = g[:-len("_luminous")]
            if parent in pts and zhi <= bbox(pts[parent])[2][1] + 0.2:
                fails.append(f"{g} 没有外凸出 {parent}，加色发光层会被实体遮住")
    bad_uv = 0
    for pair in d["VT"]:
        try:
            if any(not (0.0 <= float(x) <= 1.0) for x in pair[:2]):
                bad_uv += 1
        except ValueError:
            bad_uv += 1
    print(f"  图集 UV 越界: {bad_uv} 处")
    if bad_uv:
        fails.append(f"{bad_uv} 个 UV 不在 0..1，图标会采到相邻分区的颜色")

    print("\n[6] 面朝向 / 背面剔除（整组在游戏中消失的根因）")
    for g in ("sheath", "blade", "blade_damaged"):
        r = orientation(d, g)
        if r is None:
            print(f"  {g:20s} 面数不足，跳过")
            continue
        out, inn, flat = r
        ratio = inn / max(1, out + inn)
        flag = "** 整组会被剔除 **" if ratio > 0.8 else "OK"
        print(f"  {g:20s} 朝外 {out:5d} / 朝内 {inn:5d} / 切向 {flat:4d}"
              f"   朝内占比 {ratio:5.0%}  {flag}")
        if ratio > 0.8:
            fails.append(f"{g} 有 {ratio:.0%} 的面朝内 —— 背面剔除后整组不显示，"
                         "游戏里只剩刀身")

    print("\n[7] 结论")
    if fails:
        for f in fails:
            print("  FAIL ", f)
    if warns:
        for w in warns:
            print("  WARN ", w)
    if not fails:
        print("  PASS  结构合法，且所有实体组在渲染层会组合的状态下不互相穿透")
    print()
    return len(fails)


def main():
    args = sys.argv[1:]
    if not args:
        args = [str(Path(__file__).with_name("demo_model.obj"))]
        print("未指定文件，默认检查准备进仓库的模型\n")
    total = sum(check(Path(a)) for a in args)
    print("=" * 78)
    print(f"合计 FAIL {total} 项" if total else "合计 FAIL 0 项")
    return 1 if total else 0


if __name__ == "__main__":
    sys.exit(main())
