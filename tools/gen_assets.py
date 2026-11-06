#!/usr/bin/env python3
"""Generate the original placeholder katana, texture, particles, and tones."""

import math
import struct
import subprocess
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODEL = ROOT / "src/main/resources/assets/terminalblade/model/named"
PARTICLE = ROOT / "src/main/resources/assets/terminalblade/textures/particle"
SOUNDS = ROOT / "src/main/resources/assets/terminalblade/sounds"


def write_png(path: Path, width: int, height: int, pixel):
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        for x in range(width):
            raw.extend(pixel(x, y))
    compressor = zlib.compressobj()
    compressed = compressor.compress(bytes(raw)) + compressor.flush()

    def chunk(tag: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", compressed)
    png += chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)


class Obj:
    def __init__(self):
        self.lines = ["# Mistletoe placeholder katana. Original geometry, not taken from SlashBlade."]
        self.v = []
        self.vt = []
        self.vn = []
        self.group = None

    def use(self, name: str):
        self.lines.append(f"g {name}")
        self.group = name

    def add_vertex(self, x, y, z):
        self.v.append((x, y, z))
        self.lines.append(f"v {x:.4f} {y:.4f} {z:.4f}")
        return len(self.v)

    def add_uv(self, u, v):
        self.vt.append((u, v))
        self.lines.append(f"vt {u:.4f} {v:.4f}")
        return len(self.vt)

    def add_normal(self, x, y, z):
        length = math.sqrt(x * x + y * y + z * z) or 1.0
        self.vn.append((x / length, y / length, z / length))
        self.lines.append(f"vn {x / length:.4f} {y / length:.4f} {z / length:.4f}")
        return len(self.vn)

    def face(self, corners):
        parts = []
        for vi, ti, ni in corners:
            parts.append(f"{vi}/{ti}/{ni}")
        self.lines.append("f " + " ".join(parts))

    def box(self, x0, x1, y0, y1, z0, z1, uv):
        u0, v0, u1, v1 = uv
        verts = [
            self.add_vertex(x0, y0, z0),
            self.add_vertex(x1, y0, z0),
            self.add_vertex(x1, y1, z0),
            self.add_vertex(x0, y1, z0),
            self.add_vertex(x0, y0, z1),
            self.add_vertex(x1, y0, z1),
            self.add_vertex(x1, y1, z1),
            self.add_vertex(x0, y1, z1),
        ]
        uv_ids = [
            self.add_uv(u0, v0),
            self.add_uv(u1, v0),
            self.add_uv(u1, v1),
            self.add_uv(u0, v1),
        ]
        faces = [
            ((0, 1, 2, 3), (0, 0, -1)),
            ((5, 4, 7, 6), (0, 0, 1)),
            ((4, 0, 3, 7), (-1, 0, 0)),
            ((1, 5, 6, 2), (1, 0, 0)),
            ((3, 2, 6, 7), (0, 1, 0)),
            ((4, 5, 1, 0), (0, -1, 0)),
        ]
        for indexes, normal in faces:
            ni = self.add_normal(*normal)
            self.face([(verts[indexes[i]], uv_ids[i], ni) for i in range(4)])

    def prism(self, x_base, x_tip, y0, y1, z0, z1, tip_scale, uv):
        u0, v0, u1, v1 = uv
        cy = (y0 + y1) * 0.5
        cz = (z0 + z1) * 0.5

        def tip(value, center):
            return center + (value - center) * tip_scale

        corners = [
            (x_base, y0, z0),
            (x_base, y1, z0),
            (x_base, y1, z1),
            (x_base, y0, z1),
            (x_tip, tip(y0, cy), tip(z0, cz)),
            (x_tip, tip(y1, cy), tip(z0, cz)),
            (x_tip, tip(y1, cy), tip(z1, cz)),
            (x_tip, tip(y0, cy), tip(z1, cz)),
        ]
        ids = [self.add_vertex(*corner) for corner in corners]
        uv_ids = [self.add_uv(u0, v0), self.add_uv(u1, v0), self.add_uv(u1, v1), self.add_uv(u0, v1)]
        quads = [
            (0, 1, 2, 3),
            (4, 7, 6, 5),
            (0, 4, 5, 1),
            (1, 5, 6, 2),
            (2, 6, 7, 3),
            (3, 7, 4, 0),
        ]
        for quad in quads:
            a, b, c = (corners[quad[0]], corners[quad[1]], corners[quad[2]])
            nx = (b[1] - a[1]) * (c[2] - a[2]) - (b[2] - a[2]) * (c[1] - a[1])
            ny = (b[2] - a[2]) * (c[0] - a[0]) - (b[0] - a[0]) * (c[2] - a[2])
            nz = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
            ni = self.add_normal(nx, ny, nz)
            self.face([(ids[quad[i]], uv_ids[i], ni) for i in range(4)])


