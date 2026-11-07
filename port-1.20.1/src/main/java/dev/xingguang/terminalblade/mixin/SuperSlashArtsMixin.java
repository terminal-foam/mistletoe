package dev.xingguang.terminalblade.mixin;

import dev.xingguang.terminalblade.combat.MistletoeCombat;
import mods.flammpfeil.slashblade.ability.SuperSlashArts;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 本体 Super SA 把满耐久、千杀、妖刀和落地写死在 releaseSSA 里，没有按刀配置的公开入口。
 * 槲寄生只接管自己的刀，其它拔刀剑仍走原来的条件。
 */
@Mixin(SuperSlashArts.class)
public abstract class SuperSlashArtsMixin {
    @Inject(method = "releaseSSA", at = @At("HEAD"), cancellable = true, remap = false)
    private static void terminalblade$releaseMistletoe(ServerPlayer entity, CallbackInfo ci) {
        if (MistletoeCombat.releaseSuperFromInput(entity)) {
            ci.cancel();
        }
    }
}
