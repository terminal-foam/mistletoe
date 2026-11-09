#!/usr/bin/env python3
"""槲寄生[初雪] first_snow —— 六瓣雪花镡候选版（v1.1 派生，纯标准库）。

重要：本文件是**唯一权威生成器**，不依赖 make_demo.py、derive_first_snow.py
或任何仓库内路径。v1.0 曾用 derive 脚本从 make_demo.py 做文本派生，但那份
基础脚本不随交付包分发，导致「解压后无法复现」。v1.1 起整条链自包含：
    cd first_snow_pipeline && python build_all.py
即可从任意当前目录重生成 out/ 下全部交付物（含 OBJ / 图集 / 预览 / 校验）。

血缘：刀身、刀鞘、反り曲线、13 组名、UV 布局沿用朝露·暮色（ref/dawn.obj
是仓库基准快照，仅供 check.py 做几何血缘比对，不参与生成）。

v1.1 造型收敛（对应评审意见）：
  * 刀镡从半径 15 的六瓣尖角盘改为半径 9.6 的圆钝六角盘；
  * 删除从镡外缘伸出的枝条 / 叶片 / 白果 —— 那正是规格禁止的悬空尖刺；
  * 六角雪花改为贴盘面的浅浮雕，霜化槲寄生与冰珠只留在贴图里；
  * item_* 图标去掉枝叶白果、加粗主体与描边，BROKEN 改深底单条断刀。

产出（全部落在 out/）：
  demo_atlas.png      1024x1024 UV 图集（16 个 256px 分区）
  demo_model.obj      带 UV / 分组的 OBJ（对齐 SlashBlade Resharped 组名）
  demo_render.png     OBJ 的软件渲染预览（证明 UV 与贴图是对得上的）
  demo_particles.png  5 类粒子 / 特效贴图接触表
  demo_saya_compare.png  刀鞘贴图放大对照（旧版 / white / green / deepgreen / black）
  demo_icon.png      物品栏两态图标对照（SHEATHED / BROKEN，X 交叉）
  demo_icon_single.png   --icon=single 的单刀构图对照
  demo_emblem.png    物品栏菱形徽记背板放大预览
"""
import math
import os
import struct
import sys
import zlib
from pathlib import Path

# 自包含流水线：所有产物落在脚本同级的 out/，不依赖工作目录，
# 也不依赖任何仓库内路径或 make_demo.py / derive 脚本。
OUT = Path(__file__).resolve().parent / "out"
OUT.mkdir(parents=True, exist_ok=True)
# ---- 六瓣雪花轮廓：贴图与几何共用同一条曲线 ----
# s 是 0..1 的六次平滑波；power<1 让瓣顶变宽、谷口收窄。
# depth 是「雪花感」的主旋钮：0.32 = 谷底半径 6.53 / 瓣端 9.60。
SNOW_DEPTH, SNOW_POWER = 0.32, 0.70


def snow_profile(th):
    return (1.0 - SNOW_DEPTH) + SNOW_DEPTH * (
        (0.5 + 0.5 * math.cos(6 * th)) ** SNOW_POWER)


AW = AH = 1024
TILE = 256
INSET = 8              # 每个分区四周内缩像素，防相邻分区渗色（比例同旧版 4/128）

# 刀鞘配色档位：white=浅青瓷白 / green=翠绿白反光 / deepgreen=深森林绿漆 /
# black=近黑墨绿漆。四档共用同一套装饰结构，只换漆底与配件的调色板。
# 用法：python make_demo.py --tone=white|green|deepgreen|black（默认 white），
# 也可用环境变量 SHEATH_TONE。改完再跑 build_release.py 就会更新进仓库版贴图。
TONES = ("firstsnow", "white", "green", "deepgreen", "black")


def _read_tone():
    for a in sys.argv[1:]:
        if a.startswith("--tone="):
            return a.split("=", 1)[1]
    return os.environ.get("SHEATH_TONE", "white")


TONE = "firstsnow"   # 初雪是独立武器，配色固定；其余档位只用于对照图
if TONE not in TONES:
    raise SystemExit("未知 --tone: %s（可用 %s）" % (TONE, "/".join(TONES)))


def clamp8(x):
    return max(0, min(255, int(x)))


def rgba(r, g, b, a=255):
    return (clamp8(r), clamp8(g), clamp8(b), clamp8(a))