def build_model() -> str:
    obj = Obj()
    metal = (0.02, 0.52, 0.48, 0.98)
    handle = (0.52, 0.52, 0.98, 0.98)
    guard = (0.52, 0.02, 0.98, 0.48)
    sheath = (0.02, 0.02, 0.48, 0.48)
    edge = (0.78, 0.55, 0.98, 0.95)

    obj.use("blade")
    obj.prism(-28.0, -286.0, -1.15, 1.15, -7.4, 7.4, 0.02, metal)
    obj.box(-18.0, 48.0, -3.3, 3.3, -3.6, 3.6, handle)
    obj.box(42.0, 56.0, -5.2, 5.2, -5.2, 5.2, guard)
    obj.box(-36.0, -16.0, -16.0, 16.0, -1.1, 1.1, guard)
    obj.box(-36.0, -16.0, -1.1, 1.1, -16.0, 16.0, guard)

    obj.use("blade_luminous")
    obj.prism(-30.0, -284.0, 0.55, 1.35, -0.35, 0.35, 0.05, edge)

    obj.use("blade_damaged")
    obj.prism(-28.0, -118.0, -1.15, 1.15, -7.4, 7.4, 0.08, metal)
    obj.box(-18.0, 48.0, -3.3, 3.3, -3.6, 3.6, handle)
    obj.box(-36.0, -16.0, -16.0, 16.0, -1.1, 1.1, guard)

    obj.use("blade_damaged_luminous")
    obj.prism(-30.0, -116.0, 0.55, 1.35, -0.35, 0.35, 0.08, edge)

    obj.use("sheath")
    obj.prism(-34.0, -292.0, -2.5, 2.5, -9.2, 9.2, 0.08, sheath)
    obj.box(-40.0, -24.0, -4.0, 4.0, -11.0, 11.0, sheath)

    obj.use("sheath_luminous")
    obj.prism(-36.0, -288.0, 1.6, 2.3, -0.45, 0.45, 0.12, edge)

    icon(obj, "item_blade", False, True, False, metal, handle, guard, sheath, edge)
    icon(obj, "item_blade_luminous", False, False, True, metal, handle, guard, sheath, edge)
    icon(obj, "item_damaged", True, False, False, metal, handle, guard, sheath, edge)
    icon(obj, "item_damaged_luminous", True, False, True, metal, handle, guard, sheath, edge)
    icon(obj, "item_bladens", False, False, False, metal, handle, guard, sheath, edge)
    icon(obj, "item_bladens_luminous", False, False, True, metal, handle, guard, sheath, edge)
    return "\n".join(obj.lines) + "\n"


def icon(obj, name, broken, with_sheath, luminous, metal, handle, guard, sheath, edge):
    """Inventory groups sit near the origin so SlashBladeTEISR's 0.008 scale can show the blade."""
    obj.use(name)
    if luminous:
        obj.box(-0.35, 0.35, 2.0, 24.0 if broken else 58.0, -0.25, 0.25, edge)
        return
    if with_sheath and not broken:
        obj.box(-4.4, 4.4, -4.0, 64.0, -1.5, 1.5, sheath)
    obj.box(-1.2, 1.2, 0.0, 24.0 if broken else 60.0, -0.9, 0.9, metal)
    obj.box(-2.0, 2.0, -32.0, -2.0, -1.8, 1.8, handle)
    obj.box(-9.0, 9.0, -6.0, -1.0, -0.7, 0.7, guard)


def blade_texture(x, y):
    # 64x64, top half is bright metal, bottom-left is sheath, bottom-right is guard.
    if y < 32 and x < 32:
        shade = 226 + (x + y) % 5
        return (shade, shade, min(255, shade + 6), 255)
    if y < 32:
        g = 42 + (x % 3) * 4
        return (18, g, 28, 255)
    if x < 32:
        return (28, 30, 34, 255)
    return (214, 220, 226, 255)


def disc(cx, cy, radius, color, background=(0, 0, 0, 0)):
    def pixel(x, y):
        if (x - cx) ** 2 + (y - cy) ** 2 <= radius ** 2:
            return color
        return background

    return pixel


def main():
    MODEL.mkdir(parents=True, exist_ok=True)
    (MODEL / "mistletoe.obj").write_text(build_model(), encoding="utf-8")
    write_png(MODEL / "mistletoe.png", 64, 64, blade_texture)
    write_png(PARTICLE / "petal.png", 16, 16, disc(7.5, 7.5, 6.2, (244, 255, 246, 230)))
    write_png(PARTICLE / "berry.png", 16, 16, disc(7.5, 7.5, 4.2, (255, 255, 255, 240)))
    write_png(PARTICLE / "spark.png", 16, 16, disc(7.5, 7.5, 2.4, (255, 255, 250, 255)))

    tones = {
        "draw": (520, 0.28),
        "swing": (740, 0.16),
        "hit": (980, 0.12),
        "sacrifice": (220, 0.45),
        "super_charge": (640, 0.55),
        "super_release": (180, 0.7),
    }
    SOUNDS.mkdir(parents=True, exist_ok=True)
    for name, (freq, duration) in tones.items():
        target = SOUNDS / f"{name}.ogg"
        subprocess.run(
            [
                "ffmpeg", "-y", "-f", "lavfi",
                "-i", f"sine=frequency={freq}:duration={duration}",
                "-c:a", "libvorbis", "-q:a", "4",
                str(target),
            ],
            check=True,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
    print("assets written")


if __name__ == "__main__":
    main()
