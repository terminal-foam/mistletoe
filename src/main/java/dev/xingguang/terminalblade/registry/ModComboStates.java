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
     * 初雪的地面快速连段。
     *
     * <p>原版 combo_aN 的「下一段最早什么时候能接上」由 TimeoutNext.buildFromFrame(N) 决定，
     * 单位是游戏 tick，和 ComboState.speed 无关；只放大 speed 或只修 timeout 都不会改变出刀节奏。
     * 这里把每个 fast 段的分支窗口直接压到 2~5 tick，并且 next 直接指向下一个 fast 段（自闭环），
     * 不再经过原版 3 tick 窗口；timeout 归零，让 getTimeoutMS = 帧数/speed 随 speed 自然缩短。</p>
     *
     * <p>分支窗口按「本段最后一段命中 tick + 1」取值：A1/A2 命中在进入时（0），A3 命中 1/2，
     * A4 命中 2/3，A4_EX 命中 2/3，A5 命中 2/3/4，保证换段前本段的多段伤害已经落地。</p>
     *
     * <p>固定 6 段：A1→A2→A3→A4→A4_EX→A5→A1。原版 A4_EX 是力量/饥饿时替换 A4 的分支；
     * 初雪按需求把 A4 和 A4_EX 都串进链里。</p>
     */
    public static final DeferredHolder<ComboState, ComboState> FAST_COMBO_A1 = COMBO_STATES.register("combo_a1_fast",
            () -> fastGround(ComboStateRegistry.COMBO_A1, 3, entity -> TerminalBlade.id("combo_a2_fast"),
                    ComboStateRegistry.COMBO_A1.get()::tickAction));
    public static final DeferredHolder<ComboState, ComboState> FAST_COMBO_A2 = COMBO_STATES.register("combo_a2_fast",
            () -> fastGround(ComboStateRegistry.COMBO_A2, 3, entity -> TerminalBlade.id("combo_a3_fast"),
                    ComboStateRegistry.COMBO_A2.get()::tickAction));
    public static final DeferredHolder<ComboState, ComboState> FAST_COMBO_A3 = COMBO_STATES.register("combo_a3_fast",
            () -> fastGround(ComboStateRegistry.COMBO_A3, 5, entity -> TerminalBlade.id("combo_a4_fast"),
                    comboHits(timeline -> timeline
                            .put(1, user -> AttackManager.doSlash(user, -61.0F, false, false, 0.44F))
                            .put(2, user -> AttackManager.doSlash(user, 138.0F, false, false, 0.44F)))));
    public static final DeferredHolder<ComboState, ComboState> FAST_COMBO_A4 = COMBO_STATES.register("combo_a4_fast",
            () -> fastGround(ComboStateRegistry.COMBO_A4, 6, entity -> TerminalBlade.id("combo_a4_ex_fast"),
                    comboHits(timeline -> timeline
                            .put(2, user -> AttackManager.doSlash(user, 45.0F, false, false, 0.44F))
                            .put(3, user -> AttackManager.doSlash(user, 50.0F, true, false, 0.44F)))));
    /**
     * 第 5 段，对应原版 combo_a4_ex（力量/饥饿状态下的强化突进斩）。
     * 初雪的快速链把 A4 和 A4_EX 都串进链里：A1→A2→A3→A4→A4_EX→A5，共 6 段。
     */
    public static final DeferredHolder<ComboState, ComboState> FAST_COMBO_A4_EX = COMBO_STATES.register("combo_a4_ex_fast",
            () -> fastGround(ComboStateRegistry.COMBO_A4_EX, 6, entity -> TerminalBlade.id("combo_a5_fast"),
                    comboHits(timeline -> timeline
                            .put(2, user -> AttackManager.doSlash(user, 70.0F, false, false, 1.0))
                            .put(3, user -> AttackManager.doSlash(user, 255.0F, false, false, 1.0)))));
    public static final DeferredHolder<ComboState, ComboState> FAST_COMBO_A5 = COMBO_STATES.register("combo_a5_fast",
            () -> fastGround(ComboStateRegistry.COMBO_A5, 8, entity -> TerminalBlade.id("combo_a1_fast"),
                    comboHits(timeline -> timeline
                            .put(2, user -> Optional.ofNullable(AttackManager.doSlash(user, 35.0F, false, true, 1.0))
                                    .ifPresent(slash -> slash.setBaseSize(2.0F)))
                            .put(3, user -> Optional.ofNullable(AttackManager.doSlash(user, 40.0F, true, true, 1.0))
                                    .ifPresent(slash -> slash.setBaseSize(2.0F)))
                            .put(4, user -> Optional.ofNullable(AttackManager.doSlash(user, 30.0F, true, true, 1.0))
                                    .ifPresent(slash -> slash.setBaseSize(2.0F))))));

    /** 初雪连段动作倍率。先保守 1.5，实测后再往 1.75 / 2.0 调。 */
    private static final float FAST_GROUND_SPEED = 1.5F;

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
