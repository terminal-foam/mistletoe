#!/usr/bin/env python3
"""从 make_demo.py 派生「初雪」专属生成器 make_first_snow.py。

设计约束（依据 docs/初雪-美术资源交付与接入清单.md）：
  * 形状函数 / 13 组 / UV 布局 / item_* 三态 Z 层次 / 无 mtllib 全部原样保留；
  * 只改三处：图集 16 格配色、blade 组内刀镡轮廓、图标镡的六角化；
  * 不引入 grow_* 组、不改原点比例与反り方向。

本脚本只做文本替换并逐条断言，保证派生结果可复现、可审计。
用法：python derive_first_snow.py
"""
from pathlib import Path

HERE = Path(__file__).resolve().parent
SRC = (HERE / "make_demo.py").read_text(encoding="utf-8")
DST_DIR = HERE / "first_snow"
DST_DIR.mkdir(exist_ok=True)

R = []  # (说明, 原文, 新文)


def sub(tag, old, new):
    R.append((tag, old, new))


# ---------------------------------------------------------------- 1. 固定配色档
sub("文件头",
'''"""terminalblade 美术能力演示（纯标准库，不改动项目仓库）。''',
'''"""槲寄生[初雪] first_snow 美术资源生成器（纯标准库，不改动项目仓库）。

由 make_demo.py 派生：形状 / 分组 / UV 全部沿用朝露·暮色，只换冷白霜纹图集、
六角雪花刀镡轮廓与图标主题。交付物见 first_snow/ 目录。''')

sub("档位表",
'TONES = ("white", "green", "deepgreen", "black")',
'TONES = ("firstsnow", "white", "green", "deepgreen", "black")')

sub("固定档位",
'TONE = _read_tone()',
'TONE = "firstsnow"   # 初雪是独立武器，配色固定；其余档位只用于对照图')

# ---------------------------------------------------------------- 2. 刀身 / 柄 / 镡贴图
sub("刀身",
'''def t_blade(u, v):
    # 原版名刀做法：一条平滑银白渐变，只带一条极浅刃纹线，没有锯齿
    base = 246 - 66 * v
    grain = 3 * math.sin((u * 47 + v * 3) * math.pi)
    c = rgba(base + grain, base + grain + 3, base + grain + 8)
    if v < 0.045:                      # 刃口亮线
        c = rgba(252, 254, 255)
    if v > 0.93:                       # 栋（刀背）暗线
        c = rgba(74, 88, 100)
    hamon = 0.55 + 0.035 * math.sin(u * math.pi * 2)
    if abs(v - hamon) < 0.016:
        c = rgba(c[0] + 14, c[1] + 14, c[2] + 14)
    return c''',
'''def t_blade(u, v):
    # 初雪：冷银白刃，栋偏冷灰蓝，刃纹用极浅冰蓝，不出现暖色
    base = 244 - 58 * v
    grain = 3 * math.sin((u * 47 + v * 3) * math.pi)
    c = rgba(base + grain - 4, base + grain + 2, base + grain + 11)
    if v < 0.045:                      # 刃口雪白亮线
        c = rgba(247, 251, 255)
    if v > 0.93:                       # 栋：冷灰蓝暗线
        c = rgba(96, 116, 138)
    hamon = 0.55 + 0.035 * math.sin(u * math.pi * 2)
    if abs(v - hamon) < 0.016:
        c = rgba(c[0] + 6, c[1] + 10, c[2] + 16)
    return c''')

sub("刀身发光",
'''def t_blade_lum(u, v):
    e = math.exp(-((v - 0.18) / 0.22) ** 2)
    return rgba(215 + 40 * e, 240 + 15 * e, 255, 235 * e)''',
'''def t_blade_lum(u, v):
    # 冰蓝加色层：浅底上压低 alpha，避免把冷白刃刷成一团白
    e = math.exp(-((v - 0.18) / 0.22) ** 2)
    return rgba(169 + 46 * e, 216 + 24 * e, 255, 150 * e)''')

