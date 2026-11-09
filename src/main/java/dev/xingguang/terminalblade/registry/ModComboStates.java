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

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
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
                        .speed(3.0F)
                        .priority(40)
                        .motionLoc(DefaultResources.ExMotionLocation)
                        .next(entity -> ComboStateRegistry.NONE.getId())
                        .nextOfTimeout(entity -> ComboStateRegistry.NONE.getId())
                        .clickAction(MistletoeCombat::beginFrostBlade)
                        .addTickAction(timeline.build())
                        .timeout(200)
                        .build();
            });

    /**
     * 玩家动作播放速度倍率。2.1.9 的 {@code VmdAnimation} 通过 {@code setSpeed} 原生支持倍速，
     * 客户端用同一个值加速 VMD；命中 tick 按 {@code 原 tick / 倍率} 缩放，分支窗口取缩放后
     * “最后一段命中 + 1”，让动作、伤害、连段推进三者同步。
     *
     * <p>仅 2.1.9+ 生效：2.0.7 的 VmdAnimation 没有 setSpeed，客户端会按 1x 播放。</p>
     */
    public static final float FAST_GROUND_SPEED = 2.0F;

    public static final DeferredHolder<ComboState, ComboState> FAST_COMBO_A1 = COMBO_STATES.register("combo_a1_fast",
            () -> fastGround(ComboStateRegistry.COMBO_A1, 2, entity -> TerminalBlade.id("combo_a2_fast")));
    public static final DeferredHolder<ComboState, ComboState> FAST_COMBO_A2 = COMBO_STATES.register("combo_a2_fast",
            () -> fastGround(ComboStateRegistry.COMBO_A2, 2, entity -> TerminalBlade.id("combo_a3_fast")));
    public static final DeferredHolder<ComboState, ComboState> FAST_COMBO_A3 = COMBO_STATES.register("combo_a3_fast",
            () -> fastGround(ComboStateRegistry.COMBO_A3, -1, entity -> TerminalBlade.id("combo_a4_fast"),
                    new Hit(2, user -> AttackManager.doSlash(user, -61.0F, false, false, 0.44F)),
                    new Hit(6, user -> AttackManager.doSlash(user, 138.0F, false, false, 0.44F))));
    public static final DeferredHolder<ComboState, ComboState> FAST_COMBO_A4 = COMBO_STATES.register("combo_a4_fast",
            () -> fastGround(ComboStateRegistry.COMBO_A4, -1, entity -> TerminalBlade.id("combo_a4_ex_fast"),
                    new Hit(8, user -> AttackManager.doSlash(user, 45.0F, false, false, 0.44F)),
                    new Hit(9, user -> AttackManager.doSlash(user, 50.0F, true, false, 0.44F))));
    /** 第 5 段，对应原版 combo_a4_ex（力量/饥饿状态下的强化突进斩）。 */
    public static final DeferredHolder<ComboState, ComboState> FAST_COMBO_A4_EX = COMBO_STATES.register("combo_a4_ex_fast",
            () -> fastGround(ComboStateRegistry.COMBO_A4_EX, -1, entity -> TerminalBlade.id("combo_a5_fast"),
                    new Hit(7, user -> AttackManager.doSlash(user, 70.0F, false, false, 1.0)),
                    new Hit(14, user -> AttackManager.doSlash(user, 255.0F, false, false, 1.0))));
    public static final DeferredHolder<ComboState, ComboState> FAST_COMBO_A5 = COMBO_STATES.register("combo_a5_fast",
            () -> fastGround(ComboStateRegistry.COMBO_A5, -1, entity -> TerminalBlade.id("combo_a1_fast"),
                    new Hit(15, user -> Optional.ofNullable(AttackManager.doSlash(user, 35.0F, false, true, 1.0))
                            .ifPresent(slash -> slash.setBaseSize(2.0F))),
                    new Hit(17, user -> Optional.ofNullable(AttackManager.doSlash(user, 40.0F, true, true, 1.0))
                            .ifPresent(slash -> slash.setBaseSize(2.0F))),
                    new Hit(19, user -> Optional.ofNullable(AttackManager.doSlash(user, 30.0F, true, true, 1.0))
                            .ifPresent(slash -> slash.setBaseSize(2.0F)))));

    private ModComboStates() {
    }

    /**
     * @param branchTicks 换段窗口（游戏 tick）；传 -1 表示“由命中时间轴的最后一段 + 1 自动推导”
     * @param hits        原版命中 tick（未缩放）；为空表示该段没有 tick 命中（A1/A2 走 clickAction）
     */
    private static ComboState fastGround(DeferredHolder<ComboState, ComboState> base, int branchTicks,
                                         Function<LivingEntity, ResourceLocation> fastNext, Hit... hits) {
        ComboState original = base.get();
        int lastHit = lastScaledTick(hits);
        int branch = branchTicks >= 0 ? branchTicks : Math.max(1, lastHit + 1);
        Consumer<LivingEntity> tickAction = hits.length == 0 ? original::tickAction : scaledHits(hits);
        ComboState.Builder builder = ComboState.Builder.newInstance()
                .startAndEnd(original.getStartFrame(), original.getEndFrame())
                .priority(original.getPriority())
                .speed(original.getSpeed() * FAST_GROUND_SPEED)
                .motionLoc(original.getMotionLoc())
                .next(ComboState.TimeoutNext.buildFromFrame(framesForTicks(branch), fastNext))
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

    /** 原版命中 tick（未缩放）。 */
    private record Hit(int originalTick, Consumer<LivingEntity> action) {
    }

    /**
     * 生成按 {@link #FAST_GROUND_SPEED} 缩放后的命中时间轴。
     * 多段命中缩放后落在同一 tick 时合并到该 tick，避免时间轴覆盖丢刀。
     */
    private static Consumer<LivingEntity> scaledHits(Hit... hits) {
        Map<Integer, Consumer<LivingEntity>> grouped = new TreeMap<>();
        for (Hit hit : hits) {
            int tick = scaledTick(hit.originalTick());
            grouped.merge(tick, hit.action(), (first, second) -> user -> {
                first.accept(user);
                second.accept(user);
            });
        }
        ComboState.TimeLineTickAction.TimeLineTickActionBuilder timeline = ComboState.TimeLineTickAction.getBuilder();
        grouped.forEach(timeline::put);
        return ComboState.EMPTY_TICK_ACTION
                .andThen(ArrowReflector::doTicks)
                .andThen(timeline.build());
    }

    private static int scaledTick(int originalTick) {
        return Math.max(0, Math.round(originalTick / FAST_GROUND_SPEED));
    }

    /** 缩放后最后一段命中的 tick（与 scaledHits 同一套算法）。 */
    private static int lastScaledTick(Hit... hits) {
        int last = 0;
        for (Hit hit : hits) {
            last = Math.max(last, scaledTick(hit.originalTick()));
        }
        return last;
    }

    /** TimeoutNext.buildFromFrame 用的是帧，这里把 tick 反推成帧：ticks × 1.5。 */
    private static int framesForTicks(int ticks) {
        return Math.max(1, (int) Math.ceil(ticks * 1.5D));
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
