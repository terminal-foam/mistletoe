# 拔刀剑扩展：槲寄生

**SlashBlade Expansion: Mistletoe**

Minecraft 1.21.1 / NeoForge 附属模组。它加三把原创拔刀剑「槲寄生『暮色』」「槲寄生『朝露』」和「槲寄生『初雪』」，依赖 [SlashBlade: Resharped](https://modrinth.com/mod/slashblade-resharped) 2.0.7–2.1.9（主目标 2.1.9），不修改、不重新打包本体。

主题来自巴德尔神话里的槲寄生：平时克制，面对越庞大的生命，切口越深。Super SA 以自己的大部分生命换一次球形即死。

## 环境

- Minecraft 1.21.1
- NeoForge 21.1.228 到 21.2 之前。0.1.2 在 21.1.264 专用服务器上做过启动加载测试
- Java 21
- SlashBlade: Resharped 接受 `2.0.7-1.21.1` 到 `2.1.9-1.21.1`；开发和 GameTest 基准是 2.1.9

把本模组 JAR 和 Resharped 放进同一个 `mods` 文件夹。缺少 Resharped 时加载器会直接拒绝启动。

## 怎么拿到刀

- 创造模式：拔刀剑物品栏里的「槲寄生『暮色』」「槲寄生『朝露』」和「槲寄生『初雪』」
- 命令：`/mistletoe give`（暮色）、`/mistletoe dawn`（朝露）、`/mistletoe first_snow`（初雪）
- 合成：白鞘拔刀剑居中，上下是发光浆果，左右是铁锭，四角是橡树树叶

## 怎么打

生命值都按 Minecraft 内部 HP 计算，2 HP 等于 1 颗红心。

| 操作 | 效果 |
| --- | --- |
| 左键连段 | `D0 + k × max(0, 目标最大生命 − 玩家当前生命)`。不献祭。默认 D0 = 5，k = 0.2 |
| 右键蓄力后松开 | 精准处决。进入动作时不掉血，第 6 个游戏 tick 的命中帧才结算。目标最大生命不超过 100 时不献祭；超过 100 时按献祭后的当前生命计算差额，再附加目标最大生命的 20%，并扣除当前生命的 10% |
| 长按 V 约 1 秒 | 以身体中心做球形即死，默认半径 16。未锁血目标用无敌穿透伤害结算；范围攻击结算完，再献祭最大生命的 90%。当前生命不够支付时，攻击完成后再死亡。没有目标时仍然献祭 |

Just 窗口沿用本体，默认没有额外倍率。长按 V 时不要同时潜行并移动，这是本体的输入条件。

Super SA 会先查询 Boss 阶段锁血：锁血目标既不承受无敌穿透伤害，也不会被强制处决。已知 Boss 模组可以注册 `PhaseLockAdapter`；数据包标签 `mistletoe:super_sa_phase_protected` 是未适配 Boss 的保守兜底（默认空）。未适配目标按普通实体处理，可被处决；本模组不声称自动识别任意第三方 Boss 的阶段锁血。

公式可以用命令复核，三个数分别是目标最大生命、玩家当前生命、玩家最大生命：

```
/mistletoe audit 300 20 20
```

服务端配置在 `config/mistletoe-common.toml`。把 `debug.log_damage` 设为 `true` 后，每次结算会写进日志。

## 本地构建

```bash
./gradlew downloadSlashBlade build test
./gradlew runGameTestServer
```

`test` 只跑 JUnit。`runGameTestServer` 才跑游戏内测试。两套都要通过，不能互相代替。

`downloadSlashBlade` 会把 Resharped 2.1.9 下载到 `libs/`（编译基准），并核对 SHA-512。这个 JAR 只用于编译和开发运行，不会打进附属模组。

本机没有 Gradle 缓存时，可以用离线脚本：

```powershell
.\build-1.21.1.ps1 -Clean -Verify   # javac + jar，产物在 build-1.21.1\libs
.\run-tests.ps1                     # JUnit（需要 libs\junit-platform-console-standalone-1.11.4.jar）
```

1.20.1 Forge 版是独立工程 `port-1.20.1/`（Forge 47.2.0 / Java 17 / SlashBlade 1.1.23），构建与验证结果见 [docs/1.20.1-forge移植验证.md](docs/1.20.1-forge移植验证.md)。

开发客户端：

```bash
./gradlew runClient
```

## 这版包含什么

0.1.2 是战斗系统稳定化版本：

- Super SA 改为 `SuperSaExecutionService`：先查阶段锁血，再打无敌穿透伤害；伤害后没死且未锁血才走受控强制处决；死亡事件只在当前强制目标的作用域内解除取消。
- 删除 `super_slash_art.bypass_invulnerability` / `finish_unyielding` 两个旧开关；默认按上面的规则执行。
- Super SA 的 ComboState 转换与冷却检查移到扣耐久之前，避免“技能没放出来但耐久已经扣了”。
- OBJ 补了本体充能渲染需要的 `effect` 组。
- 斩击来源栈增加服务端 tick 兜底清空，避免异常 tick 污染下一次伤害判定。
- 新增阶段锁血接口、静态保护标签、处决 GameTest 和耐久顺序测试。
- 新增槲寄生「朝露」与「初雪」：朝露走固定 12 点熹微 + 再生；初雪是 attack_base 12 的速攻刀，霜刃 7 段高速斩击，攻速 +4.0；v1.2rc-r3 雪花刀镡、收敛刀鞘和立体流苏已接入。

旧 D-02/D-03 已由 0.1.2 的 Super SA 规则取代；其余未确认项和新增 D-09（耐久附魔）见 `docs/技术决策.md`。0.1.2 已通过 JUnit 12/12、GameTest 26/26 和 NeoForge 21.1.264 专服加载；客户端画面/真人按键验收仍未执行，所以暂不打 `v0.1.2` release tag。
