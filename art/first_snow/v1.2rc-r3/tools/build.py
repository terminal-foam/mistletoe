#!/usr/bin/env python3
"""把初雪可编辑模型归并成 SlashBlade 能加载的 13 组版本。

与旧 build_first_snow.py 规则一致（同名组 HashMap.put 只留最后一段、
渲染层只请求固定组名、解析器不认 mtllib/usemtl），但路径全部相对本文件：
  * 输入 out/demo_model.obj + out/demo_atlas.png（make_first_snow.py 产出）
  * 输出 out/first_snow.obj + out/first_snow.png
  * 默认 dry-run，绝不写仓库；--install 时 **必须** 显式给 --repo，
    不再硬编码 E:/dsh/...，可用环境变量 MISTLETOE_ASSETS 代替。

用法：
    python build.py
    python build.py --install --repo E:/dsh/terminalblade/src/main/resources/assets/mistletoe
"""
from __future__ import annotations

import argparse
import os
import re
import shutil
from collections import defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE / "out"
OUT.mkdir(parents=True, exist_ok=True)
SRC_OBJ = OUT / "demo_model.obj"
SRC_PNG = OUT / "demo_atlas.png"

# v1.1 生成器已直接产出 13 个合法组，保留映射只为兼容旧版 16 组输入。
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
    for p in (SRC_OBJ, SRC_PNG):
        if not p.is_file():
            raise SystemExit("缺少输入 %s —— 请先运行 python build_all.py 或 python make_first_snow.py" % p)
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

    obj = OUT / "first_snow.obj"
    body = list(geom)
    for g in order:
        body.append("g " + g)
        body.extend(buckets[g])
    obj.write_text("\n".join(body) + "\n", encoding="utf-8")

    png = OUT / "first_snow.png"
    shutil.copy2(SRC_PNG, png)

    print("  输入 %s (%d 行) -> %s" % (SRC_OBJ.name, len(lines), obj.name))
    print("  归并组:", MERGE, "(v1.1 生成器已直接产出 13 组，此步为兼容兜底)")
    print("  剔除 MTL 行:", stripped)
    print("  输出组 (%d): %s" % (len(order), ", ".join(order)))
    print("  重复组:", [g for g in order if order.count(g) > 1] or "无")
    print("  面数: %d   顶点行: %d" %
          (sum(len(v) for v in buckets.values()),
           sum(1 for line in geom if line.startswith("v "))))
    print("  贴图: %s (%d 字节)" % (png.name, png.stat().st_size))
    return obj, png


def install(obj: Path, png: Path, apply: bool, repo: str | None):
    if not apply:
        print("\n  dry-run：未写入仓库。")
        print("  接入需：python build.py --install --repo <assets/mistletoe 目录>")
        print("  并同步把 first_snow.json 的 render.model 改成 mistletoe:model/named/first_snow.obj")
        return
    if not repo or not Path(repo).is_dir():
        raise SystemExit("--install 需要用 --repo 显式指定 assets/mistletoe 目录（或设置 MISTLETOE_ASSETS）")
    root = Path(repo)
    for src, dst in [(obj, root / "model/named/first_snow.obj"),
                     (png, root / "model/named/first_snow.png")]:
        if dst.exists():
            bak = dst.with_suffix(dst.suffix + ".bak")
            shutil.copy2(dst, bak)
            print("    备份 %s -> %s" % (dst.name, bak.name))
        shutil.copy2(src, dst)
        print("    已写入 %s" % dst)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--install", action="store_true", help="实际拷贝进仓库")
    ap.add_argument("--repo", default=os.environ.get("MISTLETOE_ASSETS"),
                    help="assets/mistletoe 目录（--install 时必填）")
    a = ap.parse_args()
    print("=== 生成初雪 13 组进游戏版 ===")
    obj, png = build()
    install(obj, png, a.install, a.repo)


if __name__ == "__main__":
    main()
