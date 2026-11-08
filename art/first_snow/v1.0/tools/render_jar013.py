# -*- coding: utf-8 -*-
"""按 SlashBlade WavefrontObject 的真实规则，软件渲染 terminalblade-0.1.3.jar 内的模型。

反编译依据（SlashBladeResharped 2.1.9-1.21.1）：
  WavefrontObject.parseTextureCoordinate : TextureCoordinate(u_obj, 1 - v_obj)
  Face.uvDefaultOperator = Vector4f(1, 1, 0, 0)，uvOperator 指向它且无外部修改
  -> UV 原样送入 VertexConsumer；u > 1 时由 GL_REPEAT 横向平铺。
  原版 blade.obj / yamato.obj 同样使用 u = 1.008 .. 4.508（沿刀身平铺刃纹），
  所以 0.1.3.jar 里 u 到 4.006 不是格式错误。

只依赖 Python 标准库。输出：
  jar013_render_3d.png     纳刀 / 拔刀 / 破损 / 刃片掉落 四种 3D 状态
  jar013_render_icons.png  物品栏三态图标（含原版 durability 菱形底板示意）
"""

import math
import struct
import zipfile
import zlib
from pathlib import Path

JAR = Path(r"E:\dsh\terminalblade-0.1.3.jar")
OBJ_ENTRY = "assets/terminalblade/model/named/mistletoe.obj"
PNG_ENTRY = "assets/terminalblade/model/named/mistletoe.png"
OUT = Path(__file__).resolve().parent


# ---------------------------------------------------------------- 5x7 点阵字体
_F = {
    "A": "01110 10001 10001 11111 10001 10001 10001",
    "B": "11110 10001 10001 11110 10001 10001 11110",
    "C": "01111 10000 10000 10000 10000 10000 01111",
    "D": "11110 10001 10001 10001 10001 10001 11110",
    "E": "11111 10000 10000 11110 10000 10000 11111",
    "F": "11111 10000 10000 11110 10000 10000 10000",
    "G": "01111 10000 10000 10111 10001 10001 01111",
    "H": "10001 10001 10001 11111 10001 10001 10001",
    "I": "11111 00100 00100 00100 00100 00100 11111",
    "J": "00111 00010 00010 00010 00010 10010 01100",
    "K": "10001 10010 10100 11000 10100 10010 10001",
    "L": "10000 10000 10000 10000 10000 10000 11111",
    "M": "10001 11011 10101 10101 10001 10001 10001",
    "N": "10001 11001 10101 10011 10001 10001 10001",
    "O": "01110 10001 10001 10001 10001 10001 01110",
    "P": "11110 10001 10001 11110 10000 10000 10000",
    "Q": "01110 10001 10001 10001 10101 10010 01101",
    "R": "11110 10001 10001 11110 10100 10010 10001",
    "S": "01111 10000 10000 01110 00001 00001 11110",
    "T": "11111 00100 00100 00100 00100 00100 00100",
    "U": "10001 10001 10001 10001 10001 10001 01110",
    "V": "10001 10001 10001 10001 10001 01010 00100",
    "W": "10001 10001 10001 10101 10101 11011 10001",
    "X": "10001 10001 01010 00100 01010 10001 10001",
    "Y": "10001 10001 01010 00100 00100 00100 00100",
    "Z": "11111 00001 00010 00100 01000 10000 11111",
    "0": "01110 10001 10011 10101 11001 10001 01110",
    "1": "00100 01100 00100 00100 00100 00100 01110",
    "2": "01110 10001 00001 00010 00100 01000 11111",
    "3": "11110 00001 00001 01110 00001 00001 11110",
    "4": "00010 00110 01010 10010 11111 00010 00010",
    "5": "11111 10000 10000 11110 00001 00001 11110",
    "6": "01110 10000 10000 11110 10001 10001 01110",
    "7": "11111 00001 00010 00100 01000 01000 01000",
    "8": "01110 10001 10001 01110 10001 10001 01110",
    "9": "01110 10001 10001 01111 00001 00001 01110",
    "/": "00001 00001 00010 00100 01000 10000 10000",
    "-": "00000 00000 00000 11111 00000 00000 00000",
    ".": "00000 00000 00000 00000 00000 01100 01100",
    ":": "00000 01100 01100 00000 01100 01100 00000",
    "(": "00010 00100 01000 01000 01000 00100 00010",
    ")": "01000 00100 00010 00010 00010 00100 01000",
    "_": "00000 00000 00000 00000 00000 00000 11111",
    "+": "00000 00100 00100 11111 00100 00100 00000",
    "=": "00000 00000 11111 00000 11111 00000 00000",
    "<": "00001 00010 00100 01000 00100 00010 00001",
    ">": "10000 01000 00100 00010 00100 01000 10000",
    " ": "00000 00000 00000 00000 00000 00000 00000",
}
FONT = {k: v.split() for k, v in _F.items()}


