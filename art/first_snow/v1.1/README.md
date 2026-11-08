# 槲寄生「初雪」美术资源 v1.1

来源包：`terminalblade_first_snow_art_v1.1.zip`（2026-11-09）。

## 状态

- 这是**自包含可复现**的美术流水线归档。
- 尚未接入游戏运行资源；当前 `first_snow.json` 仍指向 `dawn.obj` + 旧贴图。
- 生产候选：`assets/first_snow.obj`、`assets/first_snow.png`。
- `source/first_snow.bbmodel` 可用 Blockbench 打开。
- `wiring/frost_slash.png` 和 `wiring/FrostSlashBinding.java` 是刀光绑定草案，**NOT WIRED**，不能直接上线。

## 复现命令

在解压目录运行：

```bash
python build_all.py --verify
```

## 验证结果

- 13 个组、3966 三角面、11898 顶点
- UV/索引越界：0
- 纳刀态不穿模：PASS
- `check_ingame.py`：FAIL 0
- `check.py`：PASS
  - 刀身/鞘/发光层/effect 禁止区改动：0
  - 镡盘最大半径：15.0 → 9.6
  - 镡外悬空装饰三角形：0
  - 暖金/黄：0%，饱和绿：0%
- 二次重生成：16 个文件差异 0

## 仍未完成

- 霜刃刀光未接入；`frost_slash.png` 是 32×32 预置贴图，`FrostSlashBinding.java` 未编译。
- 独立物品栏图标 PNG 未交付；当前由 OBJ 的 `item_*` 组渲染。
- v1.1 基本删除了 3D 枝叶/白果装饰，刀鞘只剩贴图纹样；需确认是否符合“少量霜化槲寄生”的设计意图。
- `assets/` 尚未替换当前游戏资源。
