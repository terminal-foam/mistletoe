package dev.xingguang.terminalblade.combat.execution;

import net.minecraft.world.entity.LivingEntity;

/**
 * 已知 Boss 模组的阶段锁血适配器。第三方模组在公共初始化阶段调用
 * {@link PhaseLockResolvers#register(PhaseLockAdapter)} 注册。
 *
 * <p>实现必须是只读、无副作用、能快速返回；不要在这里改变 Boss 状态。</p>
 */
@FunctionalInterface
public interface PhaseLockAdapter {
    /**
     * @return 当前目标是否处于不可被强制处决的阶段；没有适配信息时返回 {@link PhaseLockStatus#UNKNOWN}
     */
    PhaseLockStatus getPhaseLock(LivingEntity entity);
}