def text(buf, W, H, x, y, s, col, scale=2):
    for ch in s.upper():
        glyph = FONT.get(ch, FONT[" "])
        for gy, row in enumerate(glyph):
            for gx, bit in enumerate(row):
                if bit != "1":
                    continue
                for dy in range(scale):
                    for dx in range(scale):
                        px, py = x + (gx * scale) + dx, y + (gy * scale) + dy
                        if 0 <= px < W and 0 <= py < H:
                            o = (py * W + px) * 4
                            buf[o:o + 4] = bytes((col[0], col[1], col[2], 255))
        x += 6 * scale


def chunk(t, d):
    return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)


def save_png(path, W, H, buf):
    raw = bytearray()
    for y in range(H):
        raw.append(0)
        raw.extend(buf[y * W * 4:(y + 1) * W * 4])
    path.write_bytes(b"\x89PNG\r\n\x1a\n"
                     + chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 6, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
                     + chunk(b"IEND", b""))
    print("saved", path.name, f"{W}x{H}", path.stat().st_size, "B")


# ---------------------------------------------------------------- PNG 解码（标准库）
def decode_png(blob):
    pos, ids, w, h, ct = 8, [], None, None, None
    while pos < len(blob):
        n = struct.unpack(">I", blob[pos:pos + 4])[0]
        tag, body = blob[pos + 4:pos + 8], blob[pos + 8:pos + 8 + n]
        pos += 12 + n
        if tag == b"IHDR":
            w, h, bd, ct = struct.unpack(">IIBB", body[:10])
        elif tag == b"IDAT":
            ids.append(body)
        elif tag == b"IEND":
            break
    bpp = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ct]
    raw = zlib.decompress(b"".join(ids))
    stride, rows, prev, i = w * bpp, [], bytearray(w * bpp), 0
    for _ in range(h):
        f = raw[i]; i += 1
        line = bytearray(raw[i:i + stride]); i += stride
        if f:
            for x in range(stride):
                a = line[x - bpp] if x >= bpp else 0
                b = prev[x]
                c = prev[x - bpp] if x >= bpp else 0
                if f == 1: v = a
                elif f == 2: v = b
                elif f == 3: v = (a + b) // 2
                else:
                    p = a + b - c
                    pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                    v = a if pa <= pb and pa <= pc else (b if pb <= pc else c)
                line[x] = (line[x] + v) & 255
        prev = line
        if bpp == 4:
            rows.append(bytes(line))
        elif bpp == 3:
            rows.append(b"".join(bytes(line[k:k + 3]) + b"\xff" for k in range(0, stride, 3)))
        else:
            rows.append(b"".join(bytes((line[k],) * 3) + b"\xff" for k in range(w)))
    return w, h, rows


# ---------------------------------------------------------------- OBJ 解析
def load_obj(txt):
    V, VT, VN, faces, cur = [], [], [], [], None
    for ln in txt.splitlines():
        if ln.startswith("v "):
            V.append(tuple(map(float, ln.split()[1:4])))
        elif ln.startswith("vt "):
            VT.append(tuple(map(float, ln.split()[1:3])))
        elif ln.startswith("vn "):
            VN.append(tuple(map(float, ln.split()[1:4])))
        elif ln.startswith("g "):
            cur = ln[2:].strip()
        elif ln.startswith("f ") and cur:
            fv = []
            for tok in ln.split()[1:]:
                p = tok.split("/")
                vi = int(p[0]) - 1
                ti = int(p[1]) - 1 if len(p) > 1 and p[1] else -1
                ni = int(p[2]) - 1 if len(p) > 2 and p[2] else -1
                fv.append((vi, ti, ni))
            faces.append((cur, fv))
    return V, VT, VN, faces


