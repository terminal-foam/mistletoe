/*
 * 【草案 · 未编译 · 未接入 · 不写入仓库】
 * 初雪 SA「霜刃」专用刀光贴图绑定。
 *
 * 目的：让 wiring/frost_slash.png 真正替换掉本体刀光贴图。
 * 图集里画了 slash_ring / slash_sphere 只是「素材就绪」，
 * 和「运行时加载」是两件事；本文件是后者需要的最小挂点说明 + 代码草案。
 *
 * ===== 已核实的调用链（SlashBladeResharped 2.1.9 / 1.21.1 反编译） =====
 *   MistletoeCombat.spawnSlash(user, roll, critical, 0xDDEFFF)
 *     -> AttackManager.doSlash(...)                       // 返回 EntityDrive
 *     -> slash.setColor(color) / setBaseSize(2.4) / setLifetime(8)
 *   客户端：
 *   DriveRenderer.render(T entity, ...)
 *     -> BladeRenderState.setCol(color | alpha)
 *     -> BladeRenderState.renderOverridedLuminous(
 *            ItemStack.EMPTY, model /* drive.obj * /, "base",
 *            TEXTURE /* 写死 slashblade:model/util/ss.png * /, ...)
 *
 * 关键事实（容易踩错）：
 *  1. DriveRenderer 第 39 行 private static final TEXTURE = ss.png；
 *     第 66 行直接把它传给 renderOverridedLuminous。
 *  2. DriveRenderer.getTextureLocation(entity) 虽然返回
 *     ((EntityAbstractSummonedSword)entity).getTextureLoc()，
 *     但该 render() 重写里**根本没有调用它**。也就是说，
 *     「给 EntityDrive 设置 textureLoc」不会改变实际渲染贴图。
 *  3. 唯一不需要 mixin 的入口：
 *     BladeRenderState.renderOverrided(...) 在取 VertexConsumer 之前
 *     post RenderOverrideEvent（BladeRenderState:116）。事件暴露
 *       getOriginalTexture() / getOriginalTarget() / getTarget()
 *       setTexture(ResourceLocation) / setModel(...) / setTarget(...)
 *     因此订阅该事件、按 originalTexture + target 判定后 setTexture 即可。
 *  4. drive.obj 只有 12 个 vt，u ∈ [0.069, 0.675]、v ∈ [0.304, 0.604]，
 *     且 WavefrontObject.parseTextureCoordinate 做 v = 1 - v_obj。
 *     原版 ss.png 是 32x32。所以专用贴图必须是**独立的 32x32 PNG**，
 *     图形画在上述 UV 窗口内；直接把 1024² 图集（或其中 256² 分区）
 *     塞给 RenderType 会采到错误区域甚至全透明。
 *  5. RenderOverrideEvent 是 @OnlyIn(Dist.CLIENT)，订阅必须放客户端侧；
 *     事件里拿不到 EntityDrive 本身，只能靠 originalTexture + target +
 *     setCol 留下的颜色（BladeRenderState.col，protected static）区分。
 *     霜刃颜色是 0xDDEFFF，可作为判定条件之一，但更稳妥的做法是
 *     在 spawnSlash 时给实体打一个同步过来的标记（见下方 TODO）。
 *
 * ===== 仍未解决、必须先定方案（清单第 5 节 6 个未决项） =====
 *  TODO-1 判定范围：只给霜刃换贴图，还是所有 EntityDrive 统一换？
 *          当前草案用 originalTexture + color 粗判，可能误伤其他 SA。
 *  TODO-2 颜色来源：BladeRenderState.col 是 protected static，
 *          需确认是否有公开 getter；没有则改用 entity 标记 + S2C 同步。
 *  TODO-3 资源位置：frost_slash.png 放 assets/mistletoe/model/util/，
 *          并在 first_snow 的 SA 配置里声明，还是全局注册？
 *  TODO-4 RenderType：renderOverridedLuminous 用加色混合，
 *          霜刃需要冷白半透明，是否要自定义 RenderType 由事件 getGetRenderType 覆盖。
 *  TODO-5 粒子：p_leaf / p_berry 需要独立 particle JSON + ParticleProvider，
 *          主图集两格不能充当粒子贴图。
 *  TODO-6 兼容：确认 NeoForge 事件总线、MOD_ID、@EventBusSubscriber 的
 *          modid / value = Dist.CLIENT 与本工程一致；本草案未编译验证。
 *
 * 结论：本文件是「绑定方案说明 + 可改造骨架」，不是可运行补丁。
 *       在 TODO 全部落定并 runClient 验证前，霜刃仍使用本体 ss.png 刀光。
 */
package dev.xingguang.terminalblade.client.draft;   // TODO-6 核对包名与 MOD_ID

import mods.flammpfeil.slashblade.event.client.RenderOverrideEvent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "mistletoe", value = Dist.CLIENT)  // TODO-6
public final class FrostSlashBinding {

    /** 本体刀光贴图，DriveRenderer 硬编码；用于精确判定「这是一道 drive 刀光」。 */
    private static final ResourceLocation VANILLA_DRIVE =
            ResourceLocation.fromNamespaceAndPath("slashblade", "model/util/ss.png");

    /** 初雪霜刃刀光：32x32，UV 窗口与 drive.obj 对齐。 */
    private static final ResourceLocation FROST_SLASH =
            ResourceLocation.fromNamespaceAndPath("mistletoe", "model/util/frost_slash.png");

    private FrostSlashBinding() {}

    @SubscribeEvent
    public static void onRenderOverride(RenderOverrideEvent event) {
        if (!"base".equals(event.getOriginalTarget())) {
            return;
        }
        if (!VANILLA_DRIVE.equals(event.getOriginalTexture())) {
            return;
        }
        // TODO-1 / TODO-2：这里只按「是 drive 刀光」粗判，会命中所有 SA。
        // 落定后应改为：只在该 EntityDrive 带霜刃标记时替换。
        // 可行做法：spawnSlash 时写入 entity.getPersistentData()，
        // 通过 S2C 同步一个 id 集合，客户端用 BladeRenderState.col
        // (0xDDEFFF) 或该集合判定。
        event.setTexture(FROST_SLASH);
        // TODO-4：如需冷白半透明而非加色，用
        // event.setGetRenderType(loc -> ...自定义 RenderType...);
    }
}
