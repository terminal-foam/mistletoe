#!/usr/bin/env python3
"""从 v1.1 权威生成器派生「六瓣雪花镡」候选版。

只做 5 处定点替换，全部带命中次数断言；贴图与几何共用同一条轮廓曲线。
不改图标 item_*（16x16 保留六角剪影，深 V 谷在图标尺寸下只会变糊）。
"""
from pathlib import Path

HERE = Path(__file__).resolve().parent
SRC = HERE / "pipeline_snow" / "make_first_snow.py"
s = SRC.read_text(encoding="utf-8")


def rep(old, new, tag):
    global s
    n = s.count(old)
    assert n == 1, "%s: 命中 %d 次（期望 1）" % (tag, n)
    s = s.replace(old, new, 1)
    print("  [ok] %s" % tag)


rep('"""槲寄生[初雪] first_snow 美术资源生成器 v1.1（纯标准库，权威源）。',
    '"""槲寄生[初雪] first_snow —— 六瓣雪花镡候选版（v1.1 派生，纯标准库）。',
    "文档头")

# 轮廓函数必须在 t_tsuba 之前定义：图集生成阶段就会调用贴图函数。
rep("AW = AH = 1024\n", '''# ---- 六瓣雪花轮廓：贴图与几何共用同一条曲线 ----
# s 是 0..1 的六次平滑波；power<1 让瓣顶变宽、谷口收窄。
# depth 是「雪花感」的主旋钮：0.32 = 谷底半径 6.53 / 瓣端 9.60。
SNOW_DEPTH, SNOW_POWER = 0.32, 0.70


def snow_profile(th):
    return (1.0 - SNOW_DEPTH) + SNOW_DEPTH * (
        (0.5 + 0.5 * math.cos(6 * th)) ** SNOW_POWER)


AW = AH = 1024
''', "轮廓函数")

rep('''    # 外圈双细边跟随圆钝六角轮廓（与几何 tsuba_profile 同一条曲线）
    hex_edge = 0.93 + 0.07 * math.cos(6 * th)
    if abs(r - hex_edge) < 0.042:
        c = rgba(226, 240, 252)
    elif abs(r - 0.34) < 0.028:
        c = rgba(126, 158, 188)
''', '''    # 外圈亮边跟随六瓣轮廓；UV 采样半径 = 0.88 * profile
    snow_edge = 0.88 * snow_profile(th)
    if abs(r - snow_edge) < 0.040:
        c = rgba(226, 240, 252)
    elif abs(r - 0.34) < 0.028:
        c = rgba(126, 158, 188)
    # 六瓣冰蓝嵌面：只在瓣内，随深 V 谷自动收窄
    if 0.30 < r < 0.80 * snow_edge and abs(math.sin(3 * th)) < 0.30:
        c = rgba(158, 196, 228)
''', "贴图轮廓与冰蓝嵌面")

rep('''N = 48
TSUBA_R = 9.6


def tsuba_profile(th):
    """圆钝六角：无尖角、无悬空细刺；0.93..1.00 的六次缓起伏。"""
    return 0.93 + 0.07 * math.cos(6 * th)
''', '''N = 96               # 六瓣深 V 谷需要更密角向采样，避免谷口折出锯齿
TSUBA_R = 9.6


def tsuba_profile(th):
    """六瓣雪花：瓣顶宽平、瓣间深 V 谷；全程连续实心、无悬空尖刺。"""
    return snow_profile(th)
''', "几何轮廓与采样密度")

rep('''    m.quad(p, uv, "tsuba", both=True)

# effect 组：Resharped renderChargeEffect 用的充能外壳。''', '''    m.quad(p, uv, "tsuba", both=True)
    # 每臂一对侧枝：从主臂斜出，同样贴盘面、无悬空端点。
    # 端点 r=7.5 落在瓣内（该角度轮廓半径约 8.1），不会越出盘缘。
    for sign in (-1, 1):
        a1 = a + sign * 0.30
        c1, s1 = math.cos(a1), math.sin(a1)
        rb0, rb1, hb = 5.7, 7.5, 0.62
        q = [(SNOW_X, rb0 * ca - hb * sa, rb0 * sa + hb * ca),
             (SNOW_X, rb1 * c1 - hb * s1 * 0.5, rb1 * s1 + hb * c1 * 0.5),
             (SNOW_X, rb1 * c1 + hb * s1 * 0.5, rb1 * s1 - hb * c1 * 0.5),
             (SNOW_X, rb0 * ca + hb * sa, rb0 * sa - hb * ca)]
        m.quad(q, uv, "tsuba", both=True)

# effect 组：Resharped renderChargeEffect 用的充能外壳。''', "贴面侧枝浮雕")

SRC.write_text(s, encoding="utf-8")
print("patched:", SRC, SRC.stat().st_size, "B")
