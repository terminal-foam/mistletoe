#!/usr/bin/env python3
"""初雪流水线自带的极简 PNG 编解码（纯标准库）。

从 art_demo/render_jar013.py 抽出 PNG 相关部分，去掉对 jar / 外部路径的依赖，
使 first_snow_pipeline 目录可以整体拷进仓库 tools/first_snow/ 后独立运行。

decode_png 返回 (w, h, rows)，rows 是每行 length = w*4 的 RGBA bytes。
text() 写满 4 字节 RGBA —— 写 3 字节会让 bytearray 像素流整体错位（旧坑）。
"""
from __future__ import annotations

import struct
import zlib

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
    """把 ASCII 标签写进 RGBA 缓冲区；每像素固定 4 字节。"""
    for ch in s.upper():
        glyph = FONT.get(ch, FONT[" "])
        for gy, row in enumerate(glyph):
            for gx, bit in enumerate(row):
                if bit != "1":
                    continue
                for dy in range(scale):
                    for dx in range(scale):
                        px, py = x + gx * scale + dx, y + gy * scale + dy
                        if 0 <= px < W and 0 <= py < H:
                            o = (py * W + px) * 4
                            buf[o:o + 4] = bytes((col[0], col[1], col[2], 255))
        x += 6 * scale


def _chunk(tag, data):
    return (struct.pack(">I", len(data)) + tag + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))


def save_png(path, W, H, buf):
    raw = bytearray()
    for y in range(H):
        raw.append(0)
        raw.extend(buf[y * W * 4:(y + 1) * W * 4])
    path.write_bytes(b"\x89PNG\r\n\x1a\n"
                     + _chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 6, 0, 0, 0))
                     + _chunk(b"IDAT", zlib.compress(bytes(raw), 9))
                     + _chunk(b"IEND", b""))
    return path


def decode_png(blob):
    pos, ids, w, h, ct = 8, [], None, None, None
    while pos < len(blob):
        n = struct.unpack(">I", blob[pos:pos + 4])[0]
        tag, body = blob[pos + 4:pos + 8], blob[pos + 8:pos + 8 + n]
        pos += 12 + n
        if tag == b"IHDR":
            w, h, _bd, ct = struct.unpack(">IIBB", body[:10])
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
                if f == 1:
                    v = a
                elif f == 2:
                    v = b
                elif f == 3:
                    v = (a + b) // 2
                else:
                    p = a + b - c
                    pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                    v = a if pa <= pb and pa <= pc else (b if pb <= pc else c)
                line[x] = (line[x] + v) & 255
        prev = line
        if bpp == 4:
            rows.append(bytes(line))
        elif bpp == 3:
            rows.append(b"".join(bytes(line[k:k + 3]) + b"\xff"
                                for k in range(0, stride, 3)))
        else:
            rows.append(b"".join(bytes((line[k],) * 3) + b"\xff" for k in range(w)))
    return w, h, rows