sub("柄卷",
'''def t_handle(u, v):
    # 原版柄卷：连续菱纹 / 山形编织带，白色编织线 + 深绿底
    band = int(u * 14)
    a = (u * 14 + v * 6) % 1.0
    b = (u * 14 - v * 6) % 1.0
    d = min(abs(a - 0.5), abs(b - 0.5))
    if d > 0.42:
        return rgba(206, 216, 208)
    if d > 0.33:
        return rgba(64, 108, 78)
    return rgba(16, 40, 30) if band % 2 == 0 else rgba(22, 52, 38)''',
'''def t_handle(u, v):
    # 初雪柄卷：保留系列菱纹编织结构，改为冷灰蓝底 + 雪白编织线
    band = int(u * 14)
    a = (u * 14 + v * 6) % 1.0
    b = (u * 14 - v * 6) % 1.0
    d = min(abs(a - 0.5), abs(b - 0.5))
    if d > 0.42:
        return rgba(232, 240, 250)
    if d > 0.33:
        return rgba(108, 134, 162)
    return rgba(48, 66, 86) if band % 2 == 0 else rgba(62, 82, 104)''')

sub("刀镡贴图",
'''def t_tsuba(u, v):
    # 原版刀镡：深色金属圆盘 + 白色四瓣描边 + 内圈切羽 + 铆点
    dx, dy = (u - 0.5) * 2, (v - 0.5) * 2
    r = math.hypot(dx, dy)
    th = math.atan2(dy, dx)
    petal = 0.80 + 0.20 * math.cos(4 * th)
    base = 104 - 26 * r
    c = rgba(base + 10, base + 26, base + 24)
    if abs(r - petal) < 0.055 or abs(r - 0.34) < 0.035:
        c = rgba(214, 226, 220)
    for k in range(4):
        ang = k * math.pi / 2 + math.pi / 4
        if math.hypot(dx - 0.60 * math.cos(ang), dy - 0.60 * math.sin(ang)) < 0.075:
            c = rgba(230, 238, 232)
    # 中心四芒星徽记：与物品栏菱形背板呼应，3D 刀镡和图标镡都能看到
    star = 0.055 + 0.25 * abs(math.cos(2 * th)) ** 5
    if r < star + 0.022:
        c = rgba(126, 148, 138) if r > star else rgba(240, 248, 242)
    if r < 0.045:
        c = rgba(74, 106, 82)
    return c''',
'''def t_tsuba(u, v):
    """初雪刀镡：冷银底 + 六角雪花纹，与 blade 组内的六角轮廓同步。"""
    dx, dy = (u - 0.5) * 2, (v - 0.5) * 2
    r = math.hypot(dx, dy)
    th = math.atan2(dy, dx)
    base = 178 - 34 * r
    c = rgba(base - 6, base + 8, base + 20)

    # 外圈双细边（冷银亮边 + 冰蓝内描）
    petal = 0.78 + 0.22 * abs(math.cos(3 * th)) ** 3
    if abs(r - petal) < 0.050:
        c = rgba(226, 240, 252)
    elif abs(r - 0.34) < 0.032:
        c = rgba(126, 158, 188)

    # 六向雪花主枝：每条臂的中段再分一对短侧枝
    a6 = th % (math.pi / 3)
    spoke = abs(math.sin(a6 * 3.0))
    if r < 0.72 and spoke < 0.085:
        c = rgba(238, 248, 255) if r < 0.60 else rgba(150, 190, 224)
    for k in range(6):
        ang = k * math.pi / 3
        for rad, side in ((0.46, 0.16),):
            for sg in (-1, 1):
                a2 = ang + sg * 0.42
                if math.hypot(dx - rad * math.cos(a2), dy - rad * math.sin(a2)) < side * 0.55:
                    c = rgba(206, 230, 250)
    # 中心六角芯
    if r < 0.10:
        c = rgba(232, 244, 255) if r < 0.065 else rgba(120, 152, 184)
    return c''')

