package dev.xingguang.terminalblade.registry;

import dev.xingguang.terminalblade.TerminalBlade;
import dev.xingguang.terminalblade.combat.MistletoeCombat;
import mods.flammpfeil.slashblade.init.DefaultResources;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Consumer;

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
     * 初雪「霜刃」：同一段原版动作模板加速到 1.6 倍，7 个固定 tick 各斩一刀。
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

    private ModComboStates() {
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
