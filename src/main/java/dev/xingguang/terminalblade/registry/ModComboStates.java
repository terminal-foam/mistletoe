package dev.xingguang.terminalblade.registry;

import dev.xingguang.terminalblade.TerminalBlade;
import dev.xingguang.terminalblade.combat.MistletoeCombat;
import mods.flammpfeil.slashblade.init.DefaultResources;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Consumer;
import java.util.function.Function;

public final class ModComboStates {
    public static final DeferredRegister<ComboState> COMBO_STATES =
            DeferredRegister.create(ComboState.REGISTRY_KEY, TerminalBlade.MOD_ID);

    public static final DeferredHolder<ComboState, ComboState> SLASH_ART = COMBO_STATES.register("godslayer_cut",
            () -> art(400, 449, 40, user -> MistletoeCombat.beginSlashArt(user, false), MistletoeCombat::resolveSlashArt));

    public static final DeferredHolder<ComboState, ComboState> SLASH_ART_JUST = COMBO_STATES.register("godslayer_cut_just",
            () -> art(459, 488, 40, user -> MistletoeCombat.beginSlashArt(user, true), MistletoeCombat::resolveSlashArt));

    public static final DeferredHolder<ComboState, ComboState> SUPER = COMBO_STATES.register("godslayer_bloom",
            () -> art(1900, 1960, 20, MistletoeCombat::beginSuper, MistletoeCombat::resolveSuper));

    /**
     * 初雪「霜刃」：同一段原版动作模板加速到 2.4 倍，7 个固定 tick 各斩一刀。
     * 命中 tick 与动作速度一起调过，避免动作变快后命中点漂移。
     */
    public static final DeferredHolder<ComboState, ComboState> FROST_BLADE = COMBO_STATES.register("frost_blade",
            () -> {
                var timeline = ComboState.TimeLineTickAction.getBuilder();
                for (int i = 0; i < MistletoeCombat.FROST_HITS; i++) {
                    int hit = i;
                    timeline.put(
                            MistletoeCombat.FROST_FIRST_TICK + i * MistletoeCombat.FROST_INTERVAL,
                            user -> MistletoeCombat.frostBladeHit(user, hit));
                }
                return ComboState.Builder.newInstance()
                        .startAndEnd(400, 449)
                        .speed(2.4F)
                        .priority(40)
                        .motionLoc(DefaultResources.ExMotionLocation)
                        .next(entity -> ComboStateRegistry.NONE.getId())
                        .nextOfTimeout(entity -> ComboStateRegistry.NONE.getId())
                        .clickAction(MistletoeCombat::beginFrostBlade)
                        .addTickAction(timeline.build())
                        .timeout(400)
                        .build();
            });

    /**
     * 初雪的地面快速连段：固定 6 段 A1→A2→A3→A4→A4_EX→A5→A1。
     *
     * <p>2.0.7 的玩家 VMD 动作（{@code VmdAnimation}）每 tick 固定 +1、没有速度字段，永远 1.0x 播放，
     * 所以这里只压缩「换段窗口」，不改命中 tick 和动作 speed，保证伤害和身体动作对齐。分支窗口按
     * 「本段最后一段命中 tick + 1」取值：A1/A2=2 tick、A3=6、A4=10、A4_EX=15、A5=20。</p>
     */
    public static final DeferredHolder<ComboState, ComboState> FAST_COMBO_A1 = COMBO_STATES.register("combo_a1_fast",
            () -> fastGround(ComboStateRegistry.COMBO_A1, 3, entity -> TerminalBlade.id("combo_a2_fast")));
    public static final DeferredHolder<ComboState, ComboState> FAST_COMBO_A2 = COMBO_STATES.register("combo_a2_fast",
            () -> fastGround(ComboStateRegistry.COMBO_A2, 3, entity -> TerminalBlade.id("combo_a3_fast")));
    public static final DeferredHolder<ComboState, ComboState> FAST_COMBO_A3 = COMBO_STATES.register("combo_a3_fast",
            () -> fastGround(ComboStateRegistry.COMBO_A3, 9, entity -> TerminalBlade.id("combo_a4_fast")));
    public static final DeferredHolder<ComboState, ComboState> FAST_COMBO_A4 = COMBO_STATES.register("combo_a4_fast",
            () -> fastGround(ComboStateRegistry.COMBO_A4, 15, entity -> TerminalBlade.id("combo_a4_ex_fast")));
    /** 第 5 段，对应原版 combo_a4_ex（力量/饥饿状态下的强化突进斩）。 */
    public static final DeferredHolder<ComboState, ComboState> FAST_COMBO_A4_EX = COMBO_STATES.register("combo_a4_ex_fast",
            () -> fastGround(ComboStateRegistry.COMBO_A4_EX, 23, entity -> TerminalBlade.id("combo_a5_fast")));
    public static final DeferredHolder<ComboState, ComboState> FAST_COMBO_A5 = COMBO_STATES.register("combo_a5_fast",
            () -> fastGround(ComboStateRegistry.COMBO_A5, 30, entity -> TerminalBlade.id("combo_a1_fast")));

    /** 动作倍率保持 1.0：玩家 VMD 动画在 2.0.7 无法安全变速，强行加速只会让动作和伤害脱节。 */
    public static final float FAST_GROUND_SPEED = 1.0F;

    private ModComboStates() {
    }

    private static ComboState fastGround(DeferredHolder<ComboState, ComboState> base, int branchFrames,
                                         Function<LivingEntity, ResourceLocation> fastNext) {
        ComboState original = base.get();
        ComboState.TimeoutNext branch = ComboState.TimeoutNext.buildFromFrame(branchFrames, fastNext);
        ComboState.Builder builder = ComboState.Builder.newInstance()
                .startAndEnd(original.getStartFrame(), original.getEndFrame())
                .priority(original.getPriority())
                .speed(original.getSpeed() * FAST_GROUND_SPEED)
                .motionLoc(original.getMotionLoc())
                .next(branch)
                .nextOfTimeout(original::getNextOfTimeout)
                .timeout(0)
                .setHoldAction(original::holdAction)
                // 命中时间轴沿用原版，保证伤害和身体动作对齐；只改换段窗口。
                .setTickAction(original::tickAction)
                .clickAction(original::clickAction)
                .setHitEffect(original::hitEffect)
                .releaseAction(original::releaseAction);
        if (original.getLoop()) {
            builder.loop();
        }
        if (original.isAerial()) {
            builder.aerial();
        }
        return builder.build();
    }

    /**
     * clickAction 在进入 ComboState 的同一刻触发，只做起手。
     * TimeLineTickAction 的键是 getElapsedTime 的游戏 tick，不是 VMD 帧号。
     */
    private static ComboState art(int startFrame, int endFrame, int priority, Consumer<LivingEntity> onEnter, Consumer<LivingEntity> onHit) {
        return ComboState.Builder.newInstance()
                .startAndEnd(startFrame, endFrame)
                .priority(priority)
                .motionLoc(DefaultResources.ExMotionLocation)
                .next(entity -> ComboStateRegistry.NONE.getId())
                .nextOfTimeout(entity -> ComboStateRegistry.NONE.getId())
                .clickAction(onEnter)
                .addTickAction(ComboState.TimeLineTickAction.getBuilder()
                        .put(MistletoeCombat.HIT_TICK, onHit)
                        .build())
                .timeout(400)
                .build();
    }
}