# ---------------------------------------------------------------- 3. 刀鞘主格：新增冷白调色板 + 霜纹
sub("鞘调色板",
'''    四档共用同一套装饰结构，只换底色与各部件对比色。
    """
    if TONE == "black":
        g, amp, rim_amp = 20 + 16 * (1 - v), 30, 20''',
'''    四档共用同一套装饰结构，只换底色与各部件对比色。
    """
    if TONE == "firstsnow":
        # 初雪：冷白灰 / 淡灰蓝漆底 + 冷银配件 + 霜化枝叶白果。
        # 无大面积深绿与暖金；金线位改走冷银，叶片改灰青霜色。
        g, amp, rim_amp = 182 + 24 * (1 - v), 46, 36
        wr, wg, wb, bias = 0.95, 0.98, 1.00, 6
        gold, edge = (186, 208, 230), (248, 252, 255)
        metal, metal_hi, metal_dk = (150, 170, 194), (226, 240, 252), (88, 106, 128)
        cord, cord_hi, cord_dk = (96, 120, 144), (186, 208, 228), (58, 76, 98)
        wrap_white, wrap_dark = (247, 251, 255), (74, 98, 124)
        leaf, leaf_dk, leaf_hi, vein = (132, 162, 170), (76, 104, 116), (196, 220, 226), (222, 238, 242)
        berry, berry_sh, berry_hi, berry_dot = (250, 253, 255), (158, 180, 198), (255, 255, 255), (122, 148, 172)
        contrast = 0.70
    elif TONE == "black":
        g, amp, rim_amp = 20 + 16 * (1 - v), 30, 20''')

sub("鞘漆面霜纹",
'''    # 3) 复合纵向金线：中央暗槽 + 金线 + 银白外描边
    c = rgba(r, gr, b)
    dv = abs(v - 0.14)''',
'''    c = rgba(r, gr, b)

    # 初雪专用：极浅六向霜晶纹，密度受控，16x16 图标下不会糊成噪点。
    # 只作用于漆面层，后面的金线 / 箍 / 结绳 / 叶果会照常覆盖它。
    if TONE == "firstsnow":
        cu, cv = u * 7.0, v * 5.0
        fu, fv = cu % 1.0 - 0.5, cv % 1.0 - 0.5
        a6 = math.atan2(fv, fu)
        rad = math.hypot(fu, fv)
        spoke = min(abs(math.sin(a6 * 3.0)), abs(math.cos(a6 * 3.0)))
        crystal = max(0.0, 1.0 - spoke / 0.13) * max(0.0, 1.0 - rad / 0.42)
        if crystal > 0 and (int(cu) * 3 + int(cv) * 5) % 3 != 1:
            k = 17 * crystal
            c = rgba(r + k * 0.65, gr + k * 0.85, b + k)

    # 3) 复合纵向金线：中央暗槽 + 金线 + 银白外描边
    dv = abs(v - 0.14)''')

sub("鞘发光层",
'''    if TONE == "deepgreen":
        return rgba(150, 214, 168, 165 * e)
    return rgba(216, 248, 226, 190 * e)''',
'''    if TONE == "deepgreen":
        return rgba(150, 214, 168, 165 * e)
    if TONE == "firstsnow":
        # 浅漆底上的加色层必须压得很淡，否则整根鞘会被刷回纯白
        return rgba(140, 196, 240, 96 * e)
    return rgba(216, 248, 226, 190 * e)''')

sub("金属鎺",
'''def t_habaki(u, v):
    # 鎺：刀身与刀镡之间的黄铜套，原版名刀常见暖金色分区
    g = 176 - 66 * v + 12 * math.sin(u * 6)
    return rgba(g + 54, g + 24, g - 30)''',
'''def t_habaki(u, v):
    # 初雪：冷银鎺 / 鞘尻包头，去掉暖金
    g = 186 - 54 * v + 10 * math.sin(u * 6)
    return rgba(g - 16, g + 4, g + 26)''')