# ---------------------------------------------------------------- 光栅器
LIGHT = (-0.32, 0.55, 0.77)
_ln = math.sqrt(sum(c * c for c in LIGHT))
LIGHT = tuple(c / _ln for c in LIGHT)


def background(W, H):
    buf = bytearray(W * H * 4)
    for y in range(H):
        t = y / max(1, H - 1)
        c = bytes((int(238 - 16 * t), int(241 - 16 * t), int(236 - 14 * t), 255))
        buf[y * W * 4:(y + 1) * W * 4] = c * W
    return buf


def sample(rows, w, h, U, V):
    """GL_REPEAT + 双线性采样；V 已是解析器翻转后的值。"""
    x = (U % 1.0) * w - 0.5
    y = (V % 1.0) * h - 0.5
    x0, y0 = math.floor(x), math.floor(y)
    fx, fy = x - x0, y - y0
    xi0, xi1 = x0 % w, (x0 + 1) % w
    yi0, yi1 = y0 % h, (y0 + 1) % h
    r0, r1 = rows[yi0], rows[yi1]
    a, b = r0[xi0 * 4:xi0 * 4 + 4], r0[xi1 * 4:xi1 * 4 + 4]
    c, d = r1[xi0 * 4:xi0 * 4 + 4], r1[xi1 * 4:xi1 * 4 + 4]
    return tuple((a[k] * (1 - fx) + b[k] * fx) * (1 - fy)
                 + (c[k] * (1 - fx) + d[k] * fx) * fy for k in range(4))


def render(W, H, groups, model, tex, cam):
    V, VT, VN, faces = model
    tw, th, rows = tex
    buf = background(W, H)
    zb = [-1e9] * (W * H)
    ry, rx, s = cam["ry"], cam["rx"], cam["s"]
    cy_, sy_, cx_, sx_ = math.cos(ry), math.sin(ry), math.cos(rx), math.sin(rx)
    cx, cy, cz = cam["cx"], cam["cy"], cam["cz"]

    def project(p):
        x, y, z = p[0] - cx, p[1] - cy, p[2] - cz
        x1, z1 = x * cy_ + z * sy_, -x * sy_ + z * cy_
        y2, z2 = y * cx_ - z1 * sx_, y * sx_ + z1 * cx_
        return (W / 2 + x1 * s, H / 2 - y2 * s, z2)

    drawn = 0
    for g, fv in faces:
        if g not in groups:
            continue
        lum = "luminous" in g
        sp = [project(V[vi]) for vi, _, _ in fv]
        uvs = [(VT[ti][0], 1.0 - VT[ti][1]) if ti >= 0 else (0.0, 0.0)
               for _, ti, _ in fv]
        # 世界坐标面法线（取前三点）
        a3, b3, c3 = (V[fv[k][0]] for k in range(3))
        u = [b3[i] - a3[i] for i in range(3)]
        v = [c3[i] - a3[i] for i in range(3)]
        n = (u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0])
        nl = math.sqrt(sum(q * q for q in n)) or 1.0
        n = tuple(q / nl for q in n)
        shade = 1.0 if lum else 0.52 + 0.48 * abs(sum(n[i] * LIGHT[i] for i in range(3)))

        for k in range(1, len(fv) - 1):
            tri = (0, k, k + 1)
            p0, p1, p2 = sp[tri[0]], sp[tri[1]], sp[tri[2]]
            area = (p1[1] - p2[1]) * (p0[0] - p2[0]) + (p2[0] - p1[0]) * (p0[1] - p2[1])
            if abs(area) <= 1e-9:             # 双面都画，靠 z-buffer 取最近表面
                continue
            x0 = max(0, int(min(p0[0], p1[0], p2[0])))
            x1 = min(W - 1, int(max(p0[0], p1[0], p2[0])) + 1)
            y0 = max(0, int(min(p0[1], p1[1], p2[1])))
            y1 = min(H - 1, int(max(p0[1], p1[1], p2[1])) + 1)
            if x1 < x0 or y1 < y0:
                continue
            inv = 1.0 / area
            u0, u1, u2 = uvs[tri[0]], uvs[tri[1]], uvs[tri[2]]
            z0, z1, z2 = p0[2], p1[2], p2[2]
            for py in range(y0, y1 + 1):
                yy = py + 0.5
                for px in range(x0, x1 + 1):
                    xx = px + 0.5
                    l0 = ((p1[1] - p2[1]) * (xx - p2[0]) + (p2[0] - p1[0]) * (yy - p2[1])) * inv
                    l1 = ((p2[1] - p0[1]) * (xx - p2[0]) + (p0[0] - p2[0]) * (yy - p2[1])) * inv
                    l2 = 1.0 - l0 - l1
                    if l0 < 0 or l1 < 0 or l2 < 0:
                        continue
                    z = l0 * z0 + l1 * z1 + l2 * z2
                    idx = py * W + px
                    if z < zb[idx] - 1e-6:
                        continue
                    U = l0 * u0[0] + l1 * u1[0] + l2 * u2[0]
                    Vv = l0 * u0[1] + l1 * u1[1] + l2 * u2[1]
                    r, g_, b_, a_ = sample(rows, tw, th, U, Vv)
                    alpha = a_ / 255.0
                    if alpha <= 0.003:
                        continue
                    o = idx * 4
                    if lum:                       # 加色发光层：不写深度，且只叠在实体表面
                        if zb[idx] < -1e8:
                            continue
                        for ci, val in enumerate((r, g_, b_)):
                            buf[o + ci] = min(255, int(buf[o + ci] + val * alpha * 0.9))
                    else:
                        for ci, val in enumerate((r, g_, b_)):
                            lit = min(255, val * shade)
                            buf[o + ci] = int(buf[o + ci] * (1 - alpha) + lit * alpha)
                        zb[idx] = z
            drawn += 1
    return buf, drawn


