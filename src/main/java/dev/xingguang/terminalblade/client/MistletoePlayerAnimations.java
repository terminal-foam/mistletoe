package dev.xingguang.terminalblade.client;

import dev.xingguang.terminalblade.TerminalBlade;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 把初雪的快速连段（mistletoe:combo_aN_fast）注册进 SlashBlade 的玩家动画表。
 *
 * <p>SlashBlade 的 PlayerAnimationOverrider 只认识 slashblade:combo_a1..a5；
 * 初雪地面连段被 FastComboBridge 重定向到 mistletoe:combo_aN_fast 后，
 * 客户端在动画表里查不到对应动作，玩家会退化成原版挥臂（视觉上“动作异常”）。
 * 这里按原版 A1..A5 的 VMD 帧区间补齐 fast 段的动画。</p>
 *
 * <p>2.0.7 的动画表是 {@code Map<ResourceLocation, VmdAnimation>}，
 * 2.1.9 改成了 {@code Map<ResourceLocation, Supplier<IAnimation>>}。
 * 两个版本连段 ID 和 VmdAnimation 构造器都一样，所以用反射 + 运行时探测兼容。</p>
 */
public final class MistletoePlayerAnimations {
    private static final ResourceLocation PLAYER_MOTION =
            ResourceLocation.fromNamespaceAndPath("slashblade", "model/pa/player_motion.vmd");
    private static final String OVERRIDER_CLASS =
            "mods.flammpfeil.slashblade.compat.playerAnim.PlayerAnimationOverrider";
    private static final String VMD_CLASS =
            "mods.flammpfeil.slashblade.compat.playerAnim.VmdAnimation";

    private MistletoePlayerAnimations() {
    }

    public static void install(FMLClientSetupEvent event) {
        // 与 SlashBlade ClientHandler 相同：只有装了 playeranimator 才会启用 PlayerAnimationOverrider。
        if (!ModList.get().isLoaded("playeranimator")) {
            return;
        }
        event.enqueueWork(() -> {
            try {
                Class<?> overriderClass = Class.forName(OVERRIDER_CLASS);
                Object overrider = overriderClass.getMethod("getInstance").invoke(null);
                @SuppressWarnings("unchecked")
                Map<ResourceLocation, Object> animations =
                        (Map<ResourceLocation, Object>) overriderClass.getMethod("getAnimation").invoke(overrider);
                Object probe = animations.get(ResourceLocation.fromNamespaceAndPath("slashblade", "combo_a1"));
                boolean supplierStyle = probe instanceof Supplier;
                Constructor<?> vmd = Class.forName(VMD_CLASS)
                        .getConstructor(ResourceLocation.class, double.class, double.class, boolean.class);
                installOne(animations, vmd, supplierStyle, "combo_a1_fast", 1.0, 41.0);
                installOne(animations, vmd, supplierStyle, "combo_a2_fast", 100.0, 151.0);
                installOne(animations, vmd, supplierStyle, "combo_a3_fast", 200.0, 306.0);
                installOne(animations, vmd, supplierStyle, "combo_a4_fast", 500.0, 608.0);
                installOne(animations, vmd, supplierStyle, "combo_a5_fast", 900.0, 1061.0);
                TerminalBlade.LOGGER.info("[槲寄生] 已为初雪快速连段注册 {} 个玩家动画", 5);
            } catch (Throwable throwable) {
                TerminalBlade.LOGGER.warn("[槲寄生] 注册初雪玩家动画失败", throwable);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static void installOne(Map<ResourceLocation, Object> animations, Constructor<?> vmd,
                                   boolean supplierStyle, String fastId, double start, double end) throws Exception {
        Object animation = vmd.newInstance(PLAYER_MOTION, start, end, false);
        Object value = supplierStyle ? (Supplier<Object>) () -> animation : animation;
        animations.put(TerminalBlade.id(fastId), value);
    }
}
