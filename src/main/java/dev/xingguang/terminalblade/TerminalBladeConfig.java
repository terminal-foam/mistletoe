package dev.xingguang.terminalblade;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 设计文档里尚未由作者最终确认的数值都集中在这里。
 * 默认值采用文档给出的占位和建议，不把它们写死在各个监听器里。
 */
public final class TerminalBladeConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue BASE_DAMAGE;
    public static final ModConfigSpec.DoubleValue GAP_COEFFICIENT;
    public static final ModConfigSpec.BooleanValue CLAMP_NEGATIVE_GAP;
    public static final ModConfigSpec.DoubleValue SLASH_ART_THRESHOLD;
    public static final ModConfigSpec.DoubleValue SLASH_ART_SACRIFICE_RATIO;
    public static final ModConfigSpec.DoubleValue SLASH_ART_MAX_HEALTH_BONUS;
    public static final ModConfigSpec.DoubleValue SLASH_ART_RANGE;
    public static final ModConfigSpec.BooleanValue SLASH_ART_PAY_ON_WHIFF;
    public static final ModConfigSpec.DoubleValue JUST_SLASH_ART_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue SUPER_RADIUS;
    public static final ModConfigSpec.DoubleValue SUPER_SACRIFICE_RATIO;
    public static final ModConfigSpec.BooleanValue SUPER_REQUIRES_GROUND;
    public static final ModConfigSpec.BooleanValue SUPER_CONSUME_HALF_DURABILITY;
    public static final ModConfigSpec.BooleanValue SUPER_AFFECT_PLAYERS;
    public static final ModConfigSpec.BooleanValue SUPER_AFFECT_TAMED;
    public static final ModConfigSpec.BooleanValue SUPER_AFFECT_PASSIVE;
    public static final ModConfigSpec.BooleanValue SUPER_PERSISTENT_EXECUTION;
    public static final ModConfigSpec.IntValue SUPER_PERSISTENT_TICKS;
    public static final ModConfigSpec.IntValue SUPER_PERSISTENT_INTERVAL;
    public static final ModConfigSpec.BooleanValue LOG_DAMAGE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("normal_attack");
        BASE_DAMAGE = builder
                .comment("普通攻击基础伤害 D0。默认 5，与暮色 attack_base 对齐；朝露不走这个公式。")
                .defineInRange("base_damage", 5.0D, 0.0D, 100000.0D);
        GAP_COEFFICIENT = builder
                .comment("生命差额系数 k。伤害 = D0 + k × max(0, 目标最大生命 − 玩家当前生命)。")
                .defineInRange("gap_coefficient", 0.2D, -1000.0D, 1000.0D);
        CLAMP_NEGATIVE_GAP = builder
                .comment("目标最大生命低于玩家当前生命时，是否把差额截断为 0。设计建议为 true，待作者确认。")
                .define("clamp_negative_gap", true);
        builder.pop();

        builder.push("slash_art");
        SLASH_ART_THRESHOLD = builder
                .comment("普通 SA 强化阈值，单位是 Minecraft HP。小于或等于该值不献祭、不附加最大生命伤害。")
                .defineInRange("health_threshold", 100.0D, 0.0D, 1000000.0D);
        SLASH_ART_SACRIFICE_RATIO = builder
                .comment("强化 SA 献祭当前生命的比例。")
                .defineInRange("sacrifice_ratio", 0.10D, 0.0D, 1.0D);
        SLASH_ART_MAX_HEALTH_BONUS = builder
                .comment("强化 SA 附加伤害 = 目标最大生命 × 该比例。")
                .defineInRange("max_health_bonus", 0.20D, 0.0D, 100.0D);
        SLASH_ART_RANGE = builder
                .comment("精准处决的锁定距离。优先使用拔刀剑锁定目标，否则取面前最近的生物。")
                .defineInRange("range", 6.0D, 1.0D, 64.0D);
        SLASH_ART_PAY_ON_WHIFF = builder
                .comment("BLOCKED D-04：名字容易理解成空挥也献祭。0.1.1 保持 0.1.0 的行为：完全没有目标时不扣血；只有选中目标且 hurt 失败时，true 才会先扣 10%。默认 false。作者确认前不改名、不改默认值。")
                .define("pay_on_whiff", false);
        JUST_SLASH_ART_MULTIPLIER = builder
                .comment("Just SA 伤害倍率。默认 1，不额外加奖，只保留本体的 Just 判定窗口。")
                .defineInRange("just_multiplier", 1.0D, 0.0D, 100.0D);
        builder.pop();

        builder.push("super_slash_art");
        SUPER_RADIUS = builder
                .comment("以玩家身体中心为球心的半径。判定使用球形距离，不是立方体。")
                .defineInRange("radius", 16.0D, 0.5D, 128.0D);
        SUPER_SACRIFICE_RATIO = builder
                .comment("献祭最大生命的比例。当前生命不足以支付时，范围攻击完成后玩家死亡。")
                .defineInRange("sacrifice_ratio", 0.90D, 0.0D, 1.0D);
        SUPER_REQUIRES_GROUND = builder
                .comment("是否沿用本体 Super SA 的落地限制。默认关闭，避免技能被站地条件拦住。")
                .define("requires_ground", false);
        SUPER_CONSUME_HALF_DURABILITY = builder
                .comment("是否沿用本体 Super SA 扣除一半耐久。默认关闭，待作者确认。")
                .define("consume_half_durability", false);
        SUPER_AFFECT_PLAYERS = builder
                .comment("是否影响其他玩家。默认排除所有玩家，创造模式玩家始终排除。")
                .define("affect_players", false);
        SUPER_AFFECT_TAMED = builder
                .comment("是否影响玩家驯服的生物。")
                .define("affect_tamed", false);
        SUPER_AFFECT_PASSIVE = builder
                .comment("是否影响非敌对生物。默认只处理实现了 Enemy 的敌对生物。")
                .define("affect_passive", false);
        SUPER_PERSISTENT_EXECUTION = builder
                .comment("Super SA 首次绝对伤害没有杀死目标时，是否开启有时限的持续处决。用于有限伤、阶段窗口或自定义生命值 Boss。")
                .define("persistent_execution", true);
        SUPER_PERSISTENT_TICKS = builder
                .comment("持续处决最长持续时间（tick）。0 表示不限制，但不建议。")
                .defineInRange("persistent_ticks", 200, 0, 1200);
        SUPER_PERSISTENT_INTERVAL = builder
                .comment("持续处决两次绝对伤害之间的间隔（tick）。")
                .defineInRange("persistent_interval", 5, 1, 40);
        builder.pop();

        builder.push("debug");
        LOG_DAMAGE = builder
                .comment("在服务端日志打印槲寄生的公式结算，便于对照测试用例。")
                .define("log_damage", false);
        builder.pop();

        SPEC = builder.build();
    }

    private TerminalBladeConfig() {
    }

    public static float baseDamage() {
        return BASE_DAMAGE.get().floatValue();
    }

    public static float gapCoefficient() {
        return GAP_COEFFICIENT.get().floatValue();
    }

    public static boolean clampNegativeGap() {
        return CLAMP_NEGATIVE_GAP.get();
    }

    public static float slashArtThreshold() {
        return SLASH_ART_THRESHOLD.get().floatValue();
    }

    public static float slashArtSacrificeRatio() {
        return SLASH_ART_SACRIFICE_RATIO.get().floatValue();
    }

    public static float slashArtMaxHealthBonus() {
        return SLASH_ART_MAX_HEALTH_BONUS.get().floatValue();
    }

    public static double slashArtRange() {
        return SLASH_ART_RANGE.get();
    }

    public static boolean slashArtPayOnWhiff() {
        return SLASH_ART_PAY_ON_WHIFF.get();
    }

    public static float justMultiplier() {
        return JUST_SLASH_ART_MULTIPLIER.get().floatValue();
    }

    public static double superRadius() {
        return SUPER_RADIUS.get();
    }

    public static float superSacrificeRatio() {
        return SUPER_SACRIFICE_RATIO.get().floatValue();
    }

    public static boolean superRequiresGround() {
        return SUPER_REQUIRES_GROUND.get();
    }

    public static boolean superConsumeHalfDurability() {
        return SUPER_CONSUME_HALF_DURABILITY.get();
    }

    public static boolean superAffectPlayers() {
        return SUPER_AFFECT_PLAYERS.get();
    }

    public static boolean superAffectTamed() {
        return SUPER_AFFECT_TAMED.get();
    }

    public static boolean superAffectPassive() {
        return SUPER_AFFECT_PASSIVE.get();
    }

    public static boolean superPersistentExecution() {
        return SUPER_PERSISTENT_EXECUTION.get();
    }

    public static int superPersistentTicks() {
        return SUPER_PERSISTENT_TICKS.get();
    }

    public static int superPersistentInterval() {
        return SUPER_PERSISTENT_INTERVAL.get();
    }

    public static boolean logDamage() {
        return LOG_DAMAGE.get();
    }
}
