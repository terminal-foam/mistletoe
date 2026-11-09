#!/usr/bin/env python3
"""给初雪刀鞘换上专属纹样（v1.2rc 第二处收敛）。

结论与刀镡相反：刀鞘不是被削平，而是把暮色/朝露的「满装饰鞘」整套继承
下来只换了冷色 —— 5 道箍、整圈菱纹缠绳、整圈交叉结绳、4 组单片叶、
纵贯金线、满鞘斜擦痕与霜晶。规格 2.2 要的是「少量、细线化、不要覆盖
整个鞘身」，参考图 03 要的是「霜化的槲寄生枝叶与冰珠」+「银色金属件
加淡蓝系绑绳」。这里给 firstsnow 档单独走一张克制纹样，其余四个 tone
完全不动。
"""
from pathlib import Path

HERE = Path(__file__).resolve().parent
SRC = HERE / "pipeline_snow" / "make_first_snow.py"
s = SRC.read_text(encoding="utf-8")

NEW_FUNC = '''
def _fs_spine(u):
    """初雪鞘面那一枝槲寄生的主枝中心线（v 坐标）。"""
    t = (u - 0.085) / 0.340
    return 0.315 - 0.105 * t + 0.012 * math.sin(t * math.pi * 2)


def t_sheath_firstsnow(u, v):
    """初雪专属刀鞘纹样（参考图 03「刀鞘纹样 / 刀鞘装饰」+ 规格 2.2）。

    大面积冷白灰留白，只在鞘口到中段画一枝细线化霜化槲寄生 + 冰珠，
    中段一个局部淡蓝绑绳结，三道窄银箍。规格要求「少量、细线化、
    不要覆盖整个鞘身」。下垂流苏是立体挂件，贴图无法表现，需要另做
    sheath 组几何（未包含在本版）。
    """
    # 1) 漆底：冷白灰/淡灰蓝 + 柔和反光带 + 极轻鮫皮颗粒；不做满鞘斜擦痕
    g = 186 + 20 * (1 - v)
    sheen = 26 * math.exp(-((v - 0.34) / 0.19) ** 2)
    rim = 22 * math.exp(-((v - 0.86) / 0.055) ** 2)
    grain = _noise(u, v) * 2.5
    r = g * 0.95 + sheen + rim * 0.90 + grain
    gg = g + 6 + sheen + rim * 0.95 + grain
    b = g * 1.00 + sheen + rim + grain
    c = rgba(r, gg, b)

    # 2) 极稀疏六向冰晶细纹：约 1/11 的格子才有一枚，强度仅 9
    cu, cv = u * 9.0, v * 7.0
    iu, iv = int(cu), int(cv)
    if 0.10 < u < 0.95 and (iu * 5 + iv * 3) % 11 == 0:
        fu, fv = cu - iu - 0.5, cv - iv - 0.5
        a6 = math.atan2(fv, fu)
        rad = math.hypot(fu, fv)
        spoke = min(abs(math.sin(a6 * 3.0)), abs(math.cos(a6 * 3.0)))
        crystal = max(0.0, 1.0 - spoke / 0.10) * max(0.0, 1.0 - rad / 0.40)
        if crystal > 0.0:
            k = 9.0 * crystal
            c = rgba(r + k * 0.60, gg + k * 0.85, b + k)

    # 3) 三道窄银箍：鞘口 / 绑绳处 / 鞘尾
    for hc, hw in ((0.022, 0.013), (0.615, 0.011), (0.975, 0.015)):
        d = abs(u - hc)
        if d < hw * 1.7:
            if d >= hw:
                c = rgba(r - 14, gg - 14, b - 14)
            else:
                q = d / hw
                if q > 0.86 or q < 0.12:
                    c = rgba(96, 118, 142)
                elif 0.26 < q < 0.40 or 0.62 < q < 0.76:
                    c = rgba(232, 244, 253)
                else:
                    c = rgba(158, 180, 204)
            break

    # 4) 淡蓝绑绳：只在正面 v≈0.25 局部，两道束带 + 蝴蝶结 + 中心结。
    #    不绕整圈，避免变成暮色那种贯穿全 v 的整圈结绳带。
    if 0.10 < v < 0.42:
        du, dv = u - 0.616, v - 0.255
        for cu0 in (0.598, 0.634):
            if abs(u - cu0) < 0.005:
                c = rgba(92, 122, 158)
            elif abs(u - cu0) < 0.009:
                c = rgba(158, 186, 214)
        for sgn in (-1, 1):
            lu = (du - sgn * 0.032) / 0.030
            lv = dv / 0.052
            q = lu * lu + lv * lv
            if 0.55 < q < 1.0:
                c = rgba(146, 176, 208)
            elif 0.42 < q <= 0.55:
                c = rgba(200, 220, 238)
        q = (du / 0.012) ** 2 + (dv / 0.020) ** 2
        if q < 1.0:
            c = rgba(112, 144, 180) if q > 0.60 else rgba(178, 204, 230)
        # 两条短飘带 + 珠：贴图内的示意，真实下垂流苏仍需 sheath 组几何
        for sgn in (-1, 1):
            ub = 0.616 + sgn * 0.012 + (v - 0.27) * 0.010 * sgn
            if 0.27 < v < 0.39 and abs(u - ub) < 0.0035:
                c = rgba(146, 176, 208)
            qb = ((u - (0.616 + sgn * 0.014)) / 0.009) ** 2 + ((v - 0.400) / 0.012) ** 2
            if qb < 1.0:
                c = rgba(238, 248, 255) if qb < 0.55 else rgba(158, 188, 216)

    # 5) 一枝细线化霜化槲寄生：斜出细长柳叶（不是圆片）+ 4 颗冰珠
    if 0.075 < u < 0.435:
        spine = _fs_spine(u)
        if abs(v - spine) < 0.0040:
            c = rgba(112, 140, 154)
        for k in range(6):
            lu0 = 0.108 + k * 0.053
            sv0 = _fs_spine(lu0)
            for sgn in (-1, 1):
                # 叶长轴沿枝斜出、短轴很窄，旋转坐标让上下两排朝相反方向倾
                ec_u = lu0 + sgn * 0.012
                ec_v = sv0 + sgn * 0.028
                du2, dv2 = u - ec_u, v - ec_v
                along = du2 + sgn * 0.55 * dv2
                across = dv2 - sgn * 0.55 * du2
                q = (along / 0.030) ** 2 + (across / 0.009) ** 2
                if q < 1.0:
                    if q > 0.74:
                        c = rgba(206, 226, 234)
                    elif abs(across) < 0.0020:
                        c = rgba(230, 242, 248)
                    else:
                        c = rgba(158, 186, 196)
        for bu, bv in ((0.150, 0.285), (0.238, 0.262),
                       (0.330, 0.240), (0.398, 0.228)):
            q = ((u - bu) / 0.010) ** 2 + ((v - bv) / 0.011) ** 2
            if q < 1.0:
                if q > 0.78:
                    c = rgba(172, 194, 210)
                elif ((u - bu + 0.003) / 0.005) ** 2 + ((v - bv + 0.003) / 0.005) ** 2 < 1.0:
                    c = rgba(255, 255, 255)
                else:
                    c = rgba(246, 251, 255)
    return c


'''


def rep(old, new, tag):
    global s
    n = s.count(old)
    assert n == 1, "%s: 命中 %d 次（期望 1）" % (tag, n)
    s = s.replace(old, new, 1)
    print("  [ok] %s" % tag)


# 新函数插在 t_sheath 之前（此时 rgba / _noise 均已定义）
rep("def t_sheath(u, v):\n", NEW_FUNC + "def t_sheath(u, v):\n", "注入初雪专属纹样函数")

# firstsnow 档走新函数；原 firstsnow 调色板分支保留为回退备用，不再执行
rep('''    四档共用同一套装饰结构，只换底色与各部件对比色。
    """
    if TONE == "firstsnow":''', '''    四档共用同一套装饰结构，只换底色与各部件对比色。
    """
    # 初雪走专属克制纹样；下方 firstsnow 调色板分支保留供回退/对照。
    if TONE == "firstsnow":
        return t_sheath_firstsnow(u, v)
    if TONE == "firstsnow":''', "接线到 firstsnow 档")

SRC.write_text(s, encoding="utf-8")
print("patched:", SRC, SRC.stat().st_size, "B")