def paste(dst, DW, src, W, H, ox, oy):
    for y in range(H):
        dst[(oy + y) * DW * 4 + ox * 4:(oy + y) * DW * 4 + (ox + W) * 4] = \
            src[y * W * 4:(y + 1) * W * 4]


def diamond(buf, W, H, cx, cy, R, col):
    """原版 durability.obj 的 base/color 菱形底板（±70.7，GUI 里额外绘制）。"""
    for y in range(H):
        for x in range(W):
            d = abs(x - cx) + abs(y - cy)
            if R - 5 <= d <= R:
                o = (y * W + x) * 4
                buf[o:o + 4] = bytes(col)


# ---------------------------------------------------------------- 主流程
def main():
    with zipfile.ZipFile(JAR) as z:
        model = load_obj(z.read(OBJ_ENTRY).decode("utf-8", "replace"))
        tex = decode_png(z.read(PNG_ENTRY))
    print(f"OBJ: {len(model[0])} 顶点 / {len(model[1])} UV / {len(model[3])} 面")
    print(f"贴图: {tex[0]}x{tex[1]}")

    # ---- 3D 四态 ----
    CW, CH, GAP, MARGIN = 540, 170, 16, 16
    W, H = 2 * CW + 2 * MARGIN + GAP, 2 * CH + 2 * MARGIN + GAP + 28
    sheet = background(W, H)
    cam3 = dict(ry=0.20, rx=0.11, s=1.42, cx=-128, cy=-4, cz=0)
    states = [
        ("SHEATHED  (STATIC)", {"sheath", "blade", "sheath_luminous"},
         "SHEATH + BLADE + SHEATH_LUMINOUS"),
        ("DRAWN  (BARE BLADE)", {"blade", "blade_luminous"},
         "BLADE + BLADE_LUMINOUS"),
        ("BROKEN", {"blade_damaged", "blade_damaged_luminous"},
         "BLADE_DAMAGED + LUMINOUS"),
        ("EDGEFRAGMENT  (DROPPED)", {"blade_fragment"},
         "BLADE_FRAGMENT ONLY"),
    ]
    for i, (label, groups, note) in enumerate(states):
        ox = MARGIN + (i % 2) * (CW + GAP)
        oy = MARGIN + (i // 2) * (CH + GAP) + 28
        img, n = render(CW, CH, groups, model, tex, cam3)
        text(img, CW, CH, 12, 10, label, (38, 52, 44), 2)
        text(img, CW, CH, 12, CH - 20, note, (92, 104, 96), 1)
        for xx in range(CW):
            for yy in (0, CH - 1):
                o = (yy * CW + xx) * 4
                img[o:o + 4] = b"\x9a\xa8\x9e\xff"
        paste(sheet, W, img, CW, CH, ox, oy)
        print(f"  {label:26s} {n:4d} 个正面三角形")
    text(sheet, W, H, MARGIN, 6,
         "TERMINALBLADE 0.1.3 / SOFTWARE RENDER / UV AS SLASHBLADE LOADS IT (U > 1 = REPEAT)",
         (38, 52, 44), 1)
    save_png(OUT / "jar013_render_3d.png", W, H, sheet)

    # ---- 刀身特写：同一渲染，只把中轴附近放大，便于看清贴图纹样 ----
    DW, DH = 540, 170
    Wd, Hd = 2 * DW + 2 * MARGIN + GAP, DH + 2 * MARGIN + 28
    detail = background(Wd, Hd)
    camD = dict(ry=0.20, rx=0.11, s=4.0, cx=-205, cy=-6, cz=0)
    for i, (label, groups, note) in enumerate([
        ("SHEATH DETAIL", {"sheath", "sheath_luminous"},
         "X -272 .. -137  /  4X ZOOM"),
        ("BLADE DETAIL", {"blade", "blade_luminous"},
         "X -272 .. -137  /  4X ZOOM"),
    ]):
        img, n = render(DW, DH, groups, model, tex, camD)
        text(img, DW, DH, 12, 8, label, (38, 52, 44), 2)
        text(img, DW, DH, 12, DH - 18, note, (92, 104, 96), 1)
        paste(detail, Wd, img, DW, DH, MARGIN + i * (DW + GAP), MARGIN + 28)
        print(f"  {label:26s} {n:4d} 个三角形")
    text(detail, Wd, Hd, MARGIN, 6,
         "DETAIL / SAME UV RULES / 4X", (38, 52, 44), 1)
    save_png(OUT / "jar013_render_detail.png", Wd, Hd, detail)

    # ---- 物品栏三态图标 ----
    IW, IH = 270, 270
    W2, H2 = 3 * IW + 2 * MARGIN + 2 * GAP, IH + 2 * MARGIN + 28
    sheet2 = background(W2, H2)
    camI = dict(ry=0.0, rx=0.0, s=2.75, cx=0, cy=0, cz=0)
    icons = [
        ("ITEM / SHEATHED", {"item_blade", "item_blade_luminous"}),
        ("ITEM / BROKEN", {"item_damaged", "item_damaged_luminous"}),
        ("ITEM / NOSCABBARD", {"item_bladens", "item_bladens_luminous"}),
    ]
    for i, (label, groups) in enumerate(icons):
        ox = MARGIN + i * (IW + GAP)
        oy = MARGIN + 28
        img, n = render(IW, IH, groups, model, tex, camI)
        diamond(img, IW, IH, IW / 2, IH / 2, 116, (46, 196, 214, 255))
        text(img, IW, IH, 10, 8, label, (38, 52, 44), 2)
        text(img, IW, IH, 10, IH - 18, "CYAN FRAME = DURABILITY BASE", (92, 104, 96), 1)
        paste(sheet2, W2, img, IW, IH, ox, oy)
        print(f"  {label:22s} {n:4d} 个正面三角形")
    text(sheet2, W2, H2, MARGIN, 6,
         "INVENTORY ICONS / RENDERED FROM THE JAR'S OWN ITEM_* GROUPS", (38, 52, 44), 1)
    save_png(OUT / "jar013_render_icons.png", W2, H2, sheet2)


if __name__ == "__main__":
    main()
