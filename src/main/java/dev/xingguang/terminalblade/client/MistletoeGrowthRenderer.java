/*
 * Super SA 蓄力「槲寄生沿鞘身螺旋缠绕生长」—— 客户端渲染草案（放进 src/main/java 即可）。
 *
 * 依据 SlashBladeResharped-2.1.9-1.21.1 反编译，非猜测：
 *  1) BladeRenderState.renderOverrided(...) 在取 VertexConsumer 之前 post
 *     RenderOverrideEvent（NeoForge.EVENT_BUS，@OnlyIn(CLIENT)），事件暴露
 *     stack / model / originalTarget / poseStack / buffer / texture /
 *     getRenderType / packedLight。
 *  2) WavefrontObject.tessellateOnly(vb, pose, light, color, String... groupNames)
 *     是**可变参数**，一次可画多个自定义组；它不会再次 post 事件，所以这里
 *     直接调用 tessellateOnly 不会递归。
 *  3) LayerMainBlade:168 与 LayerSlashBlade:172 都无条件渲染 "sheath"，
 *     挂载在 hardpointB（鞘骨骼）下；事件里拿到的 PoseStack 已含该骨骼矩阵。
 *     枝条因此跟着鞘走，拔刀后仍留在鞘上 —— 符合「从鞘身萌发」的设定。
 *  4) 蓄力那 20 tick 玩家停在 ComboState.NONE→STANDBY 的静止纳刀帧，
 *     hardpointA 与 hardpointB 几乎重合、刀身藏在鞘里，直到 t+20 切
 *     godslayer_bloom（VMD 1900–1960）才真正拔刀。所以 v0.1 挂在 blade 上、
 *     贴刀背的枝条会被鞘外壳吃掉，越往切先越看不见 —— 本版改挂 sheath。
 *  5) 本体的 effect 蓄力特效条件是 ISlashBladeState.isCharged()
 *     (= 右键蓄力 getTicksUsingItem() > 9)，Super SA 是特殊招式键保持
 *     (SuperSlashArts: pressTime + 20 tick)，两者互不触发，必须自己挂。
 *  6) MoveInputHandler 在客户端也维护 IInputState（commands + lastPressTimes），
 *     只在变化时发 MoveCommandMessage 给服务端。因此本地客户端可以零网络包
 *     拿到和服务端同源的蓄力起点，进度 = (gameTime - lastPressTime) / 20。
 *
 * 配套资源：growth/mistletoe.obj 里的 grow_00..31（螺旋藤 + 叶 + 白果，
 * 双面十字扁带）与 grow_lum_00..31（加色藤线）。藤在鞘外表面之外 GAP=1.75，
 * 绕鞘 6 圈；贴图复用原 mistletoe.png，无需改动。
 *
 * 为什么是 32 子段：v0.1 对整段做 scale(along,1,1) 会把螺旋压成弹簧、
 * 甚至让藤嵌进鞘壁。子段只有 8.4375 长、67.5°，局部缩放误差 < 1 单位，
 * GAP 足以吸收，所以生长看起来是「藤尖绕着鞘往前钻」而不是「弹簧伸缩」。
 *
 * 已知边界：
 *  - 进度只在本地客户端算，别的玩家看不到你鞘上的枝条（要第三人称同步，
 *    需加一个 S2C payload：服务端在 pressTime+0 发 START、release/松手发 STOP，
 *    客户端把 progress 换成远端插值即可，本类其余代码不用动）。
 *  - 只认主手物品：副手 / 快捷栏备用的槲寄生走 renderStandbyBlade，
 *    也会 post 事件，这里用 getStack() 比对排除。
 *  - 若别的 mod 也在 sheath 上叠加渲染，可能 overdraw，但不会报错。
 *
 * 这是可直接落地的草案，未在本工程编译过；接入时请按项目实际的
 * MOD_ID / 事件总线注册方式核对一遍（本工程已有 @EventBusSubscriber 用法）。
 */
package dev.xingguang.terminalblade.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.xingguang.terminalblade.TerminalBlade;
import dev.xingguang.terminalblade.blade.MistletoeBlade;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.capability.inputstate.IInputState;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import mods.flammpfeil.slashblade.event.client.RenderOverrideEvent;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = TerminalBlade.MOD_ID, value = Dist.CLIENT)
public final class MistletoeGrowthRenderer {

    /** 与 make_growth.py 的常量必须一致。 */
    private static final int SEGMENTS = 32;
    private static final float SEG_LEN = 8.4375f;
    private static final float X0 = -16.0f;
    /** SuperSlashArts 的 chargeSuperSA 定在 pressTime + 20L。 */
    private static final float CHARGE_TICKS = 20.0f;
    /** 只在 20 tick 蓄力窗口内显示；窗口结束/松手立即归零。 */
    private static final float RETRACT_PER_TICK = 1.0f;
    private static final int WHITE = 0xFFFFFFFF;

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
    public static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            progress = 0.0f;
            return;
        }
        ItemStack stack = player.getMainHandItem();
        IInputState input = player.getData(CapabilityInputState.INPUT_STATE.get());

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
        // 只挂在鞘（hardpointB）上；用 originalTarget 避免别的 mod 改过 target。
        // sheath_luminous / blade / effect / item_* 的 renderOverrided 也会 post 事件，
        // 一律跳过，否则同一层藤会被画多次。
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
        int light = event.getPackedLightIn();

        // 复用本次渲染的 RenderType（半透明 + 背面剔除，故 grow_* 全部做了双面）
        RenderType solid = event.getGetRenderType().apply(tex);
        VertexConsumer solidVb = buf.getBuffer(solid);
        // 加色发光层（NO_CULL + LIGHTNING_ADDITIVE）
        VertexConsumer glowVb = buf.getBuffer(BladeRenderState.getSlashBladeBlendLuminous(tex));

        float grown = progress * SEGMENTS;
        int whole = (int) Math.floor(grown);
        float frac = grown - whole;

        for (int i = 0; i < Math.min(whole, SEGMENTS); i++) {
            drawSegment(model, pose, solidVb, glowVb, light, i, 1.0f);
        }
        if (whole < SEGMENTS && frac > 0.001f) {
            // 子段只有 8.4375 长，沿 x 局部伸长不会明显破坏螺旋形状；
            // 径向余量 GAP=1.75 足以吸收反り/鞘宽在这一小段内的变化。
            drawSegment(model, pose, solidVb, glowVb, light, whole,
                    Math.max(0.02f, frac));
        }
    }

    private static void drawSegment(WavefrontObject model, PoseStack pose,
                                    VertexConsumer solidVb, VertexConsumer glowVb,
                                    int light, int i, float along) {
        String body = String.format("grow_%02d", i);
        String glow = String.format("grow_lum_%02d", i);
        if (along >= 0.999f) {
            model.tessellateOnly(solidVb, pose, light, WHITE, body);
            model.tessellateOnly(glowVb, pose, light, WHITE, glow);
            return;
        }
        float pivot = X0 - SEG_LEN * i;
        pose.pushPose();
        pose.translate(pivot, 0.0f, 0.0f);
        pose.scale(along, 1.0f, 1.0f);
        pose.translate(-pivot, 0.0f, 0.0f);
        model.tessellateOnly(solidVb, pose, light, WHITE, body);
        model.tessellateOnly(glowVb, pose, light, WHITE, glow);
        pose.popPose();
    }
}
