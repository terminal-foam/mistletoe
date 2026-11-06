package dev.xingguang.terminalblade.combat.execution;

import dev.xingguang.terminalblade.TerminalBlade;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 阶段锁血解析：
 * <ol>
 *   <li>动态适配器优先；任一适配器返回 LOCKED 即锁血。</li>
 *   <li>没有动态答案时才看数据包保护标签。</li>
 *   <li>都没有则为 UNKNOWN；UNKNOWN 由处决服务按普通实体处理。</li>
 * </ol>
 *
 * <p>静态标签是“该实体类型不允许强制处决”的保守兜底，不代表真实的动态阶段状态；
 * 动态适配器明确给出 UNLOCKED 时不会被静态标签覆盖。</p>
 */
public final class PhaseLockResolvers {
    /** 保守兜底：类型出现在这个标签里的实体不会被强制处决。 */
    public static final TagKey<EntityType<?>> PHASE_PROTECTED =
            TagKey.create(Registries.ENTITY_TYPE, TerminalBlade.id("super_sa_phase_protected"));

    private static final List<PhaseLockAdapter> ADAPTERS = new CopyOnWriteArrayList<>();

    private PhaseLockResolvers() {
    }

    public static void register(PhaseLockAdapter adapter) {
        if (adapter != null && !ADAPTERS.contains(adapter)) {
            ADAPTERS.add(adapter);
        }
    }

    public static void unregister(PhaseLockAdapter adapter) {
        ADAPTERS.remove(adapter);
    }

    public static PhaseLockStatus resolve(LivingEntity entity) {
        if (entity == null) {
            return PhaseLockStatus.UNKNOWN;
        }
        boolean sawUnlocked = false;
        for (PhaseLockAdapter adapter : ADAPTERS) {
            try {
                PhaseLockStatus status = adapter.getPhaseLock(entity);
                if (status == PhaseLockStatus.LOCKED) {
                    return PhaseLockStatus.LOCKED;
                }
                if (status == PhaseLockStatus.UNLOCKED) {
                    sawUnlocked = true;
                }
            } catch (RuntimeException exception) {
                // 适配器异常不能把整次 Super SA 打崩；按未知处理，继续下一个适配器/标签。
                TerminalBlade.LOGGER.warn("[槲寄生] 阶段锁血适配器异常，按 UNKNOWN 处理：{}", adapter, exception);
            }
        }
        if (sawUnlocked) {
            return PhaseLockStatus.UNLOCKED;
        }
        if (entity.getType().is(PHASE_PROTECTED)) {
            return PhaseLockStatus.LOCKED;
        }
        return PhaseLockStatus.UNKNOWN;
    }

    public static boolean isLocked(LivingEntity entity) {
        return resolve(entity) == PhaseLockStatus.LOCKED;
    }
}