# ---------------------------------------------------------------- 4. 枝叶 / 白果 / 特效 / 粒子
sub("霜化叶",
'''def t_leaf(u, v):
    w = 0.44 * (math.sin(math.pi * min(1.0, max(0.0, u))) ** 0.75)
    d = abs(v - 0.5)
    if d > w:
        return (0, 0, 0, 0)
    edge = d / max(w, 1e-6)
    g = 62 + 62 * (1 - edge) + 18 * math.sin(u * 9)
    if abs(v - 0.5) < 0.016:
        return rgba(178, 218, 154)
    vein = abs(((u * 9 + (v - 0.5) * 5) % 1.0) - 0.5) > 0.47
    if vein:
        return rgba(34, 78, 44)
    return rgba(30 + g * 0.25, g, 44 + g * 0.35, 248)''',
'''def t_leaf(u, v):
    # 霜化叶：灰青叶身 + 白霜叶缘 + 冰蓝中脉，不出现饱和绿
    w = 0.44 * (math.sin(math.pi * min(1.0, max(0.0, u))) ** 0.75)
    d = abs(v - 0.5)
    if d > w:
        return (0, 0, 0, 0)
    edge = d / max(w, 1e-6)
    if edge > 0.86:
        return rgba(222, 238, 244, 248)
    g = 118 + 54 * (1 - edge) + 14 * math.sin(u * 9)
    if abs(v - 0.5) < 0.016:
        return rgba(214, 234, 246)
    vein = abs(((u * 9 + (v - 0.5) * 5) % 1.0) - 0.5) > 0.47
    if vein:
        return rgba(76, 106, 122)
    return rgba(g * 0.72, g * 0.94, g + 16, 248)''')

sub("白果",
'''    return rgba(shade + hi, shade + hi + 4, shade + hi + 12, a)''',
'''    return rgba(shade + hi - 6, shade + hi + 2, shade + hi + 14, a)''')

sub("effect 格",
'''def t_effect(u, v):
    r = math.hypot(u - 0.5, v - 0.5) * 2
    streak = 0.75 + 0.25 * math.sin(math.atan2(v - 0.5, u - 0.5) * 12)
    a = 210 * max(0.0, 1 - r) ** 1.7 * streak
    return rgba(205, 240, 255, a)''',
'''def t_effect(u, v):
    """本体右键充能漩涡：冰蓝六向霜旋（effect 组必须保留并验收）。"""
    dx, dy = u - 0.5, v - 0.5
    r = math.hypot(dx, dy) * 2
    th = math.atan2(dy, dx)
    swirl = 0.68 + 0.32 * math.sin(th * 6 + r * 9)
    core = max(0.0, 1 - r)
    a = 205 * core ** 1.7 * swirl
    return rgba(169 + 62 * core, 216 + 34 * core, 255, a)''')

sub("枝",
'''def t_branch(u, v):
    bark = 12 * math.sin(v * 41 + u * 9)
    return rgba(46 + bark, 62 + bark, 42 + bark)''',
'''def t_branch(u, v):
    # 深冷灰霜枝；同时用作图标刀的描边色，让冷白刀在浅底上分得出来
    bark = 9 * math.sin(v * 41 + u * 9)
    return rgba(58 + bark, 78 + bark, 104 + bark)''')

sub("粒子叶",
'''    return rgba(150, 226, 168, a)''',
'''    return rgba(198, 226, 240, a)''')

sub("粒子果",
'''    return rgba(238, 252, 255, a)''',
'''    return rgba(238 - 26 * r, 248 - 14 * r, 255, a)''')

sub("粒子霜片",
'''    return rgba(226, 248, 255, a)''',
'''    return rgba(214 - 24 * d, 238 - 12 * d, 255, a)''')

sub("霜刃环",
'''    return rgba(224, 250, 255, a * taper)''',
'''    return rgba(190 - 34 * (1 - taper), 226 - 18 * (1 - taper), 255, a * taper)''')

sub("霜刃球",
'''    return rgba(206, 242, 255, a + 60 * ray * (1 - r))''',
'''    return rgba(169 + 46 * (1 - r), 216 + 28 * (1 - r), 255, a + 60 * ray * (1 - r))''')

