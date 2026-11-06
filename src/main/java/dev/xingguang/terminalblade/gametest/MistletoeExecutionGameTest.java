package dev.xingguang.terminalblade.gametest;

import dev.xingguang.terminalblade.TerminalBlade;
import dev.xingguang.terminalblade.blade.MistletoeBlade;
import dev.xingguang.terminalblade.combat.execution.PhaseLockAdapter;
import dev.xingguang.terminalblade.combat.execution.PhaseLockResolvers;
import dev.xingguang.terminalblade.combat.execution.PhaseLockStatus;
import dev.xingguang.terminalblade.combat.execution.SuperSaExecutionService;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 0.1.2 的 Super SA 处决测试（T-01..T-07）。
 *
 * <p>测试监听器只由本类在单个测试内注册，不放进主 jar；`gametest` 包在生产构建中排除。
 * 这些测试直接调用处决服务，不依赖真人输入；真人 V/右键在客户端验收清单里。</p>
 */
@GameTestHolder(TerminalBlade.MOD_ID)
public class MistletoeExecutionGameTest {
    private static final Set<UUID> CANCEL_DAMAGE = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> CANCEL_DEATH = ConcurrentHashMap.newKeySet();

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void superKillsInvulnerableTargetWhenNotPhaseLocked(GameTestHelper helper) {
        Zombie caster = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 1, 2);
        Zombie target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 1, 2);
        giveBlade(helper, caster);
        target.setInvulnerable(true);

        SuperSaExecutionService.Result result = SuperSaExecutionService.execute(caster, target);

        helper.assertTrue(result == SuperSaExecutionService.Result.KILLED_BY_DAMAGE
                        || result == SuperSaExecutionService.Result.KILLED_BY_FORCE,
                "无敌目标没有被 Super SA 处决：" + result);
        helper.assertTrue(target.isRemoved(), "无敌目标仍然存在");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void superForcesThroughCancelledDamageEvent(GameTestHelper helper) {
        Zombie caster = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 1, 2);
        Zombie target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 1, 2);
        giveBlade(helper, caster);
        CancelDamageListener listener = new CancelDamageListener();
        NeoForge.EVENT_BUS.register(listener);
        CANCEL_DAMAGE.add(target.getUUID());
        try {
            SuperSaExecutionService.Result result = SuperSaExecutionService.execute(caster, target);
            helper.assertTrue(result == SuperSaExecutionService.Result.KILLED_BY_FORCE,
                    "伤害事件取消后没有走强制处决：" + result);
            helper.assertTrue(target.isRemoved(), "伤害事件取消后目标仍然存在");
        } finally {
            CANCEL_DAMAGE.remove(target.getUUID());
            NeoForge.EVENT_BUS.unregister(listener);
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void superUncancelsScopedDeathEvent(GameTestHelper helper) {
        Zombie caster = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 1, 2);
        Zombie target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 1, 2);
        giveBlade(helper, caster);
        CancelDeathListener listener = new CancelDeathListener();
        NeoForge.EVENT_BUS.register(listener);
        CANCEL_DEATH.add(target.getUUID());
        try {
            SuperSaExecutionService.Result result = SuperSaExecutionService.execute(caster, target);
            helper.assertTrue(result == SuperSaExecutionService.Result.KILLED_BY_FORCE,
                    "死亡事件取消后没有走强制处决：" + result);
            helper.assertTrue(target.isRemoved(), "死亡事件取消后目标仍然存在");
        } finally {
            CANCEL_DEATH.remove(target.getUUID());
            NeoForge.EVENT_BUS.unregister(listener);
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void phaseLockedBossTakesNoBypassDamage(GameTestHelper helper) {
        Zombie caster = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 1, 2);
        Zombie target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 1, 2);
        giveBlade(helper, caster);
        float before = target.getHealth();
        PhaseLockAdapter adapter = entity -> entity.getUUID().equals(target.getUUID())
                ? PhaseLockStatus.LOCKED : PhaseLockStatus.UNKNOWN;
        PhaseLockResolvers.register(adapter);
        try {
            SuperSaExecutionService.Result result = SuperSaExecutionService.execute(caster, target);
            helper.assertTrue(result == SuperSaExecutionService.Result.PHASE_LOCKED,
                    "阶段锁血目标没有在伤害前被拦下：" + result);
            helper.assertValueEqual(before, target.getHealth(), "阶段锁血目标仍然受到了 Super SA 伤害");
            helper.assertFalse(target.isRemoved(), "阶段锁血目标被移除了");
        } finally {
            PhaseLockResolvers.unregister(adapter);
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void phaseSwitchIsRecheckedBeforeForceKill(GameTestHelper helper) {
        Zombie caster = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 1, 2);
        Zombie target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 1, 2);
        giveBlade(helper, caster);
        CancelDamageListener listener = new CancelDamageListener();
        NeoForge.EVENT_BUS.register(listener);
        CANCEL_DAMAGE.add(target.getUUID());

        AtomicInteger queries = new AtomicInteger();
        PhaseLockAdapter enteringLock = entity -> {
            if (!entity.getUUID().equals(target.getUUID())) {
                return PhaseLockStatus.UNKNOWN;
            }
            // 第一次查询（伤害前）可处决；第二次查询（伤害后）进入锁血。
            return queries.getAndIncrement() == 0 ? PhaseLockStatus.UNLOCKED : PhaseLockStatus.LOCKED;
        };
        PhaseLockResolvers.register(enteringLock);
        try {
            SuperSaExecutionService.Result result = SuperSaExecutionService.execute(caster, target);
            helper.assertTrue(result == SuperSaExecutionService.Result.PHASE_LOCKED,
                    "Boss 在伤害后进入锁血时仍被强制处决：" + result);
            helper.assertFalse(target.isRemoved(), "伤害后进入锁血的目标被移除了");
        } finally {
            PhaseLockResolvers.unregister(enteringLock);
            CANCEL_DAMAGE.remove(target.getUUID());
            NeoForge.EVENT_BUS.unregister(listener);
        }

        // 切回可处决阶段：同一目标应恢复被 Super SA 处决。
        Zombie unlockedTarget = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 6, 1, 2);
        PhaseLockAdapter unlocked = entity -> entity.getUUID().equals(unlockedTarget.getUUID())
                ? PhaseLockStatus.UNLOCKED : PhaseLockStatus.UNKNOWN;
        PhaseLockResolvers.register(unlocked);
        try {
            SuperSaExecutionService.Result result = SuperSaExecutionService.execute(caster, unlockedTarget);
            helper.assertTrue(result == SuperSaExecutionService.Result.KILLED_BY_DAMAGE
                            || result == SuperSaExecutionService.Result.KILLED_BY_FORCE,
                    "切回可处决阶段后没有恢复处决：" + result);
            helper.assertTrue(unlockedTarget.isRemoved(), "切回可处决阶段后目标仍然存在");
        } finally {
            PhaseLockResolvers.unregister(unlocked);
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void unknownBossUsesDefaultRules(GameTestHelper helper) {
        Zombie caster = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 1, 2);
        Zombie target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 1, 2);
        giveBlade(helper, caster);

        SuperSaExecutionService.Result result = SuperSaExecutionService.execute(caster, target);

        helper.assertTrue(result == SuperSaExecutionService.Result.KILLED_BY_DAMAGE
                        || result == SuperSaExecutionService.Result.KILLED_BY_FORCE,
                "未适配目标没有按默认规则被处决：" + result);
        helper.assertTrue(target.isRemoved(), "未适配目标仍然存在；默认规则应可处决，保护请加标签");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void repeatedExecutionDoesNotDuplicate(GameTestHelper helper) {
        Zombie caster = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 1, 2);
        Zombie target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 1, 2);
        giveBlade(helper, caster);

        SuperSaExecutionService.execute(caster, target);
        SuperSaExecutionService.Result second = SuperSaExecutionService.execute(caster, target);

        helper.assertTrue(second == SuperSaExecutionService.Result.SKIPPED,
                "重复调用没有在已移除目标上短路：" + second);
        helper.assertTrue(target.isRemoved(), "重复调用后目标状态异常");
        helper.succeed();
    }

    private static void giveBlade(GameTestHelper helper, Zombie caster) {
        ResourceKey<SlashBladeDefinition> key = ResourceKey.create(SlashBladeDefinition.REGISTRY_KEY, MistletoeBlade.NAME);
        ItemStack blade = helper.getLevel().registryAccess()
                .lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY)
                .getOrThrow(key)
                .value()
                .getBlade(helper.getLevel().registryAccess());
        caster.setItemSlot(EquipmentSlot.MAINHAND, blade);
    }

    public static final class CancelDamageListener {
        @SubscribeEvent
        public void onDamage(LivingIncomingDamageEvent event) {
            if (CANCEL_DAMAGE.contains(event.getEntity().getUUID())) {
                event.setCanceled(true);
            }
        }
    }

    public static final class CancelDeathListener {
        @SubscribeEvent
        public void onDeath(LivingDeathEvent event) {
            if (CANCEL_DEATH.contains(event.getEntity().getUUID())) {
                event.setCanceled(true);
            }
        }
    }
}
