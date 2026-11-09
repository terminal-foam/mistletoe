# 槲寄生「初雪」雪花刀镡 v1.2rc-r3（修复版）

来源：`terminalblade_first_snow_snowflake_tsuba_v1.2rc.zip`（v1.2rc-r3）。

## 本仓库内做的修复

- 将冰珠 UV 球分段从 `10×6` 提高到 `16×10`：
  - 三角面 4486 → **4862**
  - `check_tassel.py` 仍 PASS：冰珠闭合、带子双面、不碰刀身
- 修复损坏的 `preview/tsuba_compare.png`（原先为截断 PNG）
- 重新生成并同步 `assets/`、`preview/`、`verification/`、`manifest.json`

## 验证

在 `tools/` 下运行：

```bash
python build_all.py --verify
```

结果：

- 13 组 / 4862 三角面 / 14586 顶点
- UV/索引越界 0
- `check_ingame.py`：FAIL 0
- `check_tassel.py`：PASS
- `check.py`：PASS
- 22 个生成文件二次重生成差异 0
- 刀身/鞘/发光层/effect 禁止区改动 0
- 镡半径 9.60，镡外悬空装饰 0
- 暖金/黄 0%，饱和绿 0%

## 仍未完成

- `wiring/frost_slash.png` / `FrostSlashBinding.java` 仍是 **NOT WIRED**；当前游戏霜刃仍用 SlashBlade 自带刀光 + `0xDDEFFF` 染色。
- 没有独立物品栏 PNG；物品栏由 OBJ 的 `item_*` 组渲染。
- 流苏是静态几何，不会随动作或风摆动。
- 尚未接入 1.21.1 主工程运行资源。
