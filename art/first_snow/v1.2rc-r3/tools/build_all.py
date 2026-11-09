#!/usr/bin/env python3
"""初雪美术资源流水线 —— 单入口，从任意当前目录可重复执行。

    python build_all.py                    # 重生成 out/ 全部交付物 + 校验
    python build_all.py --package          # 额外打包 zip 到上一级目录
    python build_all.py --verify           # 连跑两遍比对字节，证明可复现

自包含约定：
  * 所有路径相对本文件（HERE），不写死 E:/dsh 或任何机器路径；
  * 不依赖 make_demo.py / derive_first_snow.py / render_jar013.py；
  * 基准 dawn.obj 随包放在 ref/，几何血缘比对降级为可选；
  * 默认 dry-run，不写游戏仓库；只有 build.py --install --repo 才写。

产物目录 out/：
  first_snow.obj / first_snow.png  进游戏的 13 组模型与 1024² 图集
  demo_model.obj / demo_atlas.png  可编辑源
  first_snow.bbmodel                Blockbench 5.2.2 工程
  demo_model_blockbench.obj / materials.mtl  导入用 OBJ+MTL
  demo_*.png / icon16_preview.png  离线渲染与 16x16 验收图
  demo_tassel_*.png                立体流苏特写与鞘上参照
  atlas_usage.json                 逐格 UV 采样与绑定状态
  tassel_check.json                挂件穿模 / 闭合 / 双面专项结论
"""
from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
import sys
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE / "out"
OUT.mkdir(parents=True, exist_ok=True)
REF = HERE / "ref"

STEPS = [
    ("生成模型/图集/预览", "make_first_snow.py", []),
    ("归并为 13 组进游戏版", "build.py", []),
    ("生成 Blockbench 工程", "obj_to_bbmodel.py", []),
    ("生成刀光预置贴图（NOT WIRED）", "make_wiring_texture.py", []),
    ("游戏内静态检查", "check_ingame.py", ["out/first_snow.obj"]),
    ("立体挂件穿模专项", "check_tassel.py", []),
    ("初雪专属验收", "check.py", []),
]

GAME_FILES = ["first_snow.obj", "first_snow.png"]
SOURCE_FILES = ["demo_model.obj", "demo_atlas.png", "first_snow.bbmodel",
                "demo_model_blockbench.obj", "materials.mtl"]
PREVIEW_FILES = ["demo_render.png", "demo_render_sheathed.png", "demo_overlay.png",
                 "demo_icon.png", "demo_icon_single.png", "demo_emblem.png",
                 "demo_saya_compare.png", "demo_particles.png", "icon16_preview.png",
                 "demo_tsuba_closeup.png", "demo_tassel_closeup.png",
                 "demo_tassel_context.png"]
EXTRA_FILES = ["manifest.json", "atlas_usage.json", "tassel_check.json"]
TOOL_FILES = ["build_all.py", "make_first_snow.py", "build.py", "obj_to_bbmodel.py",
              "make_wiring_texture.py", "check_ingame.py", "check.py",
              "check_tassel.py", "png_util.py"]
REF_FILES = ["ref/dawn.obj"]
WIRING_FILES = ["wiring/FrostSlashBinding.java", "wiring/frost_slash.png"]


def run(label, script, args):
    print("\n" + "-" * 78)
    print(">>>", label, ":", script, " ".join(args))
    print("-" * 78)
    r = subprocess.run([sys.executable, str(HERE / script), *args],
                       cwd=HERE, capture_output=False)
    if r.returncode != 0:
        raise SystemExit("步骤失败：%s (exit %d)" % (script, r.returncode))


def md5(p: Path):
    return hashlib.md5(p.read_bytes()).hexdigest()[:12]


