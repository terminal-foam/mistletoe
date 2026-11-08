/*
 * Super SA 蓄力「槲寄生沿鞘身螺旋缠绕生长」—— 1.20.1 Forge 版。
 *
 * 1.20.1 SlashBlade 1.1.23 的 RenderOverrideEvent 没有 getGetRenderType / packedLight，
 * WavefrontObject.tessellateOnly 也只有 (VertexConsumer, String...)。因此这里：
 *  - 用 BladeRenderState.getSlashBladeBlend / getSlashBladeBlendLuminous 取 RenderType；
 *  - 在绘制前设置 Face.setMatrix / setLightMap / setCol，tessellateOnly 直接读这些静态值；
 *  - 事件返回后 BladeRenderState 会重新设置并 reset，不需要我们恢复。
 */
package dev.xingguang.terminalblade.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.xingguang.terminalblade.TerminalBlade;
import dev.xingguang.terminalblade.blade.MistletoeBlade;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.capability.inputstate.IInputState;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Face;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import mods.flammpfeil.slashblade.event.client.RenderOverrideEvent;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import java.awt.Color;

@EventBusSubscriber(modid = TerminalBlade.MOD_ID, value = Dist.CLIENT)
public final class MistletoeGrowthRenderer {

    /** 与 make_growth.py 的常量必须一致。 */
    private static final int SEGMENTS = 32;
    private static final float SEG_LEN = 8.4375f;
    private static final float X0 = -16.0f;
    /** SuperSlashArts 的 chargeSuperSA 定在 pressTime + 20L。 */
    private static final float CHARGE_TICKS = 20.0f;

    private static float progress = 0.0f;

    private MistletoeGrowthRenderer() {
    }

    /** 供命令/调试/未来的 S2C 包覆写进度用。 */
    public static void setProgress(float p) {
        progress = Math.max(0.0f, Math.min(1.0f, p));
    }

    public static float getProgress() {
        return progress;
    }

    // ------------------------------------------------------------ 进度：零网络包
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            progress = 0.0f;
            return;
        }
        ItemStack stack = player.getMainHandItem();
        IInputState input = player.getCapability(CapabilityInputState.INPUT_STATE).resolve().orElse(null);

        // 与服务端 SuperSlashArts / MistletoeHooks 的判定尽量对齐：
        // 手里是槲寄生、特殊招式键仍按住、落地、刀未破损未封印。
        // 注：InputCommand.SPRINT 由 SlashBladeKeyMappings.KEY_SPECIAL_MOVE 填入，
        // 不是 Minecraft 的疾跑键。
        boolean charging = MistletoeBlade.isMistletoe(stack)
                && !MistletoeBlade.isDawn(stack)
                && !MistletoeBlade.isBrokenOrSealed(stack)
                && input != null
                && input.getCommands().contains(InputCommand.SPRINT)
                && player.onGround();

        if (!charging) {
            progress = 0.0f;
            return;
        }
        long press = input.getLastPressTime(InputCommand.SPRINT);
        if (press < 0L) {
            progress = 0.0f;
            return;
        }
        long now = player.level().getGameTime();
        long elapsed = now - press;
        if (elapsed < 0L || elapsed >= (long) CHARGE_TICKS) {
            progress = 0.0f;
            return;
        }
        progress = Math.max(0.0f, Math.min(1.0f, elapsed / CHARGE_TICKS));
    }

    // ------------------------------------------------ 渲染：叠加自定义组，不改本体
    @SubscribeEvent
    public static void onRenderOverride(RenderOverrideEvent event) {
        if (progress <= 0.0f) {
            return;
        }
        if (!"sheath".equals(event.getOriginalTarget())) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || event.getStack() != player.getMainHandItem()) {
            return;
        }
        if (!MistletoeBlade.isMistletoe(event.getStack()) || MistletoeBlade.isDawn(event.getStack())) {
            return;
        }

        WavefrontObject model = event.getModel();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource buf = event.getBuffer();
        ResourceLocation tex = event.getTexture();

        // 1.20.1 的 tessellateOnly 不接收 PoseStack，必须先把静态矩阵/光照/颜色设好。
        Face.setMatrix(pose);
        Face.setLightMap(LightTexture.FULL_BRIGHT);
        Face.setCol(Color.WHITE);

        RenderType solid = BladeRenderState.getSlashBladeBlend(tex);
        VertexConsumer solidVb = buf.getBuffer(solid);
        VertexConsumer glowVb = buf.getBuffer(BladeRenderState.getSlashBladeBlendLuminous(tex));

        float grown = progress * SEGMENTS;
        int whole = (int) Math.floor(grown);
        float frac = grown - whole;

        for (int i = 0; i < Math.min(whole, SEGMENTS); i++) {
            drawSegment(model, pose, solidVb, glowVb, i, 1.0f);
        }
        if (whole < SEGMENTS && frac > 0.001f) {
            drawSegment(model, pose, solidVb, glowVb, whole, Math.max(0.02f, frac));
        }
    }

    private static void drawSegment(WavefrontObject model, PoseStack pose,
                                    VertexConsumer solidVb, VertexConsumer glowVb,
                                    int i, float along) {
        String body = String.format("grow_%02d", i);
        String glow = String.format("grow_lum_%02d", i);
        if (along >= 0.999f) {
            model.tessellateOnly(solidVb, body);
            model.tessellateOnly(glowVb, glow);
            return;
        }
        float pivot = X0 - SEG_LEN * i;
        pose.pushPose();
        pose.translate(pivot, 0.0f, 0.0f);
        pose.scale(along, 1.0f, 1.0f);
        pose.translate(-pivot, 0.0f, 0.0f);
        model.tessellateOnly(solidVb, body);
        model.tessellateOnly(glowVb, glow);
        pose.popPose();
    }
}
