package dev.xingguang.terminalblade.registry;

import dev.xingguang.terminalblade.TerminalBlade;
import dev.xingguang.terminalblade.combat.MistletoeCombat;
import mods.flammpfeil.slashblade.ability.ArrowReflector;
import mods.flammpfeil.slashblade.init.DefaultResources;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.util.AttackManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Optional;
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
     * 玩家动作播放速度倍率。客户端 {@code FastVmdAnimation} 用同一个值加速 VMD，
     * {@code ComboState.speed} 用同一个值缩短超时；命中 tick 也按这个倍率收紧，
     * 三者必须一致，否则伤害会和身体动作脱节。
     */
    public static final float FAST_GROUND_SPEED = 1.5F;

    /**
     * 初雪的地面快速连段：固定 6 段 A1→A2→A3→A4→A4_EX→A5→A1，整体 1.5x。
     *
     * <p>原版 A3/A4/A4_EX/A5 的命中 tick 是 2/6、8/9、7/14、15/17/19；这里按 1/1.5 收紧为
     * 1/4、5/6、5/9、10/11/13，使伤害和加速后的 VMD 动作保持同步。分支窗口取
     * 「收紧后最后一段命中 tick + 1」。</p>
     */
    public static final DeferredHolder<ComboState, ComboState> FAST_COMBO_A1 = COMBO_STATES.register("combo_a1_fast",
            () -> fastGround(ComboStateRegistry.COMBO_A1, 3, entity -> TerminalBlade.id("combo_a2_fast"),
                    ComboStateRegistry.COMBO_A1.get()::tickAction));
    public static final DeferredHolder<ComboState, ComboState> FAST_COMBO_A2 = COMBO_STATES.register("combo_a2_fast",
            () -> fastGround(ComboStateRegistry.COMBO_A2, 3, entity -> TerminalBlade.id("combo_a3_fast"),
                    ComboStateRegistry.COMBO_A2.get()::tickAction));
    public static final DeferredHolder<ComboState, ComboState> FAST_COMBO_A3 = COMBO_STATES.register("combo_a3_fast",
            () -> fastGround(ComboStateRegistry.COMBO_A3, 8, entity -> TerminalBlade.id("combo_a4_fast"),
                    comboHits(timeline -> timeline
                            .put(1, user -> AttackManager.doSlash(user, -61.0F, false, false, 0.44F))
                            .put(4, user -> AttackManager.doSlash(user, 138.0F, false, false, 0.44F)))));
    public static final DeferredHolder<ComboState, ComboState> FAST_COMBO_A4 = COMBO_STATES.register("combo_a4_fast",
            () -> fastGround(ComboStateRegistry.COMBO_A4, 11, entity -> TerminalBlade.id("combo_a4_ex_fast"),
                    comboHits(timeline -> timeline
                            .put(5, user -> AttackManager.doSlash(user, 45.0F, false, false, 0.44F))
                            .put(6, user -> AttackManager.doSlash(user, 50.0F, true, false, 0.44F)))));
    /** 第 5 段，对应原版 combo_a4_ex（力量/饥饿状态下的强化突进斩）。 */
    public static final DeferredHolder<ComboState, ComboState> FAST_COMBO_A4_EX = COMBO_STATES.register("combo_a4_ex_fast",
            () -> fastGround(ComboStateRegistry.COMBO_A4_EX, 15, entity -> TerminalBlade.id("combo_a5_fast"),
                    comboHits(timeline -> timeline
                            .put(5, user -> AttackManager.doSlash(user, 70.0F, false, false, 1.0))
                            .put(9, user -> AttackManager.doSlash(user, 255.0F, false, false, 1.0)))));
    public static final DeferredHolder<ComboState, ComboState> FAST_COMBO_A5 = COMBO_STATES.register("combo_a5_fast",
            () -> fastGround(ComboStateRegistry.COMBO_A5, 21, entity -> TerminalBlade.id("combo_a1_fast"),
                    comboHits(timeline -> timeline
                            .put(10, user -> Optional.ofNullable(AttackManager.doSlash(user, 35.0F, false, true, 1.0))
                                    .ifPresent(slash -> slash.setBaseSize(2.0F)))
                            .put(11, user -> Optional.ofNullable(AttackManager.doSlash(user, 40.0F, true, true, 1.0))
                                    .ifPresent(slash -> slash.setBaseSize(2.0F)))
                            .put(13, user -> Optional.ofNullable(AttackManager.doSlash(user, 30.0F, true, true, 1.0))
                                    .ifPresent(slash -> slash.setBaseSize(2.0F))))));

    private ModComboStates() {
    }

    private static ComboState fastGround(DeferredHolder<ComboState, ComboState> base, int branchFrames,
                                         Function<LivingEntity, ResourceLocation> fastNext,
                                         Consumer<LivingEntity> tickAction) {
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
                .setTickAction(tickAction)
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
     * 时间轴命中与 ArrowReflector 的组合，等价于原版 Builder 默认 tickAction 再追加 TimeLineTickAction。
     */
    private static Consumer<LivingEntity> comboHits(
            Consumer<ComboState.TimeLineTickAction.TimeLineTickActionBuilder> configure) {
        ComboState.TimeLineTickAction.TimeLineTickActionBuilder timeline = ComboState.TimeLineTickAction.getBuilder();
        configure.accept(timeline);
        return ComboState.EMPTY_TICK_ACTION
                .andThen(ArrowReflector::doTicks)
                .andThen(timeline.build());
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
