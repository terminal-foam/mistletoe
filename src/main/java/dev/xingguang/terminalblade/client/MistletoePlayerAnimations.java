package dev.xingguang.terminalblade.client;

import dev.xingguang.terminalblade.TerminalBlade;
import dev.xingguang.terminalblade.registry.ModComboStates;
import mods.flammpfeil.slashblade.compat.playerAnim.PlayerAnimationOverrider;
import mods.flammpfeil.slashblade.compat.playerAnim.VmdAnimation;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 把初雪的快速连段（mistletoe:combo_aN_fast）注册进 SlashBlade 的玩家动画表。
 *
 * <p>SlashBlade 的 PlayerAnimationOverrider 只认识 slashblade:combo_a1..a5；初雪地面连段被
 * FastComboBridge 重定向到 mistletoe:combo_aN_fast 后，客户端在动画表里查不到动作，玩家会退化成
 * 原版挥臂。这里按原版 A1..A5（含 A4_EX）的 VMD 帧区间补齐 fast 段。</p>
 *
 * <p>2.1.9 的 {@code VmdAnimation}（经由 PlayerAnimationBase）提供 {@code setSpeed}，用反射调用它把
 * 动作加速到 {@code FAST_GROUND_SPEED}；2.0.7 没有该方法，反射失败时按 1x 播放，不会崩。</p>
 *
 * <p>2.0.7 的动画表是 {@code Map<ResourceLocation, VmdAnimation>}，2.1.9 改成了
 * {@code Map<ResourceLocation, Supplier<IAnimation>>}；用运行时探测兼容两个版本。</p>
 */
public final class MistletoePlayerAnimations {
    private static final ResourceLocation PLAYER_MOTION =
            ResourceLocation.fromNamespaceAndPath("slashblade", "model/pa/player_motion.vmd");
    private static final Method SPEED_SETTER = resolveSpeedSetter();

    private MistletoePlayerAnimations() {
    }

    private static Method resolveSpeedSetter() {
        try {
            // 2.1.9 的 PlayerAnimationBase 提供 setSpeed；2.0.7 没有，返回 null 时按 1x 播放。
            return VmdAnimation.class.getMethod("setSpeed", double.class);
        } catch (Throwable throwable) {
            return null;
        }
    }

    public static void install(FMLClientSetupEvent event) {
        // 与 SlashBlade ClientHandler 相同：只有装了 playeranimator 才会启用 PlayerAnimationOverrider。
        if (!ModList.get().isLoaded("playeranimator")) {
            return;
        }
        event.enqueueWork(() -> {
            try {
                Map<ResourceLocation, ?> animations = PlayerAnimationOverrider.getInstance().getAnimation();
                Object probe = animations.get(ComboStateRegistry.COMBO_A1.getId());
                boolean supplierStyle = probe instanceof Supplier;
                installOne(animations, supplierStyle, "combo_a1_fast", 1.0, 41.0);
                installOne(animations, supplierStyle, "combo_a2_fast", 100.0, 151.0);
                installOne(animations, supplierStyle, "combo_a3_fast", 200.0, 306.0);
                installOne(animations, supplierStyle, "combo_a4_fast", 500.0, 608.0);
                installOne(animations, supplierStyle, "combo_a4_ex_fast", 800.0, 894.0);
                installOne(animations, supplierStyle, "combo_a5_fast", 900.0, 1061.0);
                TerminalBlade.LOGGER.info("[槲寄生] 已为初雪快速连段注册 6 个玩家动画（{}x）",
                        SPEED_SETTER == null ? 1.0F : ModComboStates.FAST_GROUND_SPEED);
            } catch (Throwable throwable) {
                TerminalBlade.LOGGER.warn("[槲寄生] 注册初雪玩家动画失败", throwable);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static void installOne(Map<ResourceLocation, ?> animations, boolean supplierStyle,
                                   String fastId, double start, double end) {
        Map<ResourceLocation, Object> raw = (Map<ResourceLocation, Object>) animations;
        Object value = supplierStyle
                ? (Supplier<Object>) () -> createAnimation(start, end)
                : createAnimation(start, end);
        raw.put(TerminalBlade.id(fastId), value);
    }

    private static VmdAnimation createAnimation(double start, double end) {
        VmdAnimation animation = new VmdAnimation(PLAYER_MOTION, start, end, false);
        if (SPEED_SETTER != null) {
            try {
                SPEED_SETTER.invoke(animation, (double) ModComboStates.FAST_GROUND_SPEED);
            } catch (Throwable ignored) {
                // 设置失败就按默认 1x 播放
            }
        }
        return animation;
    }
}
