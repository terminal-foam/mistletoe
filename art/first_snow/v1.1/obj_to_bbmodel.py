#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
把 SlashBlade 风格的 OBJ 转成 Blockbench 5.2.2 原生 .bbmodel（双击即可打开、可编辑），
并额外产出一份「Blockbench 导入专用」的标准 OBJ + MTL。

背景（已从 Blockbench 5.2.2 的 dist/bundle.js 反编译确认）：
  * .obj 只注册为 Codec（导出用），不是 ModelFormat —— 所以 File > Open / 双击打不开，
    只能走 File > Import > "Import OBJ Model"（action id: import_obj）。
  * 该 action 的 condition 是 modes:["edit"] && Format.meshes，
    Java Block/Item 等 box_uv 格式下不出现，必须选 Generic Model（model_format: "free"）。
  * 导入对话框默认 scale = 16，会把所有顶点乘 16；拔刀剑 OBJ 用的是几百单位坐标，
    必须把 scale 改成 1，否则模型大得看不见。
  * 导入器按 o / g 行新建 Mesh，v / vt / vn 索引是全局累积的 1-based 编号。
  * 导入器只有提供 MTL 且其中有 map_Kd 时才会给面附上贴图。
  * .bbmodel 的 meta.format_version 不能大于 "5.0"，否则弹「更新版本」错误。

