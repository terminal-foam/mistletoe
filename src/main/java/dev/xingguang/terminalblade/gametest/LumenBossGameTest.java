package dev.xingguang.terminalblade.gametest;

import dev.xingguang.terminalblade.TerminalBlade;
import dev.xingguang.terminalblade.entity.LumenPhase;
import dev.xingguang.terminalblade.entity.LumenSeishinEntity;
import dev.xingguang.terminalblade.registry.ModEntities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(TerminalBlade.MOD_ID)
public final class LumenBossGameTest {
    private LumenBossGameTest() {
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void bossSpawnsAtFullHealthInDawnPhase(GameTestHelper helper) {
        LumenSeishinEntity boss = helper.spawn(ModEntities.LUMEN_SEISHIN.get(), 2, 1, 2);
        helper.assertTrue(boss.getHealth() == LumenSeishinEntity.MAX_HEALTH, "初始生命不是 360：" + boss.getHealth());
        helper.assertTrue(boss.getPhase() == LumenPhase.DAWN, "初始阶段不是朝露：" + boss.getPhase());
        helper.assertTrue(boss.getAttributeValue(Attributes.SWEEPING_DAMAGE_RATIO) == 0.0D,
                "缺少 sweeping_damage_ratio，SlashBlade 出刀光会崩");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void bossPhasesAdvanceOnlyForward(GameTestHelper helper) {
        LumenSeishinEntity boss = helper.spawn(ModEntities.LUMEN_SEISHIN.get(), 2, 1, 2);
        boss.setHealth(239.0F);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(boss.getPhase() == LumenPhase.FIRST_SNOW, "239 HP 没有进入初雪：" + boss.getPhase());
            boss.setHealth(119.0F);
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(boss.getPhase() == LumenPhase.MISTLETOE, "119 HP 没有进入暮色：" + boss.getPhase());
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void bossPhaseLockClampsOverkill(GameTestHelper helper) {
        LumenSeishinEntity boss = helper.spawn(ModEntities.LUMEN_SEISHIN.get(), 2, 1, 2);
        // 一击打掉 300 HP：应被锁血截断在 240，不直接跳过朝露。
        boss.hurt(boss.damageSources().magic(), 300.0F);
        helper.assertTrue(boss.getHealth() >= 240.0F, "伤害没有被阶段锁血截断：" + boss.getHealth());
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void bossMeleeDamageScalesWithPhase(GameTestHelper helper) {
        LumenSeishinEntity boss = helper.spawn(ModEntities.LUMEN_SEISHIN.get(), 2, 1, 2);
        Pig target = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.PIG, 2, 1, 4);
        setMaxHealth(target, 1000.0D);

        boss.doHurtTarget(target);
        helper.assertTrue(Math.abs(target.getHealth() - 995.0F) < 0.01F, "朝露普攻不是 5：" + target.getHealth());

        boss.setPhase(LumenPhase.FIRST_SNOW);
        target.setHealth(1000.0F);
        boss.doHurtTarget(target);
        helper.assertTrue(Math.abs(target.getHealth() - 994.0F) < 0.01F, "初雪普攻不是 6：" + target.getHealth());

        boss.setPhase(LumenPhase.MISTLETOE);
        target.setHealth(1000.0F);
        boss.doHurtTarget(target);
        helper.assertTrue(Math.abs(target.getHealth() - 900.0F) < 0.01F,
                "暮色普攻不是玩家最大生命的 10%：" + target.getHealth());
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void bossFrostBladeIsSevenHitsOfTwo(GameTestHelper helper) {
        LumenSeishinEntity boss = helper.spawn(ModEntities.LUMEN_SEISHIN.get(), 2, 1, 2);
        Pig target = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.PIG, 2, 1, 3);
        setMaxHealth(target, 1000.0D);
        boss.setPhase(LumenPhase.FIRST_SNOW);

        for (int i = 0; i < 7; i++) {
            boss.frostStrike();
        }
        helper.assertTrue(Math.abs(target.getHealth() - 986.0F) < 0.01F,
                "霜刃不是 7 刀 × 2：" + target.getHealth());
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void bossSlashArtDealsQuarterMaxHealth(GameTestHelper helper) {
        LumenSeishinEntity boss = helper.spawn(ModEntities.LUMEN_SEISHIN.get(), 2, 1, 2);
        Pig target = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.PIG, 2, 1, 4);
        setMaxHealth(target, 1000.0D);
        boss.setPhase(LumenPhase.MISTLETOE);

        boss.slashStrike(target);
        helper.assertTrue(Math.abs(target.getHealth() - 750.0F) < 0.01F,
                "斩星不是最大生命的 25%：" + target.getHealth());
        helper.succeed();
    }

    private static void setMaxHealth(LivingEntity entity, double health) {
        entity.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        entity.setHealth((float) health);
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void bossEntersFinalChargeAtThirtySix(GameTestHelper helper) {
        LumenSeishinEntity boss = helper.spawn(ModEntities.LUMEN_SEISHIN.get(), 2, 1, 2);
        boss.setPhase(LumenPhase.MISTLETOE);
        boss.setHealth(36.0F);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(boss.isFinalCharging(), "36 HP 没有进入终局蓄力");
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void finalChargeKillsBossEvenWithoutHits(GameTestHelper helper) {
        LumenSeishinEntity boss = helper.spawn(ModEntities.LUMEN_SEISHIN.get(), 2, 1, 2);
        boss.setPhase(LumenPhase.MISTLETOE);
        boss.setHealth(36.0F);
        // 等换阶段的无敌窗口过去，再手动结算终局。
        helper.runAfterDelay(25, () -> {
            boss.resolveFinalCharge();
            helper.assertTrue(!boss.isAlive(), "终局释放后 Boss 没有死亡");
            helper.succeed();
        });
    }
}
