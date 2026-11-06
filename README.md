# 槲寄生

Minecraft 1.21.1 / NeoForge 附属模组。它只加一把原创拔刀剑「槲寄生」，依赖 [SlashBlade: Resharped](https://modrinth.com/mod/slashblade-resharped) 2.1.9，不修改、不重新打包本体。

主题来自巴德尔神话里的槲寄生：平时克制，面对越庞大的生命，切口越深。Super SA 以自己的大部分生命换一次球形即死。

## 环境

- Minecraft 1.21.1
- NeoForge 21.1.228 到 21.2 之前。实际启动测试过的是 21.1.228
- Java 21
- SlashBlade: Resharped 只接受 `2.1.9-1.21.1`

把本模组 JAR 和 Resharped 放进同一个 `mods` 文件夹。缺少 Resharped 时加载器会直接拒绝启动。

## 怎么拿到刀

- 创造模式：拔刀剑物品栏里的「槲寄生」
- 命令：`/mistletoe give`
- 合成：白鞘拔刀剑居中，上下是发光浆果，左右是铁锭，四角是橡树树叶

## 怎么打

生命值都按 Minecraft 内部 HP 计算，2 HP 等于 1 颗红心。

| 操作 | 效果 |
| --- | --- |
| 左键连段 | `D0 + k × max(0, 目标最大生命 − 玩家当前生命)`。不献祭。默认 D0 = 8，k = 0.2 |
| 右键蓄力后松开 | 精准处决。进入动作时不掉血，第 6 个游戏 tick 的命中帧才结算。目标最大生命不超过 100 时不献祭；超过 100 时按献祭后的当前生命计算差额，再附加目标最大生命的 20%，并扣除当前生命的 10% |
| 长按 V 约 1 秒 | 以身体中心做球形即死，默认半径 8。范围攻击结算完，再献祭最大生命的 90%。当前生命不够支付时，攻击完成后再死亡 |

Just 窗口沿用本体，默认没有额外倍率。长按 V 时不要同时潜行并移动，这是本体的输入条件。

公式可以用命令复核，三个数分别是目标最大生命、玩家当前生命、玩家最大生命：

```
/mistletoe audit 300 20 20
```

服务端配置在 `config/terminalblade-common.toml`。把 `debug.log_damage` 设为 `true` 后，每次结算会写进日志。

## 本地构建

```bash
./gradlew downloadSlashBlade build test
./gradlew runGameTestServer
```

`test` 只跑 JUnit。`runGameTestServer` 才跑游戏内测试。两套都要通过，不能互相代替。

`downloadSlashBlade` 会把 Resharped 2.1.9 下载到 `libs/`，并核对 SHA-512。这个 JAR 只用于编译和开发运行，不会打进附属模组。

开发客户端：

```bash
./gradlew runClient
```

## 这版包含什么

0.1.1 修复技能结算时机和物品栏模型分组。普通 SA 与 Super SA 在进入动作时只做起手，伤害和献祭发生在第 6 tick。正式的 Blender 雕刀、VMD 动作和 SE 仍留在后续。

D-01 到 D-08 还没有作者结论，相关行为保持 0.1.0，见 `docs/0.1.1变更摘要.md`。这版做过服务端游戏测试，没有做过客户端画面验收，所以还不是发布候选。
