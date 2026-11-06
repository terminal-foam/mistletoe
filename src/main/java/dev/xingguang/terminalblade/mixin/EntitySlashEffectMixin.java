package dev.xingguang.terminalblade.mixin;

import dev.xingguang.terminalblade.combat.MistletoeDamage;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 本体斩击命中走的是玩家近战伤害，伤害源里没有斩击实体。
 * 在斩击 tick 期间记下当前斩击，伤害钩子才能按生成时的刀而不是命中瞬间的主手判断。
 */
@Mixin(EntitySlashEffect.class)
public abstract class EntitySlashEffectMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void terminalblade$pushSlash(CallbackInfo callback) {
        MistletoeDamage.pushSlash((EntitySlashEffect) (Object) this);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void terminalblade$popSlash(CallbackInfo callback) {
        MistletoeDamage.popSlash((EntitySlashEffect) (Object) this);
    }
}