# ---------------------------------------------------------------- 5. 菱形徽记：雪花主题
sub("徽记",
'''    dx, dy = (u - 0.5) * 2, (v - 0.5) * 2
    d = abs(dx) + abs(dy)
    if d > 0.995:
        return (0, 0, 0, 0)
    a = 255 if d < 0.94 else 255 * (0.995 - d) / 0.055

    # 1) 珍珠银灰漆底：中心亮、边缘略暗，叠极细确定性颗粒
    base = 176 - 30 * d
    grain = 5 * _noise(u, v)
    c = rgba(base - 8 + grain, base + 6 + grain, base + 2 + grain, a)

    # 2) 冷银双层细边框（外亮内暗，做出金属厚度）
    if 0.900 < d < 0.940:
        c = rgba(228, 242, 238, a)
    elif 0.850 < d < 0.872:
        c = rgba(140, 168, 162, a)

    # 3) 细长四芒星：四尖朝上下左右，外圈暗描边避免星芒糊进底色
    rr = math.hypot(dx, dy)
    th = math.atan2(dy, dx)
    star = 0.045 + 0.36 * abs(math.cos(2 * th)) ** 9
    if rr < star + 0.018:
        c = rgba(132, 154, 144, a) if rr > star else rgba(250, 253, 247, a)

    # 4) 斜穿的槲寄生枝（左上 -> 右下）
    ax, ay, bx, by = 0.26, 0.70, 0.76, 0.32
    vx, vy = bx - ax, by - ay
    t = max(0.0, min(1.0, ((u - ax) * vx + (v - ay) * vy) / (vx * vx + vy * vy)))
    if math.hypot(u - (ax + t * vx), v - (ay + t * vy)) < 0.011:
        c = rgba(46, 82, 54, a)

    # 5) 三片叶：叶缘亮边 + 中脉
    ca, sa = math.cos(0.92), math.sin(0.92)
    for cx, cy, L, W in ((0.38, 0.60, 0.072, 0.030),
                         (0.55, 0.48, 0.082, 0.032),
                         (0.68, 0.39, 0.062, 0.026)):
        qx = (u - cx) * ca + (v - cy) * sa
        qy = -(u - cx) * sa + (v - cy) * ca
        q = (qx / L) ** 2 + (qy / W) ** 2
        if q < 1.0:
            c = rgba(96, 142, 88, a) if q > 0.72 else rgba(58, 104, 62, a)
            if abs(qy) < 0.0045:
                c = rgba(178, 214, 158, a)

    # 6) 三颗乳白浆果：高光 + 边缘阴影 + 脐点
    for cx, cy, R in ((0.34, 0.63, 0.030), (0.61, 0.44, 0.032), (0.72, 0.35, 0.027)):
        q = math.hypot(u - cx, v - cy)
        if q < R:
            c = rgba(250, 252, 246, a)
            if math.hypot(u - (cx - R * 0.30), v - (cy - R * 0.32)) < R * 0.38:
                c = rgba(255, 255, 255, a)
            if q > R * 0.82:
                c = rgba(168, 186, 172, a)
            if math.hypot(u - (cx + R * 0.12), v - (cy + R * 0.20)) < R * 0.14:
                c = rgba(126, 148, 128, a)
    return c''',
'''    dx, dy = (u - 0.5) * 2, (v - 0.5) * 2
    d = abs(dx) + abs(dy)
    if d > 0.995:
        return (0, 0, 0, 0)
    a = 255 if d < 0.94 else 255 * (0.995 - d) / 0.055

    # 1) 雪白偏蓝漆底：中心亮、边缘略暗，叠极细确定性颗粒
    base = 186 - 30 * d
    grain = 4 * _noise(u, v)
    c = rgba(base - 14 + grain, base + grain, base + 12 + grain, a)

    # 2) 冷银 / 冰蓝双细边框（做出金属厚度，小尺寸下仍能分出层次）
    if 0.900 < d < 0.940:
        c = rgba(238, 248, 255, a)
    elif 0.850 < d < 0.872:
        c = rgba(120, 158, 196, a)

    # 3) 六芒雪花：六条主臂 + 每条臂两对侧枝，中心六角芯
    rr = math.hypot(dx, dy)
    th = math.atan2(dy, dx)
    a6 = th % (math.pi / 3)
    arm = max(0.0, 1.0 - abs(math.sin(a6 * 3.0)) / 0.10)
    if rr < 0.60 and arm > 0:
        c = rgba(126, 170, 214, a) if rr > 0.55 else rgba(250, 253, 255, a)
    for k in range(6):
        ang = k * math.pi / 3
        for rad in (0.26, 0.40):
            for sg in (-1, 1):
                a2 = ang + sg * 0.40
                if math.hypot(dx - rad * math.cos(a2), dy - rad * math.sin(a2)) < 0.055:
                    c = rgba(214, 234, 252, a)
    if rr < 0.085:
        c = rgba(247, 251, 255, a) if rr < 0.055 else rgba(140, 178, 216, a)

    # 4) 少量霜化槲寄生：一根斜枝 + 两片叶 + 两颗白果（规格要求「少量」）
    ax, ay, bx, by = 0.30, 0.68, 0.72, 0.34
    vx, vy = bx - ax, by - ay
    t = max(0.0, min(1.0, ((u - ax) * vx + (v - ay) * vy) / (vx * vx + vy * vy)))
    if math.hypot(u - (ax + t * vx), v - (ay + t * vy)) < 0.010:
        c = rgba(92, 124, 146, a)
    ca, sa = math.cos(0.92), math.sin(0.92)
    for cx, cy, L, W in ((0.40, 0.60, 0.068, 0.028),
                         (0.63, 0.42, 0.062, 0.026)):
        qx = (u - cx) * ca + (v - cy) * sa
        qy = -(u - cx) * sa + (v - cy) * ca
        q = (qx / L) ** 2 + (qy / W) ** 2
        if q < 1.0:
            c = rgba(200, 224, 234, a) if q > 0.72 else rgba(116, 150, 164, a)
            if abs(qy) < 0.0045:
                c = rgba(228, 242, 248, a)
    for cx, cy, R in ((0.34, 0.64, 0.028), (0.68, 0.38, 0.026)):
        q = math.hypot(u - cx, v - cy)
        if q < R:
            c = rgba(252, 254, 255, a)
            if q > R * 0.82:
                c = rgba(160, 186, 206, a)
            if math.hypot(u - (cx + R * 0.12), v - (cy + R * 0.20)) < R * 0.14:
                c = rgba(118, 150, 178, a)
    return c''')

