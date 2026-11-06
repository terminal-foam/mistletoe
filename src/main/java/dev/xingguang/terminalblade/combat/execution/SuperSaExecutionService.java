package dev.xingguang.terminalblade.combat.execution;

import dev.xingguang.terminalblade.TerminalBlade;
import dev.xingguang.terminalblade.TerminalBladeConfig;
import dev.xingguang.terminalblade.combat.MistletoeDamage;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Super SA 的单个目标结算流程。顺序不能改：
 * 锁血检查 → 无敌穿透正常伤害 → 再查锁血 → 受控强制处决。
 *
 * <p>阶段锁血目标连 bypasses_invulnerability 的伤害都不打，避免在识别出锁血之前
 * 就把它打穿。UNKNOWN 按普通实体处理（可伤害、可强制处决）；需要保守保护的整合包
 * 使用 {@code terminalblade:super_sa_phase_protected} 标签。</p>
 */
public final class SuperSaExecutionService {
    public enum Result {
        KILLED_BY_DAMAGE,
        KILLED_BY_FORCE,
        PHASE_LOCKED,
        FORCE_FAILED,
        SKIPPED
    }

    private SuperSaExecutionService() {
    }

    public static Result execute(LivingEntity user, LivingEntity target) {
        if (user == null || target == null || user.level().isClientSide()
                || target.isRemoved() || target.isDeadOrDying() || target == user) {
            return Result.SKIPPED;
        }

        if (PhaseLockResolvers.resolve(target) == PhaseLockStatus.LOCKED) {
            log(target, "phase_locked_before_damage");
            return Result.PHASE_LOCKED;
        }

        float amount = target.getMaxHealth() + target.getAbsorptionAmount() + 1000.0F;
        MistletoeDamage.hurt(user, target, MistletoeDamage.GODSLAYER_ABSOLUTE, amount);

        // 注意：isAlive() 是 health > 0；正常死亡的实体在 die() 后先进入 20 tick 的
        // DYING 姿态，isRemoved() 要等 tickDeath 才变 true。这里用 isDeadOrDying()
        // （health <= 0）判断“正常伤害已经完成死亡”，避免把垂死目标再走一遍强制处决。
        if (target.isRemoved() || target.isDeadOrDying()) {
            log(target, "killed_by_damage");
            return Result.KILLED_BY_DAMAGE;
        }

        // 正常伤害之后可能刚好进入/退出阶段；必须在强制处决之前再查一次。
        if (PhaseLockResolvers.resolve(target) == PhaseLockStatus.LOCKED) {
            log(target, "phase_locked_after_damage");
            return Result.PHASE_LOCKED;
        }

        // 玩家死亡由 Player.die 处理，isRemoved() 不会变成 true；玩家目标默认被排除，
        // 若整合包显式打开 affect_players，只走正常伤害，不再做 setHealth(0)+die 兜底。
        if (target instanceof Player) {
            if (!target.isAlive()) {
                log(target, "player_died");
                return Result.KILLED_BY_DAMAGE;
            }
            log(target, "player_force_skipped");
            return Result.FORCE_FAILED;
        }

        if (MistletoeDamage.forceKill(user, target, MistletoeDamage.GODSLAYER_ABSOLUTE)) {
            log(target, "killed_by_force");
            return Result.KILLED_BY_FORCE;
        }

        TerminalBlade.LOGGER.warn("[槲寄生] Super SA 强制处决失败：{} ({})，目标仍在（health={}）",
                target.getName().getString(),
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()),
                target.getHealth());
        log(target, "force_failed");
        return Result.FORCE_FAILED;
    }

    private static void log(LivingEntity target, String outcome) {
        if (!TerminalBladeConfig.logDamage()) {
            return;
        }
        TerminalBlade.LOGGER.info("[槲寄生] Super SA target={} type={} outcome={} health={} removed={}",
                target.getName().getString(),
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()),
                outcome, target.getHealth(), target.isRemoved());
    }
}