class Canvas:
    def __init__(self, w, h, bg=(0, 0, 0, 0)):
        self.w, self.h = w, h
        self.buf = bytearray(w * h * 4)
        if bg[3]:
            for i in range(0, len(self.buf), 4):
                self.buf[i:i + 4] = bytes(bg)

    def set(self, x, y, c):
        x, y = int(x), int(y)
        if 0 <= x < self.w and 0 <= y < self.h:
            i = (y * self.w + x) * 4
            self.buf[i:i + 4] = bytes(c)

    def get(self, x, y):
        x, y = int(x), int(y)
        if 0 <= x < self.w and 0 <= y < self.h:
            i = (y * self.w + x) * 4
            return tuple(self.buf[i:i + 4])
        return (0, 0, 0, 0)

    def blend(self, x, y, c):
        if c[3] >= 255:
            self.set(x, y, c)
            return
        if c[3] <= 0:
            return
        old = self.get(x, y)
        a = c[3] / 255.0
        oa = old[3] / 255.0
        na = a + oa * (1 - a)
        if na <= 0:
            return
        out = tuple((c[k] * a + old[k] * oa * (1 - a)) / na for k in range(3))
        self.set(x, y, rgba(*out, na * 255))

    def save(self, path):
        raw = bytearray()
        for y in range(self.h):
            raw.append(0)
            raw.extend(self.buf[y * self.w * 4:(y + 1) * self.w * 4])

        def chunk(tag, data):
            return (struct.pack(">I", len(data)) + tag + data
                    + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

        png = b"\x89PNG\r\n\x1a\n"
        png += chunk(b"IHDR", struct.pack(">IIBBBBB", self.w, self.h, 8, 6, 0, 0, 0))
        png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        png += chunk(b"IEND", b"")
        Path(path).write_bytes(png)


def fill_tri(cv, p0, p1, p2, color):
    x0, y0 = p0
    x1, y1 = p1
    x2, y2 = p2
    minx = max(0, int(min(x0, x1, x2)))
    maxx = min(cv.w - 1, int(max(x0, x1, x2)) + 1)
    miny = max(0, int(min(y0, y1, y2)))
    maxy = min(cv.h - 1, int(max(y0, y1, y2)) + 1)
    d = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
    if abs(d) < 1e-9:
        return
    for y in range(miny, maxy + 1):
        py = y + 0.5
        for x in range(minx, maxx + 1):
            px = x + 0.5
            l0 = ((y1 - y2) * (px - x2) + (x2 - x1) * (py - y2)) / d
            l1 = ((y2 - y0) * (px - x2) + (x0 - x2) * (py - y2)) / d
            l2 = 1.0 - l0 - l1
            if l0 >= -1e-6 and l1 >= -1e-6 and l2 >= -1e-6:
                cv.blend(x, y, color)


def fill_tri_tex(cv, sp, uvs, depths, shade, atlas, zbuf):
    """逐像素插值 UV 采样图集 + z-buffer，与 GPU 的行为一致。"""
    (x0, y0), (x1, y1), (x2, y2) = sp
    minx = max(0, int(min(x0, x1, x2)))
    maxx = min(cv.w - 1, int(max(x0, x1, x2)) + 1)
    miny = max(0, int(min(y0, y1, y2)))
    maxy = min(cv.h - 1, int(max(y0, y1, y2)) + 1)
    d = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
    if abs(d) < 1e-9:
        return
    u0, u1, u2 = uvs
    for y in range(miny, maxy + 1):
        py = y + 0.5
        for x in range(minx, maxx + 1):
            px = x + 0.5
            l0 = ((y1 - y2) * (px - x2) + (x2 - x1) * (py - y2)) / d
            l1 = ((y2 - y0) * (px - x2) + (x0 - x2) * (py - y2)) / d
            l2 = 1.0 - l0 - l1
            if l0 < -1e-6 or l1 < -1e-6 or l2 < -1e-6:
                continue
            z = l0 * depths[0] + l1 * depths[1] + l2 * depths[2]
            idx = y * cv.w + x
            if z + 1e-6 < zbuf[idx]:
                continue
            gu = l0 * u0[0] + l1 * u1[0] + l2 * u2[0]
            gv = l0 * u0[1] + l1 * u1[1] + l2 * u2[1]
            r, g, b, a = atlas.get(int(gu * AW), int((1 - gv) * AH))
            if a:
                cv.blend(x, y, rgba(r * shade, g * shade, b * shade, a))
                if a >= 250:
                    zbuf[idx] = z


# ---------------------------------------------------------------- 图集分区函数
def t_blade(u, v):
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
    return c


def t_blade_lum(u, v):
    # 冰蓝加色层：浅底上压低 alpha，避免把冷白刃刷成一团白
    e = math.exp(-((v - 0.18) / 0.22) ** 2)
    return rgba(169 + 46 * e, 216 + 24 * e, 255, 150 * e)


def t_handle(u, v):
    # 初雪柄卷：保留系列菱纹编织结构，改为冷灰蓝底 + 雪白编织线
    band = int(u * 14)
    a = (u * 14 + v * 6) % 1.0
    b = (u * 14 - v * 6) % 1.0
    d = min(abs(a - 0.5), abs(b - 0.5))
    if d > 0.42:
        return rgba(232, 240, 250)
    if d > 0.33:
        return rgba(108, 134, 162)
    return rgba(48, 66, 86) if band % 2 == 0 else rgba(62, 82, 104)


def t_tsuba(u, v):
    """初雪刀镡：冷银底 + 六角雪花纹，与 blade 组内的六角轮廓同步。"""
    dx, dy = (u - 0.5) * 2, (v - 0.5) * 2
    r = math.hypot(dx, dy)
    th = math.atan2(dy, dx)
    base = 178 - 34 * r
    c = rgba(base - 6, base + 8, base + 20)

    # 外圈亮边跟随六瓣轮廓；UV 采样半径 = 0.88 * profile
    snow_edge = 0.88 * snow_profile(th)
    if abs(r - snow_edge) < 0.040:
        c = rgba(226, 240, 252)
    elif abs(r - 0.34) < 0.028:
        c = rgba(126, 158, 188)
    # 六瓣冰蓝嵌面：只在瓣内，随深 V 谷自动收窄
    if 0.30 < r < 0.80 * snow_edge and abs(math.sin(3 * th)) < 0.30:
        c = rgba(158, 196, 228)

    # 简洁六向雪花主枝：无细碎分叉、无悬空小刺
    a6 = th % (math.pi / 3)
    spoke = abs(math.sin(a6 * 3.0))
    if r < 0.70 and spoke < 0.075:
        c = rgba(238, 248, 255) if r < 0.60 else rgba(150, 190, 224)
    # 中心六角芯
    if r < 0.10:
        c = rgba(232, 244, 255) if r < 0.065 else rgba(120, 152, 184)
    return c


# 刀鞘装饰参数：u = 沿鞘长（0 鞘口 -> 1 鞘尾），v = 绕截面一周
# （0 上缘 / 0.25 正面 / 0.5 下缘 / 0.75 背面）。
# 概念图第 2/3/6 栏：鞘口与鞘尾各一道宽金属箍、中段三道窄箍；鞘口一小段
# 菱纹缠绳；约 6 成处一束斜交结绳；漆面带长斜擦痕；SA 态长出槲寄生叶果。
SAYA_HOOPS = ((0.020, 0.017), (0.170, 0.010), (0.445, 0.009),
              (0.735, 0.009), (0.973, 0.017))
SAYA_KNOT = (0.545, 0.662)
SAYA_WRAP = (0.038, 0.150)
SAYA_SPRIGS = ((0.255, 0.27), (0.335, 0.72), (0.805, 0.24), (0.865, 0.73))
SAYA_BERRIES = ((-0.014, -0.032), (-0.018, 0.018), (-0.006, 0.042))


def _lerp3(a, b, t):
    return (a[0] + (b[0] - a[0]) * t,
            a[1] + (b[1] - a[1]) * t,
            a[2] + (b[2] - a[2]) * t)


def _noise(u, v):
    """确定性细颗粒（漆面鮫皮质感），保证每次生成的图集字节一致。"""
    h = ((int(u * 1531) * 73856093) ^ (int(v * 1531) * 19349663)) & 255
    return h / 255.0 - 0.5



def _fs_spine(u):
    """初雪鞘面那一枝槲寄生的主枝中心线（v 坐标）。"""
    t = (u - 0.085) / 0.340
    return 0.315 - 0.105 * t + 0.012 * math.sin(t * math.pi * 2)


def t_sheath_firstsnow(u, v):
    """初雪专属刀鞘纹样（参考图 03「刀鞘纹样 / 刀鞘装饰」+ 规格 2.2）。

    大面积冷白灰留白，只在鞘口到中段画一枝细线化霜化槲寄生 + 冰珠，
    中段一个局部淡蓝绑绳结，三道窄银箍。规格要求「少量、细线化、
    不要覆盖整个鞘身」。r3 起下垂的两条流苏与冰珠改由 sheath 组立体
    几何表现，此处不再画贴面飘带示意，避免与真实挂件在同一位置重影。
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
        # 两条下垂流苏与末端冰珠已改为 3D 挂件（见下方 sheath 组几何），
        # 这里只保留结心，不再画贴面飘带，防止同一位置出现重影。

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


def t_sheath(u, v):
    """概念图风格的漆鞘：分层画出漆面 / 金线 / 菱纹缠绳 / 金属箍 / 结绳 / 叶果。

    所有装饰都画进同一张图集分区，不改组名、不加渲染层；256px 分区下
    每道箍、每根绳股、每颗白果都有明暗与纹理，而不只是色块。
    四档共用同一套装饰结构，只换底色与各部件对比色。
    """
    # 初雪走专属克制纹样；下方 firstsnow 调色板分支保留供回退/对照。
    if TONE == "firstsnow":
        return t_sheath_firstsnow(u, v)
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
        g, amp, rim_amp = 20 + 16 * (1 - v), 30, 20
        wr, wg, wb, bias = 0.60, 1.00, 0.70, 12
        gold, edge = (188, 162, 100), (176, 190, 172)
        metal, metal_hi, metal_dk = (96, 86, 62), (214, 196, 140), (38, 36, 28)
        cord, cord_hi, cord_dk = (16, 24, 20), (118, 132, 112), (6, 10, 8)
        wrap_white, wrap_dark = (206, 218, 206), (24, 44, 34)
        leaf, leaf_dk, leaf_hi, vein = (92, 136, 78), (34, 66, 40), (148, 186, 116), (176, 206, 148)
        berry, berry_sh, berry_hi, berry_dot = (240, 246, 232), (128, 146, 134), (255, 255, 252), (150, 138, 96)
        contrast = 1.00
    elif TONE == "deepgreen":
        # 深森林绿漆：比 black 亮一档、比 green 暗且低饱和，冷调墨绿底
        # 配一条明确的青白反光带；金箍 / 银白菱格 / 白果保持高对比，
        # 免得整根鞘沉成一团黑。叶片适当提亮，否则在深底上看不见。
        g, amp, rim_amp = 34 + 22 * (1 - v), 52, 34
        wr, wg, wb, bias = 0.52, 1.00, 0.72, 16
        gold, edge = (196, 168, 104), (206, 226, 208)
        metal, metal_hi, metal_dk = (104, 94, 66), (220, 204, 146), (34, 36, 28)
        cord, cord_hi, cord_dk = (14, 28, 22), (110, 138, 112), (6, 12, 9)
        wrap_white, wrap_dark = (214, 230, 214), (20, 50, 36)
        leaf, leaf_dk, leaf_hi, vein = (96, 142, 84), (30, 62, 40), (156, 196, 124), (188, 216, 156)
        berry, berry_sh, berry_hi, berry_dot = (242, 248, 236), (130, 152, 136), (255, 255, 252), (152, 140, 96)
        contrast = 0.95
    elif TONE == "green":
        g, amp, rim_amp = 66 + 34 * (1 - v), 104, 76
        wr, wg, wb, bias = 0.72, 1.00, 0.86, 34
        gold, edge = (206, 184, 118), (228, 242, 224)
        metal, metal_hi, metal_dk = (118, 104, 72), (228, 212, 156), (52, 48, 34)
        cord, cord_hi, cord_dk = (22, 38, 28), (140, 158, 132), (10, 18, 14)
        wrap_white, wrap_dark = (236, 244, 232), (32, 68, 46)
        leaf, leaf_dk, leaf_hi, vein = (72, 118, 66), (28, 60, 36), (136, 180, 108), (182, 214, 152)
        berry, berry_sh, berry_hi, berry_dot = (246, 250, 240), (150, 172, 154), (255, 255, 252), (152, 138, 92)
        contrast = 0.85
    else:                                   # white（默认定稿）
        g, amp, rim_amp = 122 + 30 * (1 - v), 76, 58
        wr, wg, wb, bias = 0.94, 1.00, 0.95, 10
        gold, edge = (178, 150, 92), (248, 252, 244)
        metal, metal_hi, metal_dk = (142, 126, 86), (238, 222, 170), (74, 66, 48)
        cord, cord_hi, cord_dk = (48, 62, 52), (166, 180, 158), (24, 36, 28)
        wrap_white, wrap_dark = (250, 252, 246), (46, 86, 58)
        leaf, leaf_dk, leaf_hi, vein = (66, 108, 62), (26, 58, 34), (128, 172, 104), (178, 212, 150)
        berry, berry_sh, berry_hi, berry_dot = (252, 252, 246), (160, 182, 164), (255, 255, 255), (146, 132, 88)
        contrast = 0.72

    # 1) 漆面：底色 + 大面积反光带 + 银白高光边
    sheen = amp * math.exp(-((v - 0.34) / 0.15) ** 2)
    rim = rim_amp * math.exp(-((v - 0.86) / 0.055) ** 2)
    grain = 4 * math.sin(u * 23 + v * 7)
    r0 = g * wr + sheen + rim + grain
    g0 = g + bias + sheen + rim + grain
    b0 = g * wb + sheen + rim * 0.92 + grain

    # 2) 细密斜擦痕 + 鮫皮颗粒：避免大片纯色，又不抢装饰
    st = (v * 17.0 + u * 1.35) % 1.0
    d_st = abs(st - 0.5)
    delta = (-9.0 if d_st < 0.022 else (4.0 if d_st < 0.055 else 0.0)) * contrast
    n = _noise(u, v) * 5 * contrast
    r, gr, b = r0 + delta + n, g0 + delta + n, b0 + delta + n

    c = rgba(r, gr, b)

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
    dv = abs(v - 0.14)
    if dv < 0.006:
        c = rgba(*metal_dk)
    elif dv < 0.016:
        c = rgba(*gold)
    elif dv < 0.024:
        c = rgba(*edge)

    # 4) 鞘口菱纹缠绳段：银白菱格 + 深绿编织底 + 交点金点（概念图「柄卷」）
    w0, w1 = SAYA_WRAP
    if w0 < u < w1:
        uu = (u - w0) / (w1 - w0)
        a = (uu * 4.0 + v * 3.0) % 1.0
        bb = (uu * 4.0 - v * 3.0) % 1.0
        dd = min(abs(a - 0.5), abs(bb - 0.5))
        if dd > 0.40:
            c = rgba(*(_lerp3(wrap_white, wrap_dark, 0.18) if dd < 0.44 else wrap_white))
        elif dd > 0.33:
            c = rgba(*wrap_dark)
        else:
            t = 0.5 + 0.5 * math.sin((uu * 28 + v * 14) * math.pi)
            c = rgba(*_lerp3(wrap_dark, leaf_dk, 0.25 + 0.25 * t))
        if (a > 0.94 or a < 0.06) and (bb > 0.94 or bb < 0.06):
            c = rgba(*gold)

    # 5) 金属箍：投影 + 中缝 + 两道亮边 + 沿周向铆点
    for hc, hw in SAYA_HOOPS:
        d = abs(u - hc)
        if d < hw * 1.6:
            if d >= hw:
                sh = 18 * contrast
                c = rgba(r - sh, gr - sh, b - sh)
            else:
                q = d / hw
                if q > 0.84 or q < 0.13:
                    c = rgba(*metal_dk)
                elif 0.22 < q < 0.34 or 0.60 < q < 0.72:
                    c = rgba(*metal_hi)
                else:
                    c = rgba(*metal)
                if 0.30 < q < 0.70 and abs((v * 18.0) % 1.0 - 0.5) < 0.15:
                    c = rgba(*gold)
            break

    # 6) 结绳：绳股亮边/暗缝/缠绕斜纹 + 两道束绳 + 正背面绳结
    k0, k1 = SAYA_KNOT
    if k0 < u < k1:
        uu = (u - k0) / (k1 - k0)
        a = (v * 2.0 + uu * 1.6) % 1.0
        bb = (v * 2.0 - uu * 1.6) % 1.0
        dc = min(abs(a - 0.5), abs(bb - 0.5))
        if dc < 0.115:
            if dc > 0.098:
                c = rgba(*cord_hi)
            elif dc < 0.020:
                c = rgba(*cord_dk)
            else:
                tw = 0.5 + 0.5 * math.sin((u * 260 + v * 95) * math.pi)
                c = rgba(*_lerp3(cord, cord_hi, 0.18 + 0.30 * tw))
        if abs(uu - 0.09) < 0.045 or abs(uu - 0.91) < 0.045:
            t = 0.5 + 0.5 * math.sin(v * 40 * math.pi)
            c = rgba(*_lerp3(cord, cord_hi, 0.15 + 0.25 * t))
        for vc in (0.25, 0.75):
            q = ((uu - 0.5) / 0.19) ** 2 + ((v - vc) / 0.095) ** 2
            if q < 1.0:
                if q > 0.55:
                    c = rgba(*_lerp3(cord, cord_hi, 0.55))
                else:
                    # 绳结中心画成交织绳股，别变成一坨纯黑空洞
                    t = 0.5 + 0.5 * math.sin((uu * 90 + v * 60) * math.pi)
                    c = rgba(*_lerp3(cord_dk, cord, 0.35 + 0.45 * t))

    # 7) 槲寄生：细枝 + 叶（叶缘亮边/中脉/侧脉）+ 白果（高光/阴影/脐点）
    for su, sv in SAYA_SPRIGS:
        if su - 0.045 < u < su + 0.010:
            tw = sv + 0.020 - (u - su) * 0.55
            if abs(v - tw) < 0.004:
                c = rgba(*leaf_dk)
        lu = (u - su) / 0.032
        lv = (v - sv) / 0.072
        shape = 0.92 * math.sqrt(max(0.0, 1.0 - lu * lu))
        if abs(lu) <= 1.0 and abs(lv) <= shape:
            e = abs(lv) / max(shape, 1e-6)
            if e > 0.86:
                c = rgba(*leaf_hi)
            elif abs(lv) < 0.055:
                c = rgba(*vein)
            else:
                side = abs(((lu * 3.2 + lv * 1.6) % 1.0) - 0.5) > 0.44
                c = rgba(*(leaf_dk if side else leaf))
        for du, dv2 in SAYA_BERRIES:
            duv, dvv = (u - su - du) / 0.010, (v - sv - dv2) / 0.026
            q = duv * duv + dvv * dvv
            if q < 1.0:
                hq = ((duv + 0.30) / 0.55) ** 2 + ((dvv + 0.32) / 0.52) ** 2
                if q > 0.80:
                    c = rgba(*berry_sh)
                elif hq < 0.55:
                    c = rgba(*berry_hi)
                else:
                    c = rgba(*berry)
                if ((duv - 0.32) / 0.30) ** 2 + ((dvv - 0.38) / 0.24) ** 2 < 1.0:
                    c = rgba(*berry_dot)
    return c


def t_sheath_plain(u, v):
    """v0.1.6 及以前的纯渐变鞘，只用于对照图里的 BEFORE 一格。"""
    if TONE == "black":
        g, amp, rim_amp = 20 + 16 * (1 - v), 30, 20
        wr, wg, wb = 0.60, 1.00, 0.70
        bias, gold, edge = 12, (168, 144, 88), (176, 190, 172)
    elif TONE == "deepgreen":
        g, amp, rim_amp = 34 + 22 * (1 - v), 52, 34
        wr, wg, wb = 0.52, 1.00, 0.72
        bias, gold, edge = 16, (196, 168, 104), (206, 226, 208)
    elif TONE == "green":
        g, amp, rim_amp = 66 + 34 * (1 - v), 104, 76
        wr, wg, wb = 0.72, 1.00, 0.86
        bias, gold, edge = 34, (206, 184, 118), (228, 242, 224)
    else:
        g, amp, rim_amp = 122 + 30 * (1 - v), 76, 58
        wr, wg, wb = 0.94, 1.00, 0.95
        bias, gold, edge = 10, (216, 198, 136), (248, 252, 244)
    sheen = amp * math.exp(-((v - 0.34) / 0.15) ** 2)
    rim = rim_amp * math.exp(-((v - 0.86) / 0.055) ** 2)
    grain = 5 * math.sin(u * 23 + v * 7)
    r = g * wr + sheen + rim + grain
    gr = g + bias + sheen + rim + grain
    b = g * wb + sheen + rim * 0.92 + grain
    if abs(v - 0.14) < 0.010:
        return rgba(*gold)
    if abs(v - 0.14) < 0.022:
        return rgba(*edge)
    return rgba(r, gr, b)


def t_habaki(u, v):
    # 初雪：冷银鎺 / 鞘尻包头，去掉暖金
    g = 186 - 54 * v + 10 * math.sin(u * 6)
    return rgba(g - 16, g + 4, g + 26)


def t_sheath_lum(u, v):
    e = math.exp(-((v - 0.5) / 0.3) ** 2)
    # 加色层在深绿漆上要压暗，否则会把整根鞘刷回浅青绿、白做深底
    if TONE == "deepgreen":
        return rgba(150, 214, 168, 165 * e)
    if TONE == "firstsnow":
        # 浅漆底上的加色层必须压得很淡，否则整根鞘会被刷回纯白
        return rgba(140, 196, 240, 96 * e)
    return rgba(216, 248, 226, 190 * e)


def t_leaf(u, v):
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
    return rgba(g * 0.72, g * 0.94, g + 16, 248)


def t_berry(u, v):
    dx, dy = u - 0.5, v - 0.5
    r = math.hypot(dx, dy)
    if r > 0.46:
        return (0, 0, 0, 0)
    a = 255 if r < 0.41 else 255 * (0.46 - r) / 0.05
    hi = 90 * math.exp(-(((u - 0.36) ** 2 + (v - 0.32) ** 2) / 0.012))
    shade = 232 - 105 * (r / 0.46) ** 2
    return rgba(shade + hi - 6, shade + hi + 2, shade + hi + 14, a)


def t_effect(u, v):
    """本体右键充能漩涡：冰蓝六向霜旋（effect 组必须保留并验收）。"""
    dx, dy = u - 0.5, v - 0.5
    r = math.hypot(dx, dy) * 2
    th = math.atan2(dy, dx)
    swirl = 0.68 + 0.32 * math.sin(th * 6 + r * 9)
    core = max(0.0, 1 - r)
    a = 205 * core ** 1.7 * swirl
    return rgba(169 + 62 * core, 216 + 34 * core, 255, a)


def t_branch(u, v):
    # 深冷灰霜枝；同时用作图标刀的描边色，让冷白刀在浅底上分得出来
    bark = 9 * math.sin(v * 41 + u * 9)
    return rgba(58 + bark, 78 + bark, 104 + bark)


def t_p_leaf(u, v):
    w = 0.36 * math.sin(math.pi * u) ** 0.8
    if abs(v - 0.5) > w:
        return (0, 0, 0, 0)
    a = 235 * (1 - abs(v - 0.5) / max(w, 1e-6) * 0.35)
    return rgba(198, 226, 240, a)


def t_p_berry(u, v):
    r = math.hypot(u - 0.5, v - 0.5) * 2
    a = 250 * max(0.0, 1 - r) ** 1.4
    return rgba(238 - 26 * r, 248 - 14 * r, 255, a)


def t_p_shard(u, v):
    dx, dy = abs(u - 0.5) * 2, abs(v - 0.5) * 2
    d = dx + dy
    if d > 0.95:
        return (0, 0, 0, 0)
    a = 255 * (1 - d / 0.95) ** 1.2
    return rgba(214 - 24 * d, 238 - 12 * d, 255, a)


def t_emblem(u, v):
    """物品栏菱形徽记背板 —— 外来 v0.4 设计稿的正确落地版本。

    珍珠银灰漆面 + 冷银双边框 + 四芒星 + 斜穿的槲寄生枝叶与三颗乳白浆果。
    四角透明；背板几何本身也做成菱形，所以即便 GUI 渲染层忽略 alpha，
    也不会露出方形黑角。这一格 (3,2) 原本是 p_shard 粒子接触表预览，
    OBJ 从未引用它，替换后 11 个在用分区的 UV 一个字都不用改。
    """
    dx, dy = (u - 0.5) * 2, (v - 0.5) * 2
    d = abs(dx) + abs(dy)
    if d > 0.995:
        return (0, 0, 0, 0)
    a = 255 if d < 0.94 else 255 * (0.995 - d) / 0.055

    # 1) 冷白偏蓝漆底：v1.1 压到中间调，让亮银刀身与深描边在 16x16 下分得开。
    #    仍保持概念图「冷白/冰蓝菱形背景」，不是深底。
    base = 148 - 30 * d
    grain = 3 * _noise(u, v)
    c = rgba(base - 16 + grain, base + grain, base + 14 + grain, a)

    # 2) 冷银 / 冰蓝双细边框（做出金属厚度，小尺寸下仍能分出层次）
    if 0.900 < d < 0.940:
        c = rgba(238, 248, 255, a)
    elif 0.850 < d < 0.872:
        c = rgba(104, 146, 190, a)

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
    return c


def t_slash_ring(u, v):
    r = math.hypot(u - 0.5, v - 1.18)
    band = abs(r - 0.86)
    if band > 0.13 or v > 0.72:
        return (0, 0, 0, 0)
    a = 255 * (1 - band / 0.13) ** 1.5
    taper = min(1.0, u * 5, (1 - u) * 5)
    return rgba(190 - 34 * (1 - taper), 226 - 18 * (1 - taper), 255, a * taper)


def t_slash_sphere(u, v):
    dx, dy = (u - 0.5) * 2, (v - 0.5) * 2
    r = math.hypot(dx, dy)
    if r > 0.98:
        return (0, 0, 0, 0)
    ring = abs(math.sin(r * math.pi * 4.5))
    a = 210 * ring * (1 - r) + 70 * max(0.0, 1 - r * 3)
    ray = max(0.0, 1 - abs(math.sin(math.atan2(dy, dx) * 6)) * 7)
    return rgba(169 + 46 * (1 - r), 216 + 28 * (1 - r), 255, a + 60 * ray * (1 - r))


def t_tassel_ribbon(u, v):
    """立体流苏专用材质（占 leaf 格）：淡蓝丝带，全格不透明。

    sheath 是不透明实体组，若采样带 alpha 的叶片/粒子纹理，游戏里会出现
    黑边或透空。因此这里整格 alpha=255，只靠明度做丝线质感。
    """
    warp = 4 * math.sin(v * 74 + u * 13) + 2.5 * math.sin(v * 31 - u * 7)
    edge = 14 * abs(u - 0.5) * 2
    return rgba(132 + warp - edge, 166 + warp - edge, 202 + warp - edge * 0.5)


def t_tassel_bead(u, v):
    """立体冰珠专用材质（占 berry 格）：全格不透明的冰白径向渐变。"""
    dx, dy = u - 0.5, v - 0.5
    d = math.hypot(dx, dy) * 1.42
    hi = 62 * math.exp(-(((u - 0.34) ** 2 + (v - 0.30) ** 2) / 0.026))
    shade = 238 - 54 * min(1.0, d) ** 1.4
    return rgba(shade - 8 + hi, shade + 2 + hi, shade + 14 + hi)


TILES = {
    "blade":            (0, 0, t_blade),
    "blade_luminous":   (1, 0, t_blade_lum),
    "handle":           (2, 0, t_handle),
    "tsuba":            (3, 0, t_tsuba),
    "sheath":           (0, 1, t_sheath),
    "sheath_luminous":  (1, 1, t_sheath_lum),
    "leaf":             (2, 1, t_leaf),
    "berry":            (3, 1, t_berry),
    "effect":           (0, 2, t_effect),
    "p_leaf":           (1, 2, t_p_leaf),
    "p_berry":          (2, 2, t_p_berry),
    "emblem":           (3, 2, t_emblem),
    "slash_ring":       (0, 3, t_slash_ring),
    "slash_sphere":     (1, 3, t_slash_sphere),
    "branch":           (2, 3, t_branch),
    "habaki":           (3, 3, t_habaki),
}

if TONE == "firstsnow":
    # leaf / berry 在 v1.1 里只是设计稿（无任何几何采样）。r3 起供立体流苏
    # 与冰珠专用：换成全格不透明材质，避免不透明 sheath 组采到带 alpha 的
    # 叶片/白果纹理。其余 4 个配色档不生成挂件，保持原纹样。
    TILES["leaf"] = (2, 1, t_tassel_ribbon)
    TILES["berry"] = (3, 1, t_tassel_bead)

atlas = Canvas(AW, AH)
for name, (col_, row_, fn) in TILES.items():
    # 逐行拼字节再一次性写回缓冲区，1024² 图集才不会慢到不可用
    for y in range(TILE):
        v = (y + 0.5) / TILE
        line = bytearray()
        for x in range(TILE):
            line.extend(fn((x + 0.5) / TILE, v))
        off = ((row_ * TILE + y) * AW + col_ * TILE) * 4
        atlas.buf[off:off + len(line)] = line
atlas.save(OUT / "demo_atlas.png")


def tuv(name, u, v):
    """分区局部 UV -> 全局 UV（v=1 对应图像顶部，与 SlashBlade 一致）。"""
    c, r, _ = TILES[name]
    gx = (c * TILE + INSET + min(1.0, max(0.0, u)) * (TILE - 2 * INSET)) / AW
    gy = (r * TILE + INSET + min(1.0, max(0.0, v)) * (TILE - 2 * INSET)) / AH
    return (gx, 1.0 - gy)


# ---------------------------------------------------------------- 参数化建模
class Mesh:
    def __init__(self):
        self.v, self.vt, self.vn = [], [], []
        self.lines = ["# terminalblade art-capability demo - procedural, original geometry"]
        self.tris = []
        self.faces = []          # (组名, f 行)，写盘时按组归并
        self.group = "blade"

    def write(self, path):
        """SlashBlade 用 Map<组名,GroupObject> 存分组，同名组只保留最后一个。
        因此必须保证每个渲染组名在 OBJ 里只出现一次 —— 这里把 f 行按组重排。
        几何按标准 Wavefront 顺序分块输出（全部 v → 全部 vt → 全部 vn → g + f），
        索引仍是 1-based 全局编号，SlashBlade 解析器与常规工具都能读。"""
        heads = [l for l in self.lines if l.startswith("#")]
        vs = [l for l in self.lines if l.startswith("v ")]
        vts = [l for l in self.lines if l.startswith("vt ")]
        vns = [l for l in self.lines if l.startswith("vn ")]
        order, buckets = [], {}
        for g, line in self.faces:
            if g not in buckets:
                buckets[g] = []
                order.append(g)
            buckets[g].append(line)
        out = heads + vs + vts + vns
        for g in order:
            out.append("g " + g)
            out.extend(buckets[g])
        Path(path).write_text("\n".join(out) + "\n", encoding="utf-8")

    def use(self, name):
        self.group = name

    def V(self, p):
        self.v.append(p)
        self.lines.append("v %.4f %.4f %.4f" % p)
        return len(self.v)

    def T(self, uv):
        self.vt.append(uv)
        self.lines.append("vt %.6f %.6f" % uv)
        return len(self.vt)

    def N(self, n):
        ln = math.sqrt(sum(c * c for c in n)) or 1.0
        n = tuple(c / ln for c in n)
        self.vn.append(n)
        self.lines.append("vn %.4f %.4f %.4f" % n)
        return len(self.vn)

    def tri(self, pts, uvs, tile, normal=None):
        a, b, c = pts
        n = normal or ((b[1] - a[1]) * (c[2] - a[2]) - (b[2] - a[2]) * (c[1] - a[1]),
                       (b[2] - a[2]) * (c[0] - a[0]) - (b[0] - a[0]) * (c[2] - a[2]),
                       (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]))
        ni = self.N(n)
        ids = [(self.V(p), self.T(tuv(tile, *uv)), ni) for p, uv in zip(pts, uvs)]
        self.faces.append((self.group, "f " + " ".join("%d/%d/%d" % i for i in ids)))
        self.tris.append((pts, [tuv(tile, *uv) for uv in uvs], tuple(n), tile, self.group))

    def quad(self, pts, uvs, tile, normal=None, both=False):
        self.tri([pts[0], pts[1], pts[2]], [uvs[0], uvs[1], uvs[2]], tile, normal)
        self.tri([pts[0], pts[2], pts[3]], [uvs[0], uvs[2], uvs[3]], tile, normal)
        if both:
            self.tri([pts[2], pts[1], pts[0]], [uvs[2], uvs[1], uvs[0]], tile)
            self.tri([pts[3], pts[2], pts[0]], [uvs[3], uvs[2], uvs[0]], tile)


m = Mesh()


# ---- 共享的中轴线：刀身 / 刀鞘 / 发光层 / 充能壳必须全部走同一条曲线 ----
# v0.2.0「换骨不换皮」：轮廓逐点取样自 terminalblade-0.1.3.jar 的
# mistletoe.obj（21 个鞘截面 + 36 个刀截面实测），装饰层完全沿用本项目。
# 它的形好在哪（对照旧版）：
#   * 腰反り 18 单位，曲率集中在后半，靠镡处几乎笔直（旧版 9t² 太浅）；
#   * 刀身窄而有脊：半宽 4.52→2.03、半厚 3.07→1.77，宽厚比 ~1.5；
#     旧版 7.4 / 1.6、宽厚比 4.6，是一张扁片；
#   * 鞘半宽 6.4 全长恒定，鞘尾收成钝圆头而不是跟着刀身收成尖刺；
#   * 切先在最后 8% 长度里线性收成一个点，不是长距离缓收。
# v0.2.1：反り方向按用户实机观感改为与 jar013 完全一致 —— 刀尖 / 鞘尾朝 −y
# 「向下弯」。之前保留 +y 是怕镜像影响姿态，实测反馈证明原版方向才是对的。
# 刀身、鞘、发光刃、金线、充能壳全部走同一条 spine_y，翻转后依旧同心；
# 镡 / 柄 / 槲寄生枝叶是 x>-16 的绝对坐标，不受影响。
BLADE_X0, BLADE_X1 = -16.0, -286.0

# 反り控制点：jar 鞘中心线按 t(鞘口 0 → 鞘尾 1) 归一化后的 |Δy| 实测值。
SORI_PROFILE = (
    (0.000, 0.00), (0.086, 0.02), (0.172, 0.29), (0.258, 0.85),
    (0.343, 1.71), (0.429, 2.86), (0.515, 4.31), (0.601, 6.06),
    (0.687, 8.11), (0.773, 10.45), (0.859, 13.09), (0.945, 16.03),
    (1.000, 18.00),
)


def t_at(x):
    """绝对 x → 刀身归一化参数 0(镡/鞘口)..1(刀尖/鞘尾)。"""
    return min(1.0, max(0.0, (x - BLADE_X0) / (BLADE_X1 - BLADE_X0)))


def _profile(t, table):
    """控制点线性插值；节点密到看不出折线，比硬拟合多项式更忠实于原模型。"""
    t = min(1.0, max(0.0, t))
    for i in range(len(table) - 1):
        t0, v0 = table[i]
        t1, v1 = table[i + 1]
        if t <= t1:
            k = 0.0 if t1 == t0 else (t - t0) / (t1 - t0)
            return v0 + (v1 - v0) * k
    return table[-1][1]


def spine_y(x):
    """反り：jar 实测腰反り曲线，刀尖 / 鞘尾朝 −y（向下弯）。"""
    return -_profile(t_at(x), SORI_PROFILE)


def kissaki(t):
    """切先：最后 10% 长度把刀身线性收成一个点。"""
    return 1.0 if t <= 0.90 else max(0.0, (1.0 - t) / 0.10)


def blade_half_width(x):
    return (4.52 - 2.49 * t_at(x)) * kissaki(t_at(x))


def blade_half_thick(x):
    return (3.07 - 1.30 * t_at(x)) * kissaki(t_at(x))


def _tail_shape(t):
    """鞘尾收头曲线：最后 12% 长度内用 smoothstep 缓收，两端切线为 0。

    v0.2.0 只在最后 5% 收，40 段里只有 2~3 个截面参与，6.4→2.1 的骤缩
    在侧面看就是一根短尖锥。改成 12% + smoothstep 后斜率减半以上，主体
    末端只轻收到 4.8 / 2.85，再由一段椭圆圆冠收成圆钝的鞘尻（黄铜包头）。
    """
    tail = min(1.0, max(0.0, (t - 0.88) / 0.12))
    return tail * tail * (3.0 - 2.0 * tail)


def sheath_half_width(x):
    """鞘半宽：全长 6.4 恒定，鞘尾只轻收到 4.80（不再是 2.1 的尖点）。

    6.4 对刀身最大半宽 4.52 始终留 >1.8 单位壁厚余量 —— 余量按「进游戏」
    留：纳刀态 blade 与 sheath 由 hardpointA/B 摆到同一位置，擦边就会露。
    """
    t = t_at(x)
    mouth = max(0.0, 1.0 - t / 0.06)
    return (6.40 - 1.60 * _tail_shape(t)) + 0.38 * mouth


def sheath_half_thick(x):
    t = t_at(x)
    mouth = max(0.0, 1.0 - t / 0.06)
    return (3.80 - 0.95 * _tail_shape(t)) + 0.23 * mouth


def blade_body(group, x0, x1, tile, segs=36):
    m.use(group)
    rings = []
    for i in range(segs + 1):
        t = i / segs
        x = x0 + (x1 - x0) * t
        # 形状参数一律用「全局 x」求值：短一截的破损刀也是同一把刀的截断，
        # 不能按自己的局部 t 重新弯一次、重新收一次尖。
        w = blade_half_width(x)
        th = blade_half_thick(x)
        cy = spine_y(x)
        rings.append([
            (x, cy - w, 0.0),            # 刃口
            (x, cy - w * 0.12, th),      # 前镐脊
            (x, cy + w, 0.0),            # 栋（刀背）
            (x, cy - w * 0.12, -th),     # 后镐脊
        ])
    for i in range(segs):
        u0, u1 = i / segs, (i + 1) / segs
        for k in range(4):
            j = (k + 1) % 4
            m.quad([rings[i][k], rings[i][j], rings[i + 1][j], rings[i + 1][k]],
                   [(u0, 0.0), (u0, 1.0), (u1, 1.0), (u1, 0.0)], tile)
    return rings


def luminous_strip(group, rings, tile):
    """发光刃：贴着刀面、整体外凸 0.28 单位。

    渲染层用加色混合画 *_luminous。若发光面与刀身共面或埋在截面内，
    游戏里会 z-fighting 闪烁甚至完全被遮住 —— 预览图看不出这个差别。
    """
    m.use(group)
    for i in range(len(rings) - 1):
        u0, u1 = i / (len(rings) - 1), (i + 1) / (len(rings) - 1)
        a, b = rings[i], rings[i + 1]
        za = a[1][2] + 0.28        # 镐脊的 z 即该处刀身最大厚度
        zb = b[1][2] + 0.28
        # 刃口 -> 镐脊，构成一条位于实体表面之外的窄发光带
        m.quad([(a[0][0], a[0][1], za), (b[0][0], b[0][1], zb),
                (b[1][0], b[1][1], zb), (a[1][0], a[1][1], za)],
               [(u0, 0.0), (u1, 0.0), (u1, 1.0), (u0, 1.0)], tile, both=True)


# 主刀身 + 发光刃 + 破损变体（刀身根部一直延伸到刀镡背面 x=-16，不再留空档）
rings = blade_body("blade", -16.0, -286.0, "blade")
luminous_strip("blade_luminous", rings, "blade_luminous")
drings = blade_body("blade_damaged", -16.0, -120.0, "blade", segs=16)
luminous_strip("blade_damaged_luminous", drings, "blade_luminous")


def collar(x0, x1, ry, rz, tile, sides=12):
    """鎺 / 切羽一类的套筒，用来把刀身、镡、柄在视觉上真正接起来。"""
    m.use("blade")
    ring = [(ry * math.cos(2 * math.pi * i / sides),
             rz * math.sin(2 * math.pi * i / sides)) for i in range(sides)]
    for i in range(sides):
        j = (i + 1) % sides
        m.quad([(x0, *ring[i]), (x0, *ring[j]), (x1, *ring[j]), (x1, *ring[i])],
               [(0, i / sides), (0, j / sides), (1, j / sides), (1, i / sides)], tile)


# 鎺必须包住新截面的根部（半宽 4.52 / 半厚 3.07），否则刀身会从鎺侧面穿出来
collar(-16.0, -36.0, 5.1, 3.6, "habaki")   # 黄铜鎺：盖住刀身与镡之间的接缝

# 初雪 v1.1：小型、简洁的六角雪花镡（规格收敛）。
# v1.0 是半径 15 的六瓣尖角盘 + 从镡外缘向外伸出的枝叶白果，系列感过强，
# 且那些细枝/小球在 3D 里就是「细小悬空尖刺」。这里做两件事：
#   1) 半径 15 -> 9.6，六尖角 -> 圆钝六角（cos6θ 只做 ±7% 的缓起伏，无尖点）；
#   2) 不再生成任何镡外悬空几何，雪花/霜化槲寄生意象全部交给 tsuba 与 emblem 贴图。
# 顶点数 / 面数 / 组名 / UV 布局 / 装配关系与朝露保持一致。
m.use("blade")
N = 96               # 六瓣深 V 谷需要更密角向采样，避免谷口折出锯齿
TSUBA_R = 9.6


def tsuba_profile(th):
    """六瓣雪花：瓣顶宽平、瓣间深 V 谷；全程连续实心、无悬空尖刺。"""
    return snow_profile(th)


front, back = [], []
for i in range(N):
    th = i / N * math.pi * 2
    r = TSUBA_R * tsuba_profile(th)
    y, z = r * math.cos(th), r * math.sin(th)
    front.append((-12.0, y, z))
    back.append((-16.0, y, z))
for i in range(N):
    j = (i + 1) % N
    u0, u1 = i / N, j / N
    m.quad([front[i], front[j], back[j], back[i]],
           [(u0, 0.0), (u1, 0.0), (u1, 1.0), (u0, 1.0)], "tsuba")
    # 正面按极坐标铺到刀镡分区，圆盘花纹才不会拉花
    def tsuba_uv(k):
        th = k / N * math.pi * 2
        rr = 0.44 * tsuba_profile(th)
        return (0.5 + rr * math.cos(th), 0.5 + rr * math.sin(th))
    m.tri([(-12.0, 0, 0), front[j], front[i]],
          [(0.5, 0.5), tsuba_uv(j), tsuba_uv(i)], "tsuba")
collar(-11.2, -12.0, 4.8, 4.8, "tsuba", sides=16)  # 切羽：随镡一起缩小
# 镡中心压一枚金属脐，避免柄穿过时露出黑洞（仍盖住 3.5/3.8 的柄截面）
hub = [(-11.0, 4.2 * math.cos(2 * math.pi * i / 16), 4.2 * math.sin(2 * math.pi * i / 16))
       for i in range(16)]
for i in range(16):
    j = (i + 1) % 16
    m.tri([(-11.0, 0, 0), hub[j], hub[i]],
          [(0.5, 0.5),
           (0.5 + 0.30 * math.cos(2 * math.pi * j / 16), 0.5 + 0.30 * math.sin(2 * math.pi * j / 16)),
           (0.5 + 0.30 * math.cos(2 * math.pi * i / 16), 0.5 + 0.30 * math.sin(2 * math.pi * i / 16))],
          "tsuba")

# 柄（八棱）+ 菱纹柄卷 + 柄头
m.use("blade")
HX0, HX1 = -16.0, 48.0
HN = 8
hr = []
for i in range(HN):
    th = i / HN * math.pi * 2
    hr.append((3.5 * math.cos(th), 3.8 * math.sin(th)))
for seg in range(10):
    t0, t1 = seg / 10, (seg + 1) / 10
    x0, x1 = HX0 + (HX1 - HX0) * t0, HX0 + (HX1 - HX0) * t1
    for i in range(HN):
        j = (i + 1) % HN
        m.quad([(x0, *hr[i]), (x0, *hr[j]), (x1, *hr[j]), (x1, *hr[i])],
               [(t0, i / HN), (t0, j / HN), (t1, j / HN), (t1, i / HN)], "handle")
m.quad([(HX1, -3.8, -3.8), (HX1, 3.8, -3.8), (HX1, 3.8, 3.8), (HX1, -3.8, 3.8)],
       [(0, 0), (1, 0), (1, 1), (0, 1)], "tsuba")

# 刀鞘：与刀身同心、同反り、同收窄，截面始终完整包住刀身截面
m.use("sheath")
SX0, SX1 = -17.0, -290.0     # 鞘口紧贴镡背，鞘尾比刀尖长 4（圆冠再往外凸 6）
SN, SSEG = 16, 40        # 16 边截面 + 40 段：反り升到 18 单位后需要更密的分段，
                          # 鞘尾 12% 的缓收也才有足够采样点


def sheath_ring(x):
    cy = spine_y(x)
    sw, sz = sheath_half_width(x), sheath_half_thick(x)
    return [(x, cy + sw * math.cos(2 * math.pi * i / SN),
             sz * math.sin(2 * math.pi * i / SN)) for i in range(SN)]


for seg in range(SSEG):
    t0, t1 = seg / SSEG, (seg + 1) / SSEG
    x0, x1 = SX0 + (SX1 - SX0) * t0, SX0 + (SX1 - SX0) * t1
    p0, p1 = sheath_ring(x0), sheath_ring(x1)
    for i in range(SN):
        j = (i + 1) % SN
        # 绕序必须让外表面法线朝外。Minecraft/SlashBlade 会剔除背面，而软件
        # 预览光栅器不剔除：原先 [p0[i],p0[j],p1[j],p1[i]] 生成的 720 个面
        # 全部朝内，进游戏后整根鞘被剔除、只剩双面生成的金线，看起来就像
        # 「刀身露在鞘外面」。UV 随顶点一起反转，保持贴图方向不变。
        # 最后一段 j 回绕到 0，UV 必须写 (i+1)/SN = 1.0 而不是 0，
        # 否则这一面会把整张贴图反向压缩贴上去，装饰一多就糊成一道脏带。
        m.quad([p0[i], p1[i], p1[j], p0[j]],
               [(t0, i / SN), (t1, i / SN),
                (t1, (i + 1) / SN), (t0, (i + 1) / SN)], "sheath")
# 鞘尻：主体末端不再平切（那样是个方头），补 5 段四分之一椭圆圆冠。
# sin 控制外凸长度、cos 控制半径，起端切线沿 -x、末端切线垂直，侧面看是
# 一个圆钝的金属包头，既不是 v0.2.0 的短尖锥，也不是平切的方块。
CAP_SEG, CAP_LEN, CAP_MIN = 5, 6.0, 0.16
cap_cy = spine_y(SX1)                 # 冠部不再继续弯，避免末端歪出去
cap_w, cap_t = sheath_half_width(SX1), sheath_half_thick(SX1)
prev = sheath_ring(SX1)
cap_x = SX1
for k in range(1, CAP_SEG + 1):
    a = (k / CAP_SEG) * (math.pi / 2)
    cap_x = SX1 - CAP_LEN * math.sin(a)
    s = CAP_MIN + (1.0 - CAP_MIN) * math.cos(a)
    ring = [(cap_x, cap_cy + cap_w * s * math.cos(2 * math.pi * i / SN),
             cap_t * s * math.sin(2 * math.pi * i / SN)) for i in range(SN)]
    for i in range(SN):
        j = (i + 1) % SN
        u0, u1 = (k - 1) / CAP_SEG, k / CAP_SEG
        # 与主管同绕序 -> 外表面法线朝外，游戏里不会被背面剔除
        m.quad([prev[i], ring[i], ring[j], prev[j]],
               [(u0, i / SN), (u1, i / SN),
                (u1, (i + 1) / SN), (u0, (i + 1) / SN)], "habaki")
    prev = ring
# 圆冠末端的小平顶封口，避免从尾部看进去透空
for i in range(SN):
    j = (i + 1) % SN
    m.tri([(cap_x, cap_cy, 0.0), prev[j], prev[i]],
          [(0.5, 0.5),
           (0.5 + 0.45 * math.cos(2 * math.pi * j / SN), 0.5 + 0.45 * math.sin(2 * math.pi * j / SN)),
           (0.5 + 0.45 * math.cos(2 * math.pi * i / SN), 0.5 + 0.45 * math.sin(2 * math.pi * i / SN))],
          "habaki")

# ---------------------------------------------------------------- 立体下垂流苏 + 冰珠
# 参考图 01/03：绑绳处垂下两条长短不一的淡蓝流苏，末端各一颗冰珠。
# 贴图只能画贴面示意，这里做成真实几何。SlashBlade 只按固定组名渲染
# （BladeRenderState.renderOverrided -> WavefrontObject.tessellateOnly），
# 新建 tassel 组在游戏里等于不存在，因此必须并进 sheath 组；Mesh.write()
# 会按组归并 f 行，同名组只出现一次，不会触发 HashMap.put 覆盖。
#
# 三条硬约束：
#   * 所有顶点在鞘椭圆之外（(dy/sw)^2+(z/sz)^2 >= 1），静止纳刀时
#     blade 完整藏在鞘里，挂件不可能插进刀身或鞘壁；
#   * 带子 both=True 生成正反双面，Minecraft 背面剔除后两侧都可见；
#   * 珠子是闭合 UV 球且法线一律朝外，不会整颗被剔除。
# UV 只采样 leaf / berry 两格（firstsnow 档已换成不透明专用材质），
# 不碰 sheath 格纹样，也不新增图集分区。
m.use("sheath")
TASSEL_U = 0.616                          # 与 t_sheath_firstsnow 的绑绳 u 对齐
TX = SX0 + (SX1 - SX0) * TASSEL_U
TCY = spine_y(TX)
TSW, TSZ = sheath_half_width(TX), sheath_half_thick(TX)
TZ0 = TSZ + 0.25                          # 挂点略出鞘壁，避免 z-fighting


def _tassel_path(x0, length, drift, dz, sway=2.2, segs=10):
    """从绑绳点向 -y 垂下的弧线，越往下越向鞘尾偏、越离鞘壁远。"""
    out = []
    for i in range(segs + 1):
        t = i / segs
        out.append((
            x0 + drift * t * t + sway * math.sin(t * math.pi * 1.25) * t,
            TCY - 0.8 - length * t,
            TZ0 + dz * t + 0.20 * math.sin(t * math.pi * 2.0),
        ))
    return out


def _ribbon(path, half, tile):
    """沿路径生成双面扁带：宽度沿 x，带面朝 ±z。"""
    n = len(path) - 1
    for i in range(n):
        a, b = path[i], path[i + 1]
        wa = half * (1.0 - 0.22 * i / n)
        wb = half * (1.0 - 0.22 * (i + 1) / n)
        v0, v1 = i / n, (i + 1) / n
        m.quad([(a[0] - wa, a[1], a[2]), (b[0] - wb, b[1], b[2]),
                (b[0] + wb, b[1], b[2]), (a[0] + wa, a[1], a[2])],
               [(0.12, v0), (0.12, v1), (0.88, v1), (0.88, v0)],
               tile, both=True)


def _bead(cx, cy, cz, r, tile, nu=16, nv=10):
    """闭合 UV 球；参数化绕序使法线全部朝外（游戏背面剔除安全）。"""
    def P(i, j):
        th, ph = 2 * math.pi * i / nu, math.pi * j / nv
        return (cx + r * math.sin(ph) * math.cos(th),
                cy + r * math.cos(ph),
                cz + r * math.sin(ph) * math.sin(th))

    for j in range(nv):
        for i in range(nu):
            p00, p10 = P(i, j), P(i + 1, j)
            p01, p11 = P(i, j + 1), P(i + 1, j + 1)
            u0, u1 = i / nu, (i + 1) / nu
            v0, v1 = j / nv, (j + 1) / nv
            if j == 0:
                m.tri([p00, p11, p01], [(u0, v0), (u1, v1), (u0, v1)], tile)
            elif j == nv - 1:
                m.tri([p00, p10, p01], [(u0, v0), (u1, v0), (u0, v1)], tile)
            else:
                m.quad([p00, p10, p11, p01],
                       [(u0, v0), (u1, v0), (u1, v1), (u0, v1)], tile)


# 起点结扣：与末端冰珠同款的小圆珠，把两条带子收束到绑绳点
_bead(TX - 0.3, TCY - 0.8, TZ0 + 1.55, 1.55, "berry", nu=8, nv=4)

for _x0, _len, _drift, _dz, _half, _br in (
        (TX + 0.9, 23.0, -2.6, 1.15, 1.35, 2.10),
        (TX - 1.5, 18.5, -6.2, 2.00, 1.15, 1.80)):
    _path = _tassel_path(_x0, _len, _drift, _dz)
    _ribbon(_path, _half, "leaf")
    _e = _path[-1]
    _bead(_e[0], _e[1] - _br * 0.85, _e[2] + 0.35, _br, "berry")

# 鞘上沿金线：沿同一条中轴线逐段生成，不再是一条笔直的硬编码四边形
m.use("sheath_luminous")
for seg in range(SSEG):
    t0, t1 = seg / SSEG, (seg + 1) / SSEG
    x0, x1 = SX0 + (SX1 - SX0) * t0, SX0 + (SX1 - SX0) * t1
    pts = []
    for x in (x0, x1):
        ang = 0.30
        yy = spine_y(x) + sheath_half_width(x) * math.cos(ang) + 0.25
        zz = sheath_half_thick(x) * math.sin(ang) + 0.25
        pts.append((x, yy, zz))
    a, b = pts
    # 金线带只朝实体外侧展开，避免下半幅埋进鞘壁造成加色闪烁
    m.quad([(a[0], a[1], a[2]), (b[0], b[1], b[2]),
            (b[0], b[1] + 0.9, b[2]), (a[0], a[1] + 0.9, a[2])],
           [(t0, 0), (t1, 0), (t1, 1), (t0, 1)], "sheath_luminous", both=True)

# 槲寄生主题组：枝条 / 叶 / 白浆果（三态可分别显隐）
def branch(group, a, b, radius=0.7):
    m.use(group)
    dx = (b[0] - a[0], b[1] - a[1], b[2] - a[2])
    side = (0.0, 0.0, radius)
    m.quad([(a[0], a[1] - radius, a[2]), (a[0], a[1] + radius, a[2]),
            (b[0], b[1] + radius, b[2]), (b[0], b[1] - radius, b[2])],
           [(0, 0), (0, 1), (1, 1), (1, 0)], "branch", both=True)


def leaf(group, base, length, width, tilt, z):
    m.use(group)
    ux, uy = math.cos(tilt), math.sin(tilt)
    vx, vy = -uy, ux
    p0 = (base[0], base[1], z)
    p1 = (base[0] + ux * length * 0.34 + vx * width, base[1] + uy * length * 0.34 + vy * width, z)
    p2 = (base[0] + ux * length, base[1] + uy * length, z)
    p3 = (base[0] + ux * length * 0.34 - vx * width, base[1] + uy * length * 0.34 - vy * width, z)
    m.quad([p0, p1, p2, p3], [(0, 0.5), (0.34, 0.0), (1, 0.5), (0.34, 1.0)], "leaf", both=True)


def berry(group, cx, cy, cz, r=2.6):
    m.use(group)
    LAT, LON = 5, 9
    sph = []
    for i in range(LAT + 1):
        ph = math.pi * i / LAT
        row = []
        for j in range(LON):
            th = 2 * math.pi * j / LON
            row.append((cx + r * math.sin(ph) * math.cos(th),
                        cy + r * math.cos(ph),
                        cz + r * math.sin(ph) * math.sin(th)))
        sph.append(row)
    for i in range(LAT):
        for j in range(LON):
            k = (j + 1) % LON
            m.quad([sph[i][j], sph[i][k], sph[i + 1][k], sph[i + 1][j]],
                   [(j / LON, i / LAT), (k / LON, i / LAT),
                    (k / LON, (i + 1) / LAT), (j / LON, (i + 1) / LAT)], "berry")


# 初雪 v1.1：不再生成任何镡外悬空的枝条 / 叶片 / 白果。
# 规格要求「小型、简洁雪花/六角意象，避免细小悬空尖刺」；v1.0 那三组从
# 镡外缘伸到 x=26、y=±28 的细枝与小球正是要收敛掉的造型差异。
# 霜化槲寄生与冰珠意象改由 sheath / emblem / p_* 贴图分区承担（概念图
# 本来就把它们画在菱形背板与鞘身表面，不是立体挂件）。
# 取而代之的是贴在镡盘面上的六角雪花浅浮雕：全部落在盘半径 9.6 之内，
# 与盘面共面微凸 0.08，双面渲染，没有任何悬空端点。
m.use("blade")
SNOW_X = -11.92            # 贴 front 面 (x=-12) 外侧，不穿进柄或刀身
for k in range(6):
    a = k * math.pi / 3
    ca, sa = math.cos(a), math.sin(a)
    # 一条短臂：从中心脐外缘 4.5 伸到 8.6，宽 1.5，末端不超出圆钝六角盘
    r0, r1, hw = 4.5, 8.6, 1.5
    p = [(SNOW_X, r0 * ca - hw * sa, r0 * sa + hw * ca),
         (SNOW_X, r1 * ca - hw * sa * 0.55, r1 * sa + hw * ca * 0.55),
         (SNOW_X, r1 * ca + hw * sa * 0.55, r1 * sa - hw * ca * 0.55),
         (SNOW_X, r0 * ca + hw * sa, r0 * sa - hw * ca)]
    uv = [(0.5 + 0.30 * math.cos(a), 0.5 + 0.30 * math.sin(a)),
          (0.5 + 0.44 * math.cos(a), 0.5 + 0.44 * math.sin(a)),
          (0.5 + 0.44 * math.cos(a + 0.18), 0.5 + 0.44 * math.sin(a + 0.18)),
          (0.5 + 0.30 * math.cos(a + 0.18), 0.5 + 0.30 * math.sin(a + 0.18))]
    m.quad(p, uv, "tsuba", both=True)
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

# effect 组：Resharped renderChargeEffect 用的充能外壳。
# 本体名刀的 effect 是包住刀鞘的 3D 筒壳（Z 比鞘大一圈），不是一张平面。
# 平面壳在 z=0 会横穿刀身与刀鞘，加色渲染时游戏里能看到一张片穿出来。
m.use("effect")
EN, ESEG = 12, 20
for seg in range(ESEG):
    t0, t1 = seg / ESEG, (seg + 1) / ESEG
    x0, x1 = SX0 + (SX1 - SX0) * t0, SX0 + (SX1 - SX0) * t1
    rings2 = []
    for x in (x0, x1):
        cy = spine_y(x)
        hy = sheath_half_width(x) * 1.16 + 0.6
        hz = sheath_half_thick(x) * 1.16 + 0.6
        rings2.append([(x,
                        cy + hy * math.cos(2 * math.pi * i / EN),
                        hz * math.sin(2 * math.pi * i / EN)) for i in range(EN)])
    p0, p1 = rings2
    for i in range(EN):
        j = (i + 1) % EN
        m.quad([p0[i], p0[j], p1[j], p1[i]],
               [(t0, i / EN), (t0, j / EN), (t1, j / EN), (t1, i / EN)],
               "effect", both=True)

# ---------------------------------------------------------------- 物品栏图标 6 组
# SlashBladeTEISR.renderIcon 字节码实测（Resharped 2.1.9）：
#   GUI -> PoseStack.scale(0.008)，按 SwordType 选 item_blade / item_bladens /
#   item_damaged，再画同名 "_luminous" 加色层；贴图仍是同一张 512 图集。
#   之后还会画 durability.obj 的 base/color —— 一个跨度 ±70.7 的青蓝菱形
#   底板（截图里那个大菱形，原版每把刀都有），落在 z≈0.1 / -1.9。
#   原版 blade.obj 的 item_* 是 x[-86.7,70.7] y[-70.7,80.2] z[-4,29.8] 的
#   3D 斜置整刀，不是 PNG、也不是两根竖条：图标必须由 OBJ 几何画出来，
#   补一张 icon.png 是无效功（游戏根本不读物品模型 layer0 之外的图标）。
# 做法：整刀放在 z=2..5.4（菱形底板之前），全部用绕序朝 +Z 的单面片分层
# 叠出前后关系 —— 既不依赖背面剔除方向，也不会共面 z-fighting。
ICON_L = 180.0
ICON_C = ICON_L / 2
# 225° = 45° 再转半圈：柄落在右上、刀尖指向左下，
# 也就是「从右上往左下插」，而不是原来那种刀尖朝右上挑的姿势。
ICON_A = math.radians(225.0)
ICON_COS, ICON_SIN = math.cos(ICON_A), math.sin(ICON_A)
ICON_SCALE = 1.12          # 让端点 ±71，贴近原版 item_* 的 ±80 屏幕占比

# 原版阎魔刀（yamato.obj）的 item_* 组是「鞘 + 刃」X 交叉：483 面 /
# bbox 158x151 近正方形、25% 顶点离主轴，不是一条细长刀。默认照这个构图：
#   主鞘 225° = 右上 -> 左下；副刃 315° = 左上 -> 右下。
# 只想保留单刀时用 --icon=single（或环境变量 ICON_STYLE=single）。
ICON_STYLES = ("cross", "single")


def _read_icon_style():
    for a in sys.argv[1:]:
        if a.startswith("--icon="):
            return a.split("=", 1)[1]
    return os.environ.get("ICON_STYLE", "cross")


ICON_STYLE = _read_icon_style()
if ICON_STYLE not in ICON_STYLES:
    raise SystemExit("未知 --icon: %s（可用 %s）" % (ICON_STYLE, "/".join(ICON_STYLES)))
ICON_A2 = math.radians(315.0)
ICON_C2 = math.cos(ICON_A2), math.sin(ICON_A2)
CROSS_Z, CROSS_Z2, CROSS_LUM_Z = 3.6, 3.8, 5.6


def ipt(s, w, z):
    """s=0 柄尾 / s=180 刀尖，w 垂直刀轴，z 朝 GUI 相机；整体朝 225° 斜置。"""
    return (ICON_SCALE * ((s - ICON_C) * ICON_COS - w * ICON_SIN),
            ICON_SCALE * ((s - ICON_C) * ICON_SIN + w * ICON_COS),
            z)


def iptx(s, w, z):
    """副刃局部坐标：315° = 柄在左上、刃尖在右下，与主鞘成 X 交叉。"""
    ca, sa = ICON_C2
    return (ICON_SCALE * ((s - ICON_C) * ca - w * sa),
            ICON_SCALE * ((s - ICON_C) * sa + w * ca),
            z)


def icon_plate(group, s0, s1, w0, w1, z, tile, u0=0.0, u1=1.0, segs=4,
               outline=0.0, wshift=0.0):
    """朝 +Z 的渐变宽度长条。outline>0 时先铺一圈更宽更深的冷灰描边。

    描边在 z-0.12、与主体不共面；16x16 缩略下靠这圈暗边把冷白刀
    从冷白菱形背板里分出来（概念图 image4 的做法）。
    v1.1：主体半宽 6.4 -> 10、描边 2.4 -> 3.2，16x16 下每条都有 2~3 像素；
    wshift 用于破损态把断下来的刀尖横向挪开，形成可辨的错位断口。
    """
    m.use(group)
    if outline > 0:
        for i in range(segs):
            a, b = i / segs, (i + 1) / segs
            sa, sb = s0 + (s1 - s0) * a, s0 + (s1 - s0) * b
            wa = w0 + (w1 - w0) * a + outline
            wb = w0 + (w1 - w0) * b + outline
            m.quad([ipt(sa, -wa + wshift, z - 0.12), ipt(sb, -wb + wshift, z - 0.12),
                    ipt(sb, wb + wshift, z - 0.12), ipt(sa, wa + wshift, z - 0.12)],
                   [(a, 0), (b, 0), (b, 1), (a, 1)], "branch")
    for i in range(segs):
        a, b = i / segs, (i + 1) / segs
        sa, sb = s0 + (s1 - s0) * a, s0 + (s1 - s0) * b
        wa, wb = w0 + (w1 - w0) * a, w0 + (w1 - w0) * b
        ua, ub = u0 + (u1 - u0) * a, u0 + (u1 - u0) * b
        m.quad([ipt(sa, -wa + wshift, z), ipt(sb, -wb + wshift, z),
                ipt(sb, wb + wshift, z), ipt(sa, wa + wshift, z)],
               [(ua, 0), (ub, 0), (ub, 1), (ua, 1)], tile)


def icon_disc(group, s, w, r, z, tile, n=12):
    """正对 GUI 的多边形盘，UV 取 tile 内切圆（圆盘图案不会被拉伸）。"""
    m.use(group)
    ring = [(s + r * math.cos(2 * math.pi * i / n),
             w + r * math.sin(2 * math.pi * i / n)) for i in range(n)]
    uv = [(0.5 + 0.49 * math.cos(2 * math.pi * i / n),
           0.5 + 0.49 * math.sin(2 * math.pi * i / n)) for i in range(n)]
    for i in range(1, n - 1):
        m.tri([ipt(ring[0][0], ring[0][1], z),
               ipt(ring[i][0], ring[i][1], z),
               ipt(ring[i + 1][0], ring[i + 1][1], z)],
              [uv[0], uv[i], uv[i + 1]], tile)


def icon_twig(group, s0, w0, s1, w1, half, z, tile="branch"):
    dx, dy = s1 - s0, w1 - w0
    length = math.hypot(dx, dy) or 1.0
    px, py = -dy / length * half, dx / length * half
    m.use(group)
    # 绕序必须让法线朝 +Z：枝的 (px,py) 是 (dx,dy) 的逆时针法向，
    # 从 -px 侧起笔才是 +Z；反过来写整根枝会在 GUI 里被背面剔除掉。
    m.quad([ipt(s0 - px, w0 - py, z), ipt(s1 - px, w1 - py, z),
            ipt(s1 + px, w1 + py, z), ipt(s0 + px, w0 + py, z)],
           [(0, 0), (1, 0), (1, 1), (0, 1)], tile)


def icon_leaf(group, s, w, lng, wide, ang, z):
    dx, dy = math.cos(ang), math.sin(ang)
    px, py = -dy * wide, dx * wide
    m.use(group)
    m.quad([ipt(s - px, w - py, z),
            ipt(s + lng * dx - px, w + lng * dy - py, z),
            ipt(s + lng * dx + px, w + lng * dy + py, z),
            ipt(s + px, w + py, z)],
           [(0, 0), (1, 0), (1, 1), (0, 1)], "leaf")


CROSS_TRI_RANGES = []       # 副刃三角形在 m.tris 中的区间，供 single 对照预览过滤


def icon_cross(group, gl, broken):
    """原版阎魔刀式 X 构图：主条 225°（右上→左下），副条 315°（左上→右下）。

    副条全部朝 +Z 单面，z=3.6..3.8 与主条 z=2.0..3.2 分层，不共面闪烁；
    加色刃线抬到 z=5.6，保证 luminous 层外凸、不会被实体遮住。
    """
    start = len(m.tris)
    tip = 116.0 if broken else 168.0

    def plate(s0, s1, w0, w1, tile, segs=6, zz=CROSS_Z, outline=0.0):
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
                   [(a, 0), (b, 0), (b, 1), (a, 1)], tile)

    def disc(s, w, r, tile, n=10, zz=CROSS_Z2):
        m.use(group)
        ring = [(s + r * math.cos(2 * math.pi * i / n),
                 w + r * math.sin(2 * math.pi * i / n)) for i in range(n)]
        uv = [(0.5 + 0.49 * math.cos(2 * math.pi * i / n),
               0.5 + 0.49 * math.sin(2 * math.pi * i / n)) for i in range(n)]
        for i in range(1, n - 1):
            m.tri([iptx(ring[0][0], ring[0][1], zz),
                   iptx(ring[i][0], ring[i][1], zz),
                   iptx(ring[i + 1][0], ring[i + 1][1], zz)],
                  [uv[0], uv[i], uv[i + 1]], tile)

    plate(6, 44, 7.6, 7.6, "handle", segs=4)             # 菱纹柄卷（加粗）
    disc(46, 0, 9.0, "tsuba")                             # 小刀镡（加粗）
    plate(50, tip, 8.4, 4.6 if broken else 2.6, "blade", segs=8, outline=3.0)
    if broken:
        plate(tip, tip + 6, 4.6, 3.2, "habaki", segs=1)  # 断口
    m.use(gl)
    w_start, w_end = 8.4, 4.6 if broken else 2.6
    for i in range(8):
        a, b = i / 8, (i + 1) / 8
        sa, sb = 50 + (tip - 50) * a, 50 + (tip - 50) * b
        wa = w_start + (w_end - w_start) * a
        wb = w_start + (w_end - w_start) * b
        m.quad([iptx(sa, -wa, CROSS_LUM_Z), iptx(sb, -wb, CROSS_LUM_Z),
                iptx(sb, -wb + 1.2, CROSS_LUM_Z), iptx(sa, -wa + 1.2, CROSS_LUM_Z)],
               [(a, 0), (b, 0), (b, 1), (a, 1)], "blade_luminous")
    CROSS_TRI_RANGES.append((start, len(m.tris)))


def icon_backplate(group, tile="emblem"):
    """物品栏菱形徽记背板 —— 落在真正生效的位置。

    SlashBladeTEISR 在 GUI 里渲染的是 OBJ 的 item_* 组，不是贴图里某块菱形。
    z=1.2 夹在原版 durability 菱形底板（z≈0.1）与图标主刀（z≥2.0）之间：
    既不会挡住刀，也不会被底板盖掉。
    几何本身就是菱形，UV 只采样分区的内接菱形，四角透明区不会被采到。

    v1.1：BROKEN 换用深冷灰 `branch` 分区做底，SHEATHED 用冷白 `emblem`。
    两态在 16x16 下的第一眼差异由这层明度差承担（不靠细枝小果）。
    """
    m.use(group)
    R, z = 48.0, 1.2
    # 逆时针绕序：从 +Z 看朝外，避免被背面剔除
    m.quad([(-R, 0.0, z), (0.0, -R, z), (R, 0.0, z), (0.0, R, z)],
           [(0.0, 0.5), (0.5, 0.0), (1.0, 0.5), (0.5, 1.0)], tile)


def icon_sword(state, look=None):
    """state 是渲染层会请求的组名；look 决定画成什么外观。

    只要 SHEATHED / BROKEN 两种图标：item_bladens 组必须留着（渲染层在
    NOSCABBARD 状态会请求它，删掉就是空图标），但让它复用纳刀外观。

    v1.1 按 ChatGPT 评审收敛 16x16 辨识度，做了三处减法 / 一处加法：
      减：两根斜枝、三片叶、三颗白果、柄头大六角盘 —— 16x16 下全是噪点；
      减：BROKEN 不再画 X 交叉副刃，只留一条断刀；
      加：刀 / 鞘半宽 6.4 -> 10、描边 2.4 -> 3.2，每条在 16x16 下有 2~3 像素；
      加：SHEATHED 冷白背板 / BROKEN 深冷灰背板，两态第一眼就是明暗差，
          再加中段 16 单位断口 + 9 单位横向错位，缩到 16x16 仍能看出"断了"。
    霜化槲寄生与冰珠意象保留在 emblem 贴图里（背板纹理），不再用立体几何。
    """
    look = look or state
    g, gl = "item_" + state, "item_" + state + "_luminous"
    broken, sheathed = look == "damaged", look == "blade"
    tip = ICON_L

    # 背板：两态明度硬差异（SHEATHED 冷白 / BROKEN 深冷灰）
    icon_backplate(g, "branch" if broken else "emblem")

    # 柄卷 + 六角雪花镡 + 鎺（去掉柄头那个 14 半径的大盘）
    icon_plate(g, 0, 44, 9.0, 9.4, 2.0, "handle", segs=4, outline=3.0)
    icon_disc(g, 2, 0, 9.6, 2.4, "tsuba", n=6)
    icon_plate(g, 44, 54, 9.0, 8.8, 3.2, "habaki", segs=1)

    if sheathed:
        # 纳刀态：一条粗鞘贯穿全长，末端冷银鞘尻
        icon_plate(g, 54, 172, 10.0, 5.4, 2.0, "sheath", segs=8, outline=3.2)
        icon_plate(g, 172, tip, 5.4, 2.2, 2.4, "habaki", segs=1, outline=2.0)
    else:
        # 破损态：前后两截，中间留 16 单位空洞，后截横向错位 9 单位
        icon_plate(g, 54, 104, 9.6, 7.2, 2.0, "blade", segs=5, outline=3.2)
        icon_plate(g, 104, 108, 7.2, 7.0, 3.0, "habaki", segs=1)   # 断面亮口
        icon_plate(g, 120, 176, 6.8, 2.4, 2.0, "blade", segs=5,
                   outline=3.2, wshift=9.0)
        icon_plate(g, 118, 122, 6.8, 6.6, 3.0, "habaki", segs=1,
                   wshift=9.0)                                        # 另一半断面

    # 加色发光层：单面 +Z、整体外凸到 z=5.4；破损态只在前半截走刃线
    m.use(gl)
    lum = "sheath_luminous" if sheathed else "blade_luminous"
    w_start, w_end = (10.0, 5.4) if sheathed else (9.6, 7.2)
    lum_end = tip if sheathed else 104.0
    for i in range(8):
        a, b = i / 8, (i + 1) / 8
        sa, sb = 54 + (lum_end - 54) * a, 54 + (lum_end - 54) * b
        wa = w_start + (w_end - w_start) * a
        wb = w_start + (w_end - w_start) * b
        m.quad([ipt(sa, -wa, 5.4), ipt(sb, -wb, 5.4),
                ipt(sb, -wb + 2.4, 5.4), ipt(sa, -wa + 2.4, 5.4)],
               [(a, 0), (b, 0), (b, 1), (a, 1)], lum)

    # 原版阎魔刀式 X 交叉只属于 SHEATHED；BROKEN 是单条断刀，轮廓本身就不同。
    # --icon=single 可把 SHEATHED 也退回单刀构图做对照。
    if ICON_STYLE == "cross" and not broken:
        icon_cross(g, gl, broken)


for _st, _look in (("blade", "blade"), ("bladens", "blade"),
                   ("damaged", "damaged")):
    icon_sword(_st, _look)

m.write(OUT / "demo_model.obj")


# ---------------------------------------------------------------- 软件渲染预览
def rot(p, ay, ax):
    x, y, z = p
    x, z = x * math.cos(ay) + z * math.sin(ay), -x * math.sin(ay) + z * math.cos(ay)
    x, y = x * math.cos(ax) - y * math.sin(ax), x * math.sin(ax) + y * math.cos(ax)
    return (x, y, z)


RW, RH = 1000, 420
AY, AX = math.radians(20), math.radians(-13)
SCALE, CX, CY = 3.05, 500, 210
# v1.1：悬空枝/叶/果几何已整体移除，雪花意象并入 blade 组的镡盘浮雕与贴图。
THEME = set()
LIGHT = (0.35, 0.55, 0.76)
ll = math.sqrt(sum(c * c for c in LIGHT))
LIGHT = tuple(c / ll for c in LIGHT)


def render(groups, filename, blade_tiles=None, tiles=None):
    cv = Canvas(RW, RH)
    for y in range(RH):
        k = y / RH
        for x in range(RW):
            cv.set(x, y, rgba(238 - 22 * k, 242 - 20 * k, 247 - 16 * k, 255))
    zbuf = [-1e9] * (RW * RH)
    jobs = []
    for pts, uvs, normal, tile, group in m.tris:
        if group not in groups:
            continue
        if blade_tiles is not None and group == "blade" and tile not in blade_tiles:
            continue
        if tiles is not None and tile not in tiles:
            continue
        rp = [rot(p, AY, AX) for p in pts]
        depth = sum(p[2] for p in rp) / 3.0
        sp = [(CX + (p[0] + 119) * SCALE, CY - p[1] * SCALE) for p in rp]
        rn = rot(normal, AY, AX)
        nl = math.sqrt(sum(c * c for c in rn)) or 1.0
        lam = abs(sum(rn[k] * LIGHT[k] for k in range(3)) / nl)
        shade = 0.55 + 0.45 * lam
        if "luminous" in tile:
            shade = 1.0
        jobs.append((depth, sp, uvs, [p[2] for p in rp], shade))
    jobs.sort(key=lambda j: j[0])
    for _, sp, uvs, dz, shade in jobs:
        fill_tri_tex(cv, sp, uvs, dz, shade, atlas, zbuf)
    cv.save(OUT / filename)


# 拔刀态：刀身 + 发光刃 + 槲寄生枝叶；纳刀态：刀鞘 + 柄 + 镡
# 拔刀态：刀身 + 镡 + 柄 + 槲寄生（发光层只在游戏内独立渲染，预览不叠加）
render({"blade"} | THEME, "demo_render.png")
render({"blade", "sheath", "sheath_luminous"} | THEME,
       "demo_render_sheathed.png", blade_tiles={"handle", "tsuba", "habaki"})
# 全组叠加态：用来核对刀身是否完全藏在鞘里、不再出现楔形穿模
render({"blade", "sheath", "sheath_luminous"} | THEME, "demo_overlay.png")

# 镡特写：默认视角几乎沿盘面看过去，圆盘会被压成窄带，六瓣轮廓看不出来。
# 这里把视线转到正对镡面，只画 tile=tsuba 的三角形（柄卷/鎺/刀身都不进画面）。
_saved = (AY, AX, SCALE, CX, CY, RW, RH)
AY, AX = math.radians(88), math.radians(0)
SCALE, CX, CY = 15.0, -1585.0, 200.0
RW, RH = 400, 400
render({"blade"}, "demo_tsuba_closeup.png", blade_tiles={"tsuba"})
AY, AX, SCALE, CX, CY, RW, RH = _saved


def focus_render(groups, tiles, filename, scale, w=520, h=440, focus_tiles=None):
    """按指定 tile 集合的投影 bbox 自动定心，避免手工猜 CX/CY 把主体裁掉。"""
    global AY, AX, SCALE, CX, CY, RW, RH
    saved = (AY, AX, SCALE, CX, CY, RW, RH)
    AY, AX = math.radians(20), math.radians(-13)
    SCALE, RW, RH = scale, w, h
    pts = []
    for p3, _uv, _n, tile, group in m.tris:
        if group not in groups or tile not in (focus_tiles or tiles):
            continue
        for p in p3:
            rp = rot(p, AY, AX)
            pts.append(((rp[0] + 119) * SCALE, -rp[1] * SCALE))
    xs, ys = [q[0] for q in pts], [q[1] for q in pts]
    CX = w / 2 - (min(xs) + max(xs)) / 2
    CY = h / 2 - (min(ys) + max(ys)) / 2
    render(groups, filename, tiles=tiles)
    AY, AX, SCALE, CX, CY, RW, RH = saved


# 立体流苏特写：默认整刀视角下挂件只有几十像素，看不出双面带与冰珠
focus_render({"sheath"}, {"leaf", "berry"},
             "demo_tassel_closeup.png", 12.0)
# 带鞘身做参照，确认挂点贴在绑绳处而不是浮在空中
focus_render({"sheath"}, {"leaf", "berry", "sheath"},
             "demo_tassel_context.png", 5.5, focus_tiles={"leaf", "berry"})


# ---------------------------------------------------------------- 物品栏图标预览
# 对照图：先画原版 durability 的青蓝菱形底板，再叠三种 item_* 状态。
# 这张 PNG 只给人看，不是游戏资产 —— 游戏里的图标完全由 OBJ 几何生成。
FONT = {
    "S": ["01111", "10000", "10000", "01110", "00001", "00001", "11110"],
    "H": ["10001", "10001", "10001", "11111", "10001", "10001", "10001"],
    "E": ["11111", "10000", "10000", "11110", "10000", "10000", "11111"],
    "A": ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    "T": ["11111", "00100", "00100", "00100", "00100", "00100", "00100"],
    "D": ["11110", "10001", "10001", "10001", "10001", "10001", "11110"],
    "N": ["10001", "11001", "10101", "10011", "10001", "10001", "10001"],
    "K": ["10001", "10010", "10100", "11000", "10100", "10010", "10001"],
    "B": ["11110", "10001", "10001", "11110", "10001", "10001", "11110"],
    "R": ["11110", "10001", "10001", "11110", "10100", "10010", "10001"],
    "O": ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
    "W": ["10001", "10001", "10001", "10101", "10101", "11011", "10001"],
    "I": ["11111", "00100", "00100", "00100", "00100", "00100", "11111"],
    "G": ["01111", "10000", "10000", "10111", "10001", "10001", "01110"],
    "L": ["10000", "10000", "10000", "10000", "10000", "10000", "11111"],
    "C": ["01111", "10000", "10000", "10000", "10000", "10000", "01111"],
    "F": ["11111", "10000", "10000", "11110", "10000", "10000", "10000"],
    "V": ["10001", "10001", "10001", "10001", "10001", "01010", "00100"],
}


def draw_text(cv, x, y, text, color, sc=2):
    for k, ch in enumerate(text.upper()):
        glyph = FONT.get(ch)
        if not glyph:
            continue
        for r, row in enumerate(glyph):
            bits = int(row, 2)
            for c in range(5):
                if bits & (1 << (4 - c)):
                    for dy in range(sc):
                        for dx in range(sc):
                            cv.set(x + (k * 6 + c) * sc + dx,
                                   y + r * sc + dy, color)


def diamond(cv, cx, cy, r, color):
    fill_tri(cv, (cx, cy - r), (cx + r, cy), (cx, cy + r), color)
    fill_tri(cv, (cx, cy - r), (cx, cy + r), (cx - r, cy), color)


def icon_rot(p):
    """近似 GUI 的 display rotation [15,-25,-5]，只用于预览。"""
    x, y, z = rot(p, math.radians(-12), math.radians(8))
    b = math.radians(-3)
    return (x * math.cos(b) - y * math.sin(b),
            x * math.sin(b) + y * math.cos(b), z)


def icon_preview(filename="demo_icon.png", single=False):
    """single=True 时按 z 层过滤掉 X 交叉的副刃，画出旧构图做对照。"""
    IW, PAD, LBL = 168, 18, 26
    sheet = Canvas(2 * IW + 3 * PAD, IW + 2 * PAD + LBL)
    bg = rgba(178, 188, 202)
    for y in range(sheet.h):
        for x in range(sheet.w):
            sheet.set(x, y, bg)
    # 每格独立 z-buffer，否则前一格的深度会串到后一格
    # 只展示用户要的两种图标外观；NOSCABBARD 在游戏里复用 SHEATHED。
    states = [
        ({"item_blade", "item_blade_luminous"}, "SHEATHED"),
        ({"item_damaged", "item_damaged_luminous"}, "BROKEN"),
    ]
    SC = (IW - 24) / 170.0
    for i, (groups, label) in enumerate(states):
        zbuf = [-1e9] * (sheet.w * sheet.h)
        ox, oy = PAD + i * (IW + PAD), PAD
        cx, cy = ox + IW / 2.0, oy + IW / 2.0
        # 本体耐久菱形层（仅 GUI 绘制，见 SlashBladeTEISR:104）：
        # 颜色为 lerp(深灰 -> 紫红) 乘 durability.png，不受初雪主图集控制。
        # 这里按满耐久态用冷灰紫示意；清单第 84 行把它列为独立的程序接入任务。
        diamond(sheet, cx, cy, 79, rgba(52, 44, 96))
        diamond(sheet, cx, cy, 75, rgba(72, 154, 208))
        diamond(sheet, cx, cy, 67, rgba(66, 70, 92))
        for k in range(10):                     # 底板上一点点深浅斑驳
            bx = cx - 44 + (k * 37) % 88
            by = cy - 40 + (k * 53) % 80
            sheet.blend(bx, by, rgba(48, 52, 76, 150))
        jobs = []
        for idx, (pts, uvs, normal, tile, group) in enumerate(m.tris):
            if group not in groups:
                continue
            # 副刃在建模时记录了 m.tris 的索引区间，按区间过滤最可靠
            # （副刃 z 与主条的枝叶/鎺有重叠，不能只按 z 判断）。
            if single and any(a <= idx < b for a, b in CROSS_TRI_RANGES):
                continue
            rp = [icon_rot(p) for p in pts]
            depth = sum(p[2] for p in rp) / 3.0
            sp = [(cx + p[0] * SC, cy - p[1] * SC) for p in rp]
            shade = 1.0 if "luminous" in tile else 0.94
            jobs.append((depth, sp, uvs, [p[2] for p in rp], shade))
        jobs.sort(key=lambda j: j[0])
        for _, sp, uvs, dz, shade in jobs:
            fill_tri_tex(sheet, sp, uvs, dz, shade, atlas, zbuf)
        draw_text(sheet, ox + 6, oy + IW + 4, label, rgba(38, 52, 42), 2)
    sheet.save(OUT / filename)


icon_preview("demo_icon.png")
icon_preview("demo_icon_single.png", single=True)


# ---------------------------------------------------------------- 粒子 / 特效接触表
PART = [
    ("p_leaf", "leaf"), ("p_berry", "berry"), ("p_shard", "shard"),
    ("slash_ring", "ring"), ("slash_sphere", "sphere"),
]
CELL = TILE + 4
sheet = Canvas(len(PART) * CELL, TILE + 30)
for y in range(sheet.h):
    for x in range(sheet.w):
        sheet.set(x, y, rgba(238, 242, 247, 255))
for i, (tile, _) in enumerate(PART):
    # (3,2) 已让给物品栏菱形徽记；粒子接触表里的 shard 直接由函数生成，
    # 不再从 TILES 取值，保证预览与图集互不影响。
    fn = t_p_shard if tile == "p_shard" else TILES[tile][2]
    for y in range(TILE):
        v = (y + 0.5) / TILE
        for x in range(TILE):
            sheet.blend(i * CELL + 2 + x, 2 + y, fn((x + 0.5) / TILE, v))
sheet.save(OUT / "demo_particles.png")


# ---------------------------------------------------------------- 菱形徽记放大预览
def emblem_preview():
    """把 emblem 分区放大 3 倍看纹样（只给人看，不是游戏资产）。"""
    S, PAD = 3, 14
    sheet = Canvas(TILE * S + 2 * PAD, TILE * S + 2 * PAD + 24,
                   rgba(178, 188, 202, 255))
    for y in range(TILE):
        v = (y + 0.5) / TILE
        line = [t_emblem((x + 0.5) / TILE, v) for x in range(TILE)]
        for dy in range(S):
            for x, c in enumerate(line):
                for dx in range(S):
                    sheet.blend(PAD + x * S + dx, PAD + y * S + dy, c)
    draw_text(sheet, PAD + 2, PAD + TILE * S + 5, "BADGE", rgba(34, 48, 40), 2)
    sheet.save(OUT / "demo_emblem.png")


emblem_preview()


# ---------------------------------------------------------------- 刀鞘装饰对照图
def saya_compare():
    """把刀鞘分区放大成对照图：BEFORE（旧纯渐变）+ 四档配色。

    只给人看，不是游戏资产；真正的贴图就是 demo_atlas.png 里那一格。
    """
    global TONE
    keep = TONE
    S, PAD, LBL = 2, 14, 24
    labels = ("BEFORE", "FIRST SNOW", "GREEN", "DEEP GREEN", "BLACK")
    tones = ("white", "firstsnow", "green", "deepgreen", "black")
    W = len(labels) * TILE * S + (len(labels) + 1) * PAD
    H = TILE * S + 2 * PAD + LBL
    sheet = Canvas(W, H, rgba(232, 236, 230, 255))
    for i, label in enumerate(labels):
        TONE = tones[i]
        fn = t_sheath_plain if i == 0 else t_sheath
        ox = PAD + i * (TILE * S + PAD)
        for y in range(TILE):
            v = (y + 0.5) / TILE
            line = bytearray()
            for x in range(TILE):
                c = fn((x + 0.5) / TILE, v)
                for _ in range(S):
                    line.extend(c)
            for dy in range(S):
                off = ((PAD + y * S + dy) * W + ox) * 4
                sheet.buf[off:off + len(line)] = line
        draw_text(sheet, ox + 2, PAD + TILE * S + 5, label, rgba(38, 52, 42), 2)
    TONE = keep
    sheet.save(OUT / "demo_saya_compare.png")


saya_compare()

print("faces(triangles):", len(m.tris))
print("obj lines:", len(m.lines))
print("groups:", sorted({g for *_, g in m.tris}))
for f in ["demo_atlas.png", "demo_model.obj", "demo_render.png", "demo_particles.png"]:
    print(f, (OUT / f).stat().st_size, "bytes")