def write_manifest():
    usage = json.loads((OUT / "atlas_usage.json").read_text(encoding="utf-8"))
    doc = {
        "artifact": "mistletoe:first_snow",
        "version": "v1.2rc-r3 (pipeline)",
        "variant": "六瓣雪花镡 + 初雪专属刀鞘纹样 + 立体下垂流苏/冰珠（CANDIDATE）",
        "generated_by": "cd first_snow_pipeline && python build_all.py",
        "python_stdlib_only": True,
        "repo_written": False,
        "layout": "解压即根目录：脚本在顶层，ref/ wiring/ out/ 按相对位置随包；"
                  "assets/ source/ preview/ 是方便取用的分类副本",
        "reproduce": "cd <解压目录> && python build_all.py --verify",
        "files": {
            "pipeline_root": TOOL_FILES + ["manifest.json"],
            "assets": GAME_FILES,
            "source": SOURCE_FILES,
            "preview": PREVIEW_FILES,
            "ref": REF_FILES,
            "wiring_drafts": WIRING_FILES,
        },
        "hashes": {f: md5(OUT / f) for f in GAME_FILES + SOURCE_FILES},
        "baseline": {"path": "ref/dawn.obj", "md5": md5(REF / "dawn.obj"),
                     "purpose": "几何血缘比对；缺失时 check.py 降级为 WARN"},
        "atlas_tiles": usage,
        "atlas_tile_summary": {
            "total": 16,
            "sampled_by_obj": sum(1 for v in usage.values() if v["obj_samples"] > 0),
            "active": [k for k, v in usage.items() if v["binding"] == "ACTIVE"],
            "conditional": [k for k, v in usage.items() if v["binding"] == "CONDITIONAL"],
            "design_only": [k for k, v in usage.items() if v["binding"] == "DESIGN_ONLY"],
            "not_wired": [k for k, v in usage.items() if v["binding"] == "NOT_WIRED"],
        },
        "runtime_binding": {
            "status": "PARTIAL",
            "wired_now": [
                "OBJ 的 13 个固定组由 LayerMainBlade / LayerSlashBlade / SlashBladeTEISR 按组名渲染",
                "first_snow.png 由 first_snow.json 的 texture 字段加载",
            ],
            "not_wired": [
                {
                    "item": "slash_ring / slash_sphere（霜刃刀光）",
                    "finding": "MistletoeCombat.spawnSlash -> AttackManager.doSlash -> EntityDrive；"
                               "DriveRenderer.render 第 66 行把贴图写死为 "
                               "slashblade:model/util/ss.png (32x32)，"
                               "getTextureLocation(entity) 在该 render 里未被调用，"
                               "所以改实体的 textureLoc 不生效。",
                    "only_hook": "RenderOverrideEvent.setTexture()，由 "
                                 "BladeRenderState.renderOverrided 在取 VertexConsumer 之前 post；"
                                 "可用 getOriginalTexture()/getOriginalTarget() 判定 drive 的 'base'。",
                    "extra_constraint": "drive.obj 的 UV 仅 u 0.069..0.675 / v 0.304..0.604，"
                                        "所以霜刃刀光必须是独立的 32x32 贴图且图形落在该窗口，"
                                        "不能直接用 1024 图集里的 256x256 分区。",
                    "draft": "wiring/FrostSlashBinding.java（未编译、未接入）",
                    "draft_texture": "wiring/frost_slash.png（32x32 预置素材，未绑定）",
                },
                {
                    "item": "p_leaf / p_berry（粒子）",
                    "finding": "粒子贴图由各自的 particle JSON + ParticleProvider 独立加载，"
                               "不读主模型图集；仓库现有 berry/petal/spark 三张独立粒子贴图。",
                    "action": "如需霜化粒子，另建 particle JSON 与纹理注册；主图集这两格仅为设计稿。",
                },
                {
                    "item": "effect 分区（充能壳）",
                    "finding": "effect 组几何存在，但本体 renderChargeEffect 由 "
                               "isCharged() = getTicksUsingItem()>9 (右键蓄力) 触发；"
                               "Super SA 是按键保持，不产生 use tick，因此该层在 Super SA 期间不亮。",
                    "action": "若要 Super SA 期间出现，需另挂客户端渲染事件（见 growth addon 方案）。",
                },
                {
                    "item": "first_snow.json 模型指向",
                    "finding": "仓库当前 first_snow.json 的 render.model 仍指 dawn.obj。",
                    "action": "按清单第 6 节改为 mistletoe:model/named/first_snow.obj，"
                              "并把 first_snow.obj / first_snow.png 放进 assets/mistletoe/model/named/。",
                },
            ],
        },
        "verification": {
            "check_ingame": "PASS / FAIL 0",
            "check_py": "PASS / 13 组 / 血缘 / 造型收敛 / 16 格 / 配色 / 16x16 量化",
            "check_tassel": "PASS / 328 面 / 全在鞘椭圆外 / 不碰刀身 / 冰珠闭合 / 带子双面",
            "needs_runClient": [
                "16x16 真实 GUI 缩放下的辨识度",
                "GUI 本体耐久菱形层与冷白图标的配色协调",
                "霜纹在浅漆面上的可见度",
            ],
        },
    }
    path = OUT / "manifest.json"
    path.write_text(json.dumps(doc, ensure_ascii=False, indent=2), encoding="utf-8")
    print("\n  已写 out/manifest.json（%d B）" % path.stat().st_size)
    s = doc["atlas_tile_summary"]
    print("  图集 16 格：%d 格有 OBJ 采样 / %d 未绑定 / %d 仅设计稿 / %d 条件触发" %
          (s["sampled_by_obj"], len(s["not_wired"]), len(s["design_only"]),
           len(s["conditional"])))
    return doc