# ---------------------------------------------------------------- 6. 刀镡几何：四瓣 -> 六角雪花
sub("镡轮廓",
'''# 四瓣花形刀镡
m.use("blade")
N = 40
front, back = [], []
for i in range(N):
    th = i / N * math.pi * 2
    r = 15.0 * (0.79 + 0.21 * math.cos(4 * th))''',
'''# 初雪：六角雪花刀镡。顶点数 / 面数 / 组名 / UV 布局与朝露完全一致，
# 只把极坐标半径从 4 次花瓣改成 6 次尖角，最大半径仍为 15，装配关系不变。
m.use("blade")
N = 40
front, back = [], []
for i in range(N):
    th = i / N * math.pi * 2
    r = 15.0 * (0.78 + 0.22 * abs(math.cos(3 * th)) ** 3)''')

sub("镡正面 UV",
'''        rr = 0.44 * (0.79 + 0.21 * math.cos(4 * th))''',
'''        rr = 0.44 * (0.78 + 0.22 * abs(math.cos(3 * th)) ** 3)''')

sub("图标镡六角",
'''    icon_disc(g, 2, 0, 7.6, 2.4, "tsuba", n=8)
    icon_disc(g, 48, 0, 14.0, 3.0, "tsuba", n=12)''',
'''    icon_disc(g, 2, 0, 7.6, 2.4, "tsuba", n=6)      # 六角雪花镡
    icon_disc(g, 48, 0, 14.0, 3.0, "tsuba", n=6)    # 柄头同步六角''')

# ---------------------------------------------------------------- 7. 对照图带上初雪档
sub("鞘对照档",
'''    labels = ("BEFORE", "WHITE", "GREEN", "DEEP GREEN", "BLACK")
    tones = ("white", "white", "green", "deepgreen", "black")''',
'''    labels = ("BEFORE", "FIRST SNOW", "GREEN", "DEEP GREEN", "BLACK")
    tones = ("white", "firstsnow", "green", "deepgreen", "black")''')

# ---------------------------------------------------------------- 8. 图标预览：耐久层示意色改成真实冷调
# 离线预览里那片「橄榄绿」只是旧演示取色，不是本体真实颜色，更不是初雪贴图。
# 实测 SlashBladeTEISR:104 仅在 ItemDisplayContext.GUI 传 renderDurability=true，
# setCol = lerp(Color(0.25,0.25,0.25) -> 0xA52A63 紫红, 破损度)，
# 再乘 durability.png 的 4x4 色块；GROUND / FIXED / 手持均不画这层。
sub("预览背景冷调",
'''    bg = rgba(161, 178, 157)''',
'''    bg = rgba(178, 188, 202)''')

