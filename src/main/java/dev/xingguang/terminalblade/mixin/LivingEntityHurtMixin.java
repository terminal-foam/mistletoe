package dev.xingguang.terminalblade.mixin;

import dev.xingguang.terminalblade.blade.MistletoeBlade;
import dev.xingguang.terminalblade.combat.MistletoeDamage;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 初雪普通攻击在 hurt 完整返回后再清一次受击无敌帧。
 * 之前用 LivingDamageEvent.Post 清空会被 hurt 后面的字段写入覆盖，所以这里放在 RETURN。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHurtMixin {
    @Inject(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("RETURN"))
    private void terminalblade$clearFirstSnowIFrames(DamageSource source, float amount,
                                                     CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide() || !cir.getReturnValueZ()) {
            return;
        }
        if (!(source.getEntity() instanceof LivingEntity attacker)
                || !MistletoeBlade.isFirstSnow(attacker.getMainHandItem())) {
            return;
        }
        ResourceKey<DamageType> type = source.typeHolder().unwrapKey().orElse(null);
        if (MistletoeDamage.FROST_BLADE.equals(type)) {
            return;
        }
        self.invulnerableTime = 0;
    }
}