def package(zip_out: str | None = None):
    """打包成「解压即根目录」的布局。

    关键：脚本放在 zip 顶层（不是 tools/ 子目录），同时把 ref/、wiring/、
    out/ 按脚本期望的相对位置一起放进去。这样解压后
        cd terminalblade_first_snow_art_v1.1 && python build_all.py
    就能从干净目录重生成，不需要拼路径、不需要外部仓库。
    assets/ source/ preview/ 只是方便取用的分类副本。
    """
    name = "first_snow_pipeline_v1.2rc_r3"
    dest = Path(zip_out) if zip_out else HERE.parent.parent / (name + ".zip")
    groups = {
        # 顶层 = 可运行流水线本体
        "": [(HERE / f, f) for f in TOOL_FILES]
            + [(OUT / "manifest.json", "manifest.json")],
        "ref": [(HERE / "ref" / "dawn.obj", "dawn.obj")],
        "wiring": [(HERE / "wiring" / f, f)
                   for f in ("FrostSlashBinding.java", "frost_slash.png")],
        # out/ 是生成器的输出目录，必须原样带齐，脚本才能就地重跑
        "out": [(OUT / f, f) for f in
                sorted(p.name for p in OUT.iterdir() if p.is_file())],
        # 分类副本，便于直接取用
        "assets": [(OUT / f, f) for f in GAME_FILES],
        "source": [(OUT / f, f) for f in SOURCE_FILES],
        "preview": [(OUT / f, f) for f in PREVIEW_FILES],
    }
    missing = [str(p) for items in groups.values() for p, _ in items if not p.is_file()]
    if missing:
        raise SystemExit("打包缺文件: %s" % missing)
    with zipfile.ZipFile(dest, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for folder, items in groups.items():
            for p, fname in items:
                arc = "%s/%s%s" % (name, (folder + "/") if folder else "", fname)
                z.write(p, arc)
    z = zipfile.ZipFile(dest)
    print("\n  zip: %s  %d B / %d 条目 / CRC %s" %
          (dest, dest.stat().st_size, len(z.infolist()), z.testzip()))
    return dest


def snapshot():
    return {f: md5(OUT / f) for f in
            GAME_FILES + SOURCE_FILES + PREVIEW_FILES + EXTRA_FILES
            if (OUT / f).is_file()}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--package", action="store_true", help="打包 zip")
    ap.add_argument("--zip-out", default=None, help="指定 zip 输出路径")
    ap.add_argument("--verify", action="store_true", help="连跑两遍验证字节级可复现")
    a = ap.parse_args()

    print("=" * 78)
    print("初雪美术资源流水线 v1.2rc-r3   cwd =", HERE)
    print("=" * 78)
    for label, script, args in STEPS:
        run(label, script, args)
    write_manifest()

    if a.verify:
        before = snapshot()
        print("\n>>> 第二遍重生成（验证可复现）")
        for label, script, args in STEPS:
            run(label, script, args)
        write_manifest()
        after = snapshot()
        diff = [k for k in before if before.get(k) != after.get(k)]
        print("\n  复现比对：%d 个文件，差异 %d 个 %s" %
              (len(before), len(diff), diff or "（完全一致）"))
        if diff:
            raise SystemExit("不可复现：%s" % diff)

    if a.package:
        package(a.zip_out)
    print("\n完成。out/ 下为完整交付物；仓库未被写入。")


if __name__ == "__main__":
    main()
