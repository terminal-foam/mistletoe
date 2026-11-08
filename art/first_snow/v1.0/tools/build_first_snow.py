#!/usr/bin/env python3
"""把初雪的可编辑 16 组模型归并成 SlashBlade 能加载的 13 组版本。

与 build_release.py 同一套规则（HashMap.put 同名组只留最后一段、
渲染层只请求固定组名、解析器不认 mtllib/usemtl），但：
  * 目标文件名固定为 first_snow.obj / first_snow.png；
  * 命名空间是 mistletoe（不是 terminalblade）；
  * 默认只产出到 first_snow/，绝不写仓库；--install 才拷贝并备份。

用法：python build_first_snow.py [--install] [--repo <assets/mistletoe 目录>]
"""
from __future__ import annotations

import argparse
import re
import shutil
from collections import defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
SRC_OBJ = HERE / "first_snow" / "demo_model.obj"
SRC_PNG = HERE / "first_snow" / "demo_atlas.png"
OUT_DIR = HERE / "first_snow"
REPO = Path("E:/dsh/terminalblade/src/main/resources/assets/mistletoe")

MERGE = {
    "mistletoe_branch": "blade",
    "mistletoe_leaf": "blade",
    "mistletoe_berry": "blade",
}
EXPECTED = [
    "blade", "blade_luminous", "blade_damaged", "blade_damaged_luminous",
    "sheath", "sheath_luminous", "effect",
    "item_blade", "item_blade_luminous",
    "item_damaged", "item_damaged_luminous",
    "item_bladens", "item_bladens_luminous",
]
NAME_RE = re.compile(r"^[A-Za-z0-9_.]+$")


def build():
    lines = SRC_OBJ.read_text(encoding="utf-8").splitlines()
    geom, buckets, order = [], defaultdict(list), []
    cur = None
    stripped = 0
    for raw in lines:
        s = raw.strip()
        if not s or s.startswith("#") or s.startswith(("v ", "vt ", "vn ")):
            geom.append(raw)
            continue
        if s.startswith(("g ", "o ")):
            name = s.split()[1]
            if not NAME_RE.match(name):
                raise ValueError("非法组名: %s" % name)
            cur = MERGE.get(name, name)
            if cur not in buckets:
                buckets[cur] = []
                order.append(cur)
            continue
        if s.startswith(("mtllib", "usemtl")):
            stripped += 1
            continue
        if s.startswith("f ") and cur:
            buckets[cur].append(raw)
        else:
            geom.append(raw)

    if sorted(order) != sorted(EXPECTED):
        raise SystemExit("输出组与清单 13 组不符: %s" % order)

    obj = OUT_DIR / "first_snow.obj"
    body = list(geom)
    for g in order:
        body.append("g " + g)
        body.extend(buckets[g])
    obj.write_text("\n".join(body) + "\n", encoding="utf-8")

    png = OUT_DIR / "first_snow.png"
    shutil.copy2(SRC_PNG, png)

    print("  %s %d 行 -> %s" % (SRC_OBJ.name, len(lines), obj.name))
    print("  归并组:", MERGE)
    print("  剔除 MTL 行:", stripped)
    print("  输出组 (%d): %s" % (len(order), ", ".join(order)))
    print("  重复组:", [g for g in order if order.count(g) > 1] or "无")
    print("  面数: %d   顶点行: %d" %
          (sum(len(v) for v in buckets.values()),
           sum(1 for line in geom if line.startswith("v "))))
    print("  贴图: %s (%d 字节)" % (png.name, png.stat().st_size))
    return obj, png


def install(obj: Path, png: Path, apply: bool, repo: Path):
    pairs = [(obj, repo / "model/named/first_snow.obj"),
             (png, repo / "model/named/first_snow.png")]
    print("\n  安装计划（进仓库，默认 dry-run）:")
    for src, dst in pairs:
        print("    %s -> %s" % (src.name, dst))
    if not apply:
        print("  未写入仓库。清单第 6 节还需同步改 first_snow.json 的 render.model。")
        return
    if not repo.is_dir():
        print("  !! 目录不存在，中止: %s" % repo)
        return
    for src, dst in pairs:
        if dst.exists():
            bak = dst.with_suffix(dst.suffix + ".bak")
            shutil.copy2(dst, bak)
            print("    备份 %s -> %s" % (dst.name, bak.name))
        shutil.copy2(src, dst)
        print("    已写入 %s" % dst.name)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--install", action="store_true", help="实际拷贝进仓库")
    ap.add_argument("--repo", default=str(REPO), help="assets/mistletoe 目录")
    a = ap.parse_args()
    print("=== 生成初雪 13 组进游戏版 ===")
    obj, png = build()
    install(obj, png, a.install, Path(a.repo))


if __name__ == "__main__":
    main()
