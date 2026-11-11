package dev.xingguang.terminalblade.gametest;

import dev.xingguang.terminalblade.TerminalBlade;
import dev.xingguang.terminalblade.entity.LumenPhase;
import dev.xingguang.terminalblade.entity.LumenSeishinEntity;
import dev.xingguang.terminalblade.registry.ModEntities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
}