sub("耐久层示意色",
'''        # 原版耐久菱形底板：紫描边 -> 青蓝框 -> 橄榄底
        diamond(sheet, cx, cy, 79, rgba(58, 42, 110))
        diamond(sheet, cx, cy, 75, rgba(47, 195, 227))
        diamond(sheet, cx, cy, 67, rgba(80, 71, 32))
        for k in range(10):                     # 底板上一点点深浅斑驳
            bx = cx - 44 + (k * 37) % 88
            by = cy - 40 + (k * 53) % 80
            sheet.blend(bx, by, rgba(60, 54, 25, 150))''',
'''        # 本体耐久菱形层（仅 GUI 绘制，见 SlashBladeTEISR:104）：
        # 颜色为 lerp(深灰 -> 紫红) 乘 durability.png，不受初雪主图集控制。
        # 这里按满耐久态用冷灰紫示意；清单第 84 行把它列为独立的程序接入任务。
        diamond(sheet, cx, cy, 79, rgba(52, 44, 96))
        diamond(sheet, cx, cy, 75, rgba(72, 154, 208))
        diamond(sheet, cx, cy, 67, rgba(66, 70, 92))
        for k in range(10):                     # 底板上一点点深浅斑驳
            bx = cx - 44 + (k * 37) % 88
            by = cy - 40 + (k * 53) % 80
            sheet.blend(bx, by, rgba(48, 52, 76, 150))''')

sub("徽记预览背景",
'''                   rgba(150, 168, 158, 255))''',
'''                   rgba(178, 188, 202, 255))''')

# ---------------------------------------------------------------- 9. 图标刀描边（16x16 辨识度）
# 概念图 image4 的背板是冷白/冰蓝浅色，交叉刀靠深色描边分出来。
# 只靠贴图色差的话，缩到 16x16 时刀会与背板糊成一块（清单第 88 行）。
sub("icon_plate 描边",
'''def icon_plate(group, s0, s1, w0, w1, z, tile, u0=0.0, u1=1.0, segs=4):
    """朝 +Z 的渐变宽度长条。绕序已验证法线 +Z，GUI 旋转后仍朝向相机。"""
    m.use(group)
    for i in range(segs):
        a, b = i / segs, (i + 1) / segs
        sa, sb = s0 + (s1 - s0) * a, s0 + (s1 - s0) * b
        wa, wb = w0 + (w1 - w0) * a, w0 + (w1 - w0) * b
        ua, ub = u0 + (u1 - u0) * a, u0 + (u1 - u0) * b
        m.quad([ipt(sa, -wa, z), ipt(sb, -wb, z),
                ipt(sb, wb, z), ipt(sa, wa, z)],
               [(ua, 0), (ub, 0), (ub, 1), (ua, 1)], tile)''',
'''def icon_plate(group, s0, s1, w0, w1, z, tile, u0=0.0, u1=1.0, segs=4, outline=0.0):
    """朝 +Z 的渐变宽度长条。outline>0 时先铺一圈更宽更深的冷灰描边。

    描边在 z-0.12、与主体不共面；16x16 缩略下靠这圈暗边把冷白刀
    从冷白菱形背板里分出来（概念图 image4 的做法）。
    """
    m.use(group)
    if outline > 0:
        for i in range(segs):
            a, b = i / segs, (i + 1) / segs
            sa, sb = s0 + (s1 - s0) * a, s0 + (s1 - s0) * b
            wa = w0 + (w1 - w0) * a + outline
            wb = w0 + (w1 - w0) * b + outline
            m.quad([ipt(sa, -wa, z - 0.12), ipt(sb, -wb, z - 0.12),
                    ipt(sb, wb, z - 0.12), ipt(sa, wa, z - 0.12)],
                   [(a, 0), (b, 0), (b, 1), (a, 1)], "branch")
    for i in range(segs):
        a, b = i / segs, (i + 1) / segs
        sa, sb = s0 + (s1 - s0) * a, s0 + (s1 - s0) * b
        wa, wb = w0 + (w1 - w0) * a, w0 + (w1 - w0) * b
        ua, ub = u0 + (u1 - u0) * a, u0 + (u1 - u0) * b
        m.quad([ipt(sa, -wa, z), ipt(sb, -wb, z),
                ipt(sb, wb, z), ipt(sa, wa, z)],
               [(ua, 0), (ub, 0), (ub, 1), (ua, 1)], tile)''')

