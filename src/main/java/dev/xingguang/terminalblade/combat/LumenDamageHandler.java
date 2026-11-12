package dev.xingguang.terminalblade.combat;

import dev.xingguang.terminalblade.TerminalBlade;
import dev.xingguang.terminalblade.entity.LumenSeishinEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * 流明·星的阶段锁血钩子。放在独立类里，避免把实体逻辑和全局事件绑死。
 */
@EventBusSubscriber(modid = TerminalBlade.MOD_ID)
public final class LumenDamageHandler {
    private LumenDamageHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFinalDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof LumenSeishinEntity boss) {
            event.setNewDamage(boss.adjustFinalDamage(event.getNewDamage()));
        } else if (event.getEntity() instanceof dev.xingguang.terminalblade.entity.LumenCompanionEntity pet) {
            event.setNewDamage(pet.adjustFinalDamage(event.getSource(), event.getNewDamage()));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamageApplied(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof LumenSeishinEntity boss) {
            boss.onDamageResolved();
        }
    }
}
