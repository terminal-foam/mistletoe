# 槲寄生「初雪」美术资源 v1.0

来源包：`terminalblade_first_snow_art_v1.0.zip`（2026-11-09）。

## 当前状态

- 这是美术交付包，**尚未接入游戏运行资源**。
- 当前 1.21.1 主工程仍使用 `dawn.obj` + 旧版 `first_snow.png`。
- 候选生产文件是 `assets/first_snow.obj` 和 `assets/first_snow.png`。
- `source/first_snow.bbmodel` 可用 Blockbench 打开。
- `preview/` 是视觉预览，不是可直接加载的游戏资源。

## 静态检查

在把 `assets/` 临时放到 `tools/first_snow/` 后运行：

- `check_first_snow.py`：PASS
  - 13 个组、组名/UV/索引正常
  - 与 `dawn.obj` 的几何血缘通过
  - 16 格图集覆盖、冷色配色禁令通过
- `check_ingame.py assets/first_snow.obj`：PASS（FAIL 0）
  - 纳刀态不穿模
  - 发光层/充能层关系正常
  - `item_*` 图标坐标与面朝向正常

## 已知缺口

- `tools/check_first_snow.py` 期望资源位于 `tools/first_snow/`，但包内实际在 `assets/`；按原样不能直接运行。
- `tools/build_first_snow.py` 期望 `tools/first_snow/demo_model.obj`、`demo_atlas.png`，包内没有。
- `tools/derive_first_snow.py` 期望 `tools/make_demo.py`，包内没有。
- `preview/demo_icon.png` 是预览图；没有独立的 `initial_snow_icon.png` 生产图标。
- 没有 `frost_slash.png`；当前霜刃使用 SlashBlade 斩击实体 + `0xDDEFFF` 染色。
- 16×16 缩略图辨识度一般，`item_*` 几何可能需要再简化。

## 接入前要确认

1. 以 `assets/first_snow.obj` 作为模型权威版本，还是以 `source/demo_model.obj` 为准。
2. 是否接受当前枝叶/白果风格的刀镡，还是改成更简洁的六角雪花。
3. 是否需要独立图标 PNG 和自定义霜刃刀光贴图。