sub("icon_cross 局部 plate 描边",
'''    def plate(s0, s1, w0, w1, tile, segs=6, zz=CROSS_Z):
        m.use(group)
        for i in range(segs):
            a, b = i / segs, (i + 1) / segs
            sa, sb = s0 + (s1 - s0) * a, s0 + (s1 - s0) * b
            wa, wb = w0 + (w1 - w0) * a, w0 + (w1 - w0) * b
            m.quad([iptx(sa, -wa, zz), iptx(sb, -wb, zz),
                    iptx(sb, wb, zz), iptx(sa, wa, zz)],
                   [(a, 0), (b, 0), (b, 1), (a, 1)], tile)''',
'''    def plate(s0, s1, w0, w1, tile, segs=6, zz=CROSS_Z, outline=0.0):
        m.use(group)
        if outline > 0:
            for i in range(segs):
                a, b = i / segs, (i + 1) / segs
                sa, sb = s0 + (s1 - s0) * a, s0 + (s1 - s0) * b
                wa = w0 + (w1 - w0) * a + outline
                wb = w0 + (w1 - w0) * b + outline
                m.quad([iptx(sa, -wa, zz - 0.12), iptx(sb, -wb, zz - 0.12),
                        iptx(sb, wb, zz - 0.12), iptx(sa, wa, zz - 0.12)],
                       [(a, 0), (b, 0), (b, 1), (a, 1)], "branch")
        for i in range(segs):
            a, b = i / segs, (i + 1) / segs
            sa, sb = s0 + (s1 - s0) * a, s0 + (s1 - s0) * b
            wa, wb = w0 + (w1 - w0) * a, w0 + (w1 - w0) * b
            m.quad([iptx(sa, -wa, zz), iptx(sb, -wb, zz),
                    iptx(sb, wb, zz), iptx(sa, wa, zz)],
                   [(a, 0), (b, 0), (b, 1), (a, 1)], tile)''')

sub("主鞘描边",
'''        icon_plate(g, 60, 174, 6.4, 3.0, 2.0, "sheath", segs=8)''',
'''        icon_plate(g, 60, 174, 6.4, 3.0, 2.0, "sheath", segs=8, outline=2.4)''')

sub("主刃描边",
'''        icon_plate(g, 60, tip - 8, 5.6, 2.8 if not broken else 4.4,
                   2.0, "blade", segs=8)
        icon_plate(g, tip - 8, tip, 2.8 if not broken else 4.4,
                   0.6 if not broken else 3.2, 2.0, "blade", segs=1)''',
'''        icon_plate(g, 60, tip - 8, 5.6, 2.8 if not broken else 4.4,
                   2.0, "blade", segs=8, outline=2.4)
        icon_plate(g, tip - 8, tip, 2.8 if not broken else 4.4,
                   0.6 if not broken else 3.2, 2.0, "blade", segs=1, outline=1.8)''')

sub("副刃描边",
'''    plate(50, tip, 4.6, 3.0 if broken else 1.0, "blade", segs=8)''',
'''    plate(50, tip, 4.6, 3.0 if broken else 1.0, "blade", segs=8, outline=2.0)''')

# ---------------------------------------------------------------- 执行替换
text = SRC
bad = []
for tag, old, new in R:
    n = text.count(old)
    if n != 1:
        bad.append((tag, n))
    else:
        text = text.replace(old, new, 1)
if bad:
    for tag, n in bad:
        print("  锚点异常 [%s]: 原文出现 %d 次（应为 1）" % (tag, n))
    raise SystemExit("共 %d 项需修正，未写出文件" % len(bad))

out = DST_DIR / "make_first_snow.py"
out.write_text(text, encoding="utf-8")
print("派生完成:", out, out.stat().st_size, "B")
print("替换项:", len(R))
for tag, _, _ in R:
    print("  -", tag)