因此最省事的交付是 .bbmodel：纹理以 data URL 内嵌，UV 精确还原，不需要任何导入设置。
"""
import base64
import json
import re
import struct
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE / "out"
OUT.mkdir(parents=True, exist_ok=True)
OBJ_PATH = OUT / "demo_model.obj"
ATLAS_PATH = OUT / "demo_atlas.png"
BBMODEL_PATH = OUT / "first_snow.bbmodel"
BB_OBJ_PATH = OUT / "demo_model_blockbench.obj"
MTL_PATH = OUT / "materials.mtl"

FORMAT_VERSION = "5.0"          # Blockbench 5.2.2 能接受的最高版本
MODEL_FORMAT = "free"           # Generic Model，meshes: true
TEX_ID = 0                      # faces.texture 是 Texture.all 的下标


# ------------------------------------------------------------------ OBJ 解析
def parse_obj(path):
    """返回 (V, VT, VN, groups)；groups 为 [{name, faces:[[(vi,ti,ni),...], ...]}]。"""
    V, VT, VN, groups = [], [], [], []
    cur = None
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        p = line.split()
        tag = p[0]
        if tag == "v":
            V.append(tuple(float(x) for x in p[1:4]))
        elif tag == "vt":
            VT.append(tuple(float(x) for x in p[1:3]))
        elif tag == "vn":
            VN.append(tuple(float(x) for x in p[1:4]))
        elif tag in ("g", "o"):
            cur = {"name": p[1] if len(p) > 1 else "group", "faces": []}
            groups.append(cur)
        elif tag == "f":
            if cur is None:
                cur = {"name": "unknown", "faces": []}
                groups.append(cur)
            face = []
            for tok in p[1:]:
                bits = tok.split("/")
                vi = int(bits[0]) if bits[0] else None
                ti = int(bits[1]) if len(bits) > 1 and bits[1] else None
                ni = int(bits[2]) if len(bits) > 2 and bits[2] else None
                face.append((vi, ti, ni))
            cur["faces"].append(face)
    return V, VT, VN, groups


def png_size(path):
    b = path.read_bytes()
    if b[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("不是 PNG: %s" % path)
    w, h = struct.unpack(">II", b[16:24])
    return w, h


def uuid(prefix, i):
    return "%s-%08x" % (prefix, i)


# ------------------------------------------------------------------ .bbmodel
def build_bbmodel(V, VT, VN, groups, W, H, atlas_bytes):
    data_url = "data:image/png;base64," + base64.b64encode(atlas_bytes).decode("ascii")
    elements, outliner, group_defs = [], [], []

    for gi, g in enumerate(groups):
        # 每个 (顶点索引, UV索引) 组合独占一个 Blockbench 顶点，避免同点不同 UV 打架
        vmap, vertices = {}, {}
        faces = {}
        for fi, face in enumerate(g["faces"]):
            keys, uv = [], {}
            for (vi, ti, _ni) in face:
                if vi is None or not (1 <= vi <= len(V)):
                    continue
                k = vmap.get((vi, ti))
                if k is None:
                    k = "v%05d" % len(vmap)
                    vmap[(vi, ti)] = k
                    vertices[k] = [round(c, 4) for c in V[vi - 1]]
                keys.append(k)
                if ti and 1 <= ti <= len(VT):
                    u, v = VT[ti - 1]
                    # OBJ vt 是左下原点，Blockbench UV 是左上原点、单位为像素
                    uv[k] = [round(u * W, 3), round((1.0 - v) * H, 3)]
                else:
                    uv[k] = [0.0, 0.0]
            if len(keys) >= 3:
                faces["f%05d" % fi] = {
                    "vertices": keys,
                    "uv": uv,
                    "texture": TEX_ID,
                }

        if not faces:
            continue
        mesh_uuid = uuid("mesh", gi)
        elements.append({
            "name": g["name"],
            "type": "mesh",
            "uuid": mesh_uuid,
            "position": [0, 0, 0],
            "rotation": [0, 0, 0],
            "origin": [0, 0, 0],
            "color": 0,
            "visibility": True,
            "locked": False,
            "render_order": "default",
            "allow_spectral": False,
            "vertices": vertices,
            "faces": faces,
        })
        grp_uuid = uuid("group", gi)
        group_defs.append({
            "name": g["name"],
            "uuid": grp_uuid,
            "export": True,
            "mirror_uv": False,
            "isOpen": False,
            "locked": False,
            "visibility": True,
            "autouv": 0,
            "selected": False,
            "primary_selected": False,
            "shade": True,
            "reset": False,
            "color": 0,
            "bedrock_binding": "",
            "origin": [0, 0, 0],
            "position": [0, 0, 0],
            "rotation": [0, 0, 0],
            "children": [],
        })
        # outliner 里的 group 只放 uuid/isOpen/children —— 与 Blockbench 自己导出的结构一致
        outliner.append({
            "uuid": grp_uuid,
            "isOpen": False,
            "children": [mesh_uuid],
        })

    model = {
        "meta": {
            "format_version": FORMAT_VERSION,
            "model_format": MODEL_FORMAT,
            "box_uv": False,
        },
        "name": "mistletoe",
        "model_identifier": "",
        "visible_box": [1, 1, 1],
        "variable_placeholders": "",
        "variable_placeholder_buttons": [],
        "timeline_setups": [],
        "resolution": {"width": W, "height": H},
        "elements": elements,
        "groups": group_defs,
        "outliner": outliner,
        "textures": [{
            "path": "",
            "name": ATLAS_PATH.name,
            "folder": "",
            "namespace": "",
            "id": str(TEX_ID),
            "group": "",
            "width": W,
            "height": H,
            "uv_width": W,
            "uv_height": H,
            "particle": False,
            "use_as_default": False,
            "layers_enabled": False,
            "sync_to_project": "",
            "render_mode": "default",
            "render_sides": "auto",
            "frame_time": 1,
            "frame_order_type": "loop",
            "frame_order": [],
            "frame_interpolate": False,
            "visible": True,
            "mode": "bitmap",
            "saved": True,
            "internal": True,
            "uuid": uuid("texture", 0),
            "source": data_url,
        }],
    }
    return model


# ------------------------------------------- Blockbench「Import OBJ」专用 OBJ
def build_blockbench_obj(V, VT, VN, groups):
    """按组分段输出：o → 该组的 v/vt/vn → usemtl → f。
    索引仍是 1-based 全局累加（Blockbench 导入器的 l/d/u 数组不清空）。"""
    out = [
        "# Blockbench Import-OBJ 专用版本（与 SlashBlade 版几何一致，分组顺序不同）",
        "# File > Import > Import OBJ Model；格式选 Generic Model；Scale 必须填 1",
        "mtllib " + MTL_PATH.name,
    ]
    nv = nt = nn = 0
    for g in groups:
        if not g["faces"]:
            continue
        local_v, local_t, local_n = {}, {}, {}
        rows_v, rows_t, rows_n = [], [], []
        for face in g["faces"]:
            for (vi, ti, ni) in face:
                if vi and 1 <= vi <= len(V) and vi not in local_v:
                    nv += 1
                    local_v[vi] = nv
                    rows_v.append("v %.6f %.6f %.6f" % V[vi - 1])
                if ti and 1 <= ti <= len(VT) and ti not in local_t:
                    nt += 1
                    local_t[ti] = nt
                    rows_t.append("vt %.8f %.8f" % VT[ti - 1])
                if ni and 1 <= ni <= len(VN) and ni not in local_n:
                    nn += 1
                    local_n[ni] = nn
                    rows_n.append("vn %.6f %.6f %.6f" % VN[ni - 1])
        out.append("o " + re.sub(r"[^\w.]", "_", g["name"]))
        out.extend(rows_v)
        out.extend(rows_t)
        out.extend(rows_n)
        out.append("usemtl mistletoe_atlas")
        for face in g["faces"]:
            toks = []
            for (vi, ti, ni) in face:
                a = local_v.get(vi, 0)
                b = local_t.get(ti, 0)
                c = local_n.get(ni, 0)
                toks.append("%d/%d/%d" % (a, b, c))
            out.append("f " + " ".join(toks))
    return "\n".join(out) + "\n"


def main():
    V, VT, VN, groups = parse_obj(OBJ_PATH)
    W, H = png_size(ATLAS_PATH)
    atlas_bytes = ATLAS_PATH.read_bytes()
    print("OBJ: %d 顶点 / %d UV / %d 法线 / %d 分组 / %d 面"
          % (len(V), len(VT), len(VN), len(groups), sum(len(g["faces"]) for g in groups)))
    print("图集: %dx%d, %d 字节" % (W, H, len(atlas_bytes)))

    model = build_bbmodel(V, VT, VN, groups, W, H, atlas_bytes)
    BBMODEL_PATH.write_text(json.dumps(model, ensure_ascii=False, separators=(",", ":")),
                            encoding="utf-8")

    BB_OBJ_PATH.write_text(build_blockbench_obj(V, VT, VN, groups), encoding="utf-8")
    MTL_PATH.write_text(
        "newmtl mistletoe_atlas\n"
        "Ka 1.000000 1.000000 1.000000\n"
        "Kd 1.000000 1.000000 1.000000\n"
        "Ks 0.000000 0.000000 0.000000\n"
        "map_Kd %s\n" % ATLAS_PATH.name,
        encoding="utf-8",
    )

    # 静态自检：复现 Blockbench parse 的关键约束
    assert model["meta"]["format_version"] == FORMAT_VERSION
    assert model["meta"]["model_format"] == MODEL_FORMAT
    assert len(model["textures"]) > TEX_ID
    assert model["textures"][TEX_ID]["source"].startswith("data:image/png;base64,")
    all_mesh_uuids = [e["uuid"] for e in model["elements"]]
    out_uuids = [c for o in model["outliner"] for c in o["children"]]
    assert out_uuids == all_mesh_uuids, "outliner 与 elements 的 uuid 不一致"
    for e in model["elements"]:
        for fid, f in e["faces"].items():
            assert len(f["vertices"]) >= 3
            assert all(v in e["vertices"] for v in f["vertices"]), (e["name"], fid, "顶点缺引用")
            assert set(f["uv"]) == set(f["vertices"]), (e["name"], fid, "UV 顶点不匹配")
            assert f["texture"] == TEX_ID
            assert all(0 <= u <= W and 0 <= v <= H for u, v in f["uv"].values()), \
                (e["name"], fid, "UV 越界")
    print("\n自检通过：%d 个 mesh / %d 个三角面 / UV 全部在 0..%d 内"
          % (len(model["elements"]),
             sum(len(e["faces"]) for e in model["elements"]), W))
    print("已写出：")
    for p in (BBMODEL_PATH, BB_OBJ_PATH, MTL_PATH):
        print("  %-42s %8d 字节" % (p.name, p.stat().st_size))


if __name__ == "__main__":
    main()
