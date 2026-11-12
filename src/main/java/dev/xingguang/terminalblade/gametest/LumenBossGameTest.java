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
        helper.assertTrue(Math.abs(target.getHealth() - 995.0F) < 0.01F, "朝露首刀不是 5：" + target.getHealth());

        boss.setPhase(LumenPhase.FIRST_SNOW);
        target.setHealth(1000.0F);
        boss.doHurtTarget(target);
        helper.assertTrue(Math.abs(target.getHealth() - 994.0F) < 0.01F, "初雪首刀不是 6：" + target.getHealth());

        boss.setPhase(LumenPhase.MISTLETOE);
        target.setHealth(1000.0F);
        boss.doHurtTarget(target);
        helper.assertTrue(Math.abs(target.getHealth() - 800.0F) < 0.01F,
                "暮色首刀不是目标最大生命的 20%：" + target.getHealth());
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

    /** 转阶段即刻起手，并且完整打完七刀霜刃（不因目标锁定丢失而中断）。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void phaseFrostTransitionExecutesAllSevenStrikes(GameTestHelper helper) {
        LumenSeishinEntity boss = helper.spawn(ModEntities.LUMEN_SEISHIN.get(), 2, 1, 2);
        Pig target = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.PIG, 2, 1, 3);
        setMaxHealth(target, 1000.0D);
        boss.setNoAi(true);
        boss.setYRot(0.0F);
        boss.setHealth(239.0F);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(boss.getPhase() == LumenPhase.FIRST_SNOW, "未即时进入初雪阶段");
            helper.assertTrue(target.getHealth() < 1000.0F, "转阶段没有立即发动第一刀霜刃");
            helper.runAfterDelay(45, () -> {
                helper.assertTrue(Math.abs(target.getHealth() - 986.0F) < 0.01F,
                        "霜刃转阶段未完成七次判定：" + target.getHealth() + " phase=" + boss.getPhase() + " left=" + boss.debugFrostHitsLeft() + " targets=" + boss.debugFrostTargets());
                helper.succeed();
            });
        });
    }
    /** 随从 20 血 + 下界合金全甲 + 保护 IV：一次 20 点伤害不该秒杀。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void companionTakesHeavilyReducedDamage(GameTestHelper helper) {
        dev.xingguang.terminalblade.entity.LumenCompanionEntity pet =
                helper.spawn(ModEntities.LUMEN_COMPANION.get(), 2, 1, 2);
        helper.assertTrue(pet.getMaxHealth() == 20.0F, "随从最大生命不是 20：" + pet.getMaxHealth());
        helper.assertTrue(Math.abs(pet.getAttributeValue(Attributes.ARMOR) - 20.0D) < 0.01D,
                "护甲不是 20：" + pet.getAttributeValue(Attributes.ARMOR));
        helper.assertTrue(Math.abs(pet.getAttributeValue(Attributes.ARMOR_TOUGHNESS) - 12.0D) < 0.01D,
                "韧性不是 12：" + pet.getAttributeValue(Attributes.ARMOR_TOUGHNESS));

        pet.hurt(pet.damageSources().generic(), 20.0F);
        float left = pet.getHealth();
        helper.assertTrue(left > 10.0F,
                "20 点伤害打得太多（减伤没生效？）剩余：" + left);
        helper.assertTrue(left < 20.0F, "20 点伤害完全没生效，剩余：" + left);
        helper.succeed();
    }
    /** 随从普攻用玩家公式：D = base + k × (目标最大生命 − 自己当前生命)。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void companionMeleeUsesPlayerFormula(GameTestHelper helper) {
        dev.xingguang.terminalblade.entity.LumenCompanionEntity pet =
                helper.spawn(ModEntities.LUMEN_COMPANION.get(), 2, 1, 2);
        Pig target = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.PIG, 2, 1, 3);
        setMaxHealth(target, 1000.0D);

        pet.doHurtTarget(target);
        float dealt = 1000.0F - target.getHealth();
        // base 5 + 0.2 × (1000 − 20) = 201；旧的 10% 公式只会打 100
        helper.assertTrue(dealt > 150.0F,
                "随从普攻没有使用玩家公式（生命差额），实际只打了：" + dealt);
        helper.succeed();
    }
    /** Boss 会主动锁定敌对生物。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void bossAttacksHostileMobs(GameTestHelper helper) {
        LumenSeishinEntity boss = helper.spawn(ModEntities.LUMEN_SEISHIN.get(), 2, 1, 2);
        net.minecraft.world.entity.monster.Zombie zombie = helper.spawnWithNoFreeWill(
                net.minecraft.world.entity.EntityType.ZOMBIE, 2, 1, 6);
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(boss.getTarget() instanceof net.minecraft.world.entity.monster.Zombie,
                    "Boss 没有主动锁定敌对生物，当前目标：" + boss.getTarget());
            helper.succeed();
        });
    }

    /** 未收服的随从同样会主动锁定敌对生物。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void untamedCompanionAttacksHostileMobs(GameTestHelper helper) {
        dev.xingguang.terminalblade.entity.LumenCompanionEntity pet =
                helper.spawn(ModEntities.LUMEN_COMPANION.get(), 2, 1, 2);
        helper.assertFalse(pet.isTame(), "这只随从不该是已收服状态");
        net.minecraft.world.entity.monster.Zombie zombie = helper.spawnWithNoFreeWill(
                net.minecraft.world.entity.EntityType.ZOMBIE, 2, 1, 6);
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(pet.getTarget() instanceof net.minecraft.world.entity.monster.Zombie,
                    "未收服的随从没有主动锁定敌对生物，当前目标：" + pet.getTarget());
            helper.succeed();
        });
    }
    /** 终局蓄力对敌对生物同样生效。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void finalChargeKillsHostileMobs(GameTestHelper helper) {
        LumenSeishinEntity boss = helper.spawn(ModEntities.LUMEN_SEISHIN.get(), 2, 1, 2);
        setMaxHealth(boss, 1000.0D);
        net.minecraft.world.entity.monster.Zombie zombie = helper.spawnWithNoFreeWill(
                net.minecraft.world.entity.EntityType.ZOMBIE, 3, 1, 2);
        boss.resolveFinalCharge();
        helper.assertTrue(!zombie.isAlive(),
                "终局蓄力没有结算敌对生物，僵尸剩余生命：" + zombie.getHealth());
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

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void bossCannotAttackWhileCharging(GameTestHelper helper) {
        LumenSeishinEntity boss = helper.spawn(ModEntities.LUMEN_SEISHIN.get(), 2, 1, 2);
        Pig target = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.PIG, 2, 1, 4);
        setMaxHealth(target, 1000.0D);
        boss.setPhase(LumenPhase.MISTLETOE);
        boss.setHealth(36.0F);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(boss.isFinalCharging(), "36 HP 没有进入蓄力");
            boolean hit = boss.doHurtTarget(target);
            helper.assertTrue(!hit, "蓄力期间 Boss 仍然能普攻");
            helper.assertTrue(Math.abs(target.getHealth() - 1000.0F) < 0.01F,
                    "蓄力期间目标被打了：" + target.getHealth());
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void finalChargeGrantsNinetyPercentReduction(GameTestHelper helper) {
        LumenSeishinEntity boss = helper.spawn(ModEntities.LUMEN_SEISHIN.get(), 2, 1, 2);
        boss.setPhase(LumenPhase.MISTLETOE);
        boss.setHealth(36.0F);
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(boss.isFinalCharging(), "第 25 tick 已经不在蓄力");
            float before = boss.getHealth();
            boss.hurt(boss.damageSources().magic(), 10.0F);
            float taken = before - boss.getHealth();
            helper.assertTrue(Math.abs(taken - 1.0F) < 0.01F,
                    "蓄力期间减伤不是 90%，实际承受 " + taken);
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void phaseChangeReleasesSaWithNewBlade(GameTestHelper helper) {
        LumenSeishinEntity boss = helper.spawn(ModEntities.LUMEN_SEISHIN.get(), 2, 1, 2);
        Pig target = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.PIG, 2, 1, 3);
        setMaxHealth(target, 1000.0D);
        // 跌破 240 后应立刻进入初雪并用新刀甩一次霜刃，没有无敌等待。
        boss.setHealth(239.0F);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(boss.getPhase() == LumenPhase.FIRST_SNOW, "没有进入初雪：" + boss.getPhase());
            helper.assertTrue(target.getHealth() < 1000.0F,
                    "转阶段没有立刻放霜刃，目标生命：" + target.getHealth());
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void bossMeleeChainHitsThreeTimesInDawn(GameTestHelper helper) {
        LumenSeishinEntity boss = helper.spawn(ModEntities.LUMEN_SEISHIN.get(), 2, 1, 2);
        Pig target = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.PIG, 2, 1, 4);
        setMaxHealth(target, 1000.0D);
        boss.setNoAi(true);
        boss.doHurtTarget(target);
        // 朝露三连：首刀立刻，后两刀分别在第 9 / 18 tick，共 3×5 = 15。
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(Math.abs(target.getHealth() - 985.0F) < 0.01F,
                    "朝露三连没有打出 3×5：" + target.getHealth());
            helper.succeed();
        });
    }

    /** 随从应该接受任意食物收服，但不接受非食物。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void companionAcceptsAnyFoodToTame(GameTestHelper helper) {
        dev.xingguang.terminalblade.entity.LumenCompanionEntity pet =
                helper.spawn(ModEntities.LUMEN_COMPANION.get(), 2, 1, 2);
        helper.assertTrue(pet.isFood(new net.minecraft.world.item.ItemStack(
                net.minecraft.world.item.Items.APPLE)), "苹果应该能用来收服");
        helper.assertTrue(pet.isFood(new net.minecraft.world.item.ItemStack(
                net.minecraft.world.item.Items.BREAD)), "面包应该能用来收服");
        helper.assertTrue(!pet.isFood(new net.minecraft.world.item.ItemStack(
                net.minecraft.world.item.Items.STONE)), "石头不该能用来收服");
        helper.assertTrue(!pet.isFood(new net.minecraft.world.item.ItemStack(
                net.minecraft.world.item.Items.IRON_INGOT)), "铁锭不该能用来收服");
        helper.succeed();
    }

    /** 臣服后必须主动把敌对生物设为目标，且不能误打被动生物。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void tamedFollowerSeeksHostileMobs(GameTestHelper helper) {
        dev.xingguang.terminalblade.entity.LumenCompanionEntity pet =
                helper.spawn(ModEntities.LUMEN_COMPANION.get(), 2, 1, 2);
        net.minecraft.world.entity.monster.Zombie foe =
                helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.ZOMBIE, 3, 1, 6);
        net.minecraft.world.entity.animal.Cow cow =
                helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.COW, 6, 1, 2);
        // 不需要真实玩家：主人 UUID 只用于归属判定，避免 mock 玩家登录把测试卡住
        pet.setTame(true, true);
        pet.setOwnerUUID(java.util.UUID.randomUUID());

        helper.runAfterDelay(60, () -> {
            LivingEntity target = pet.getTarget();
            String diag = " target=" + target
                    + " tame=" + pet.isTame()
                    + " owner=" + pet.getOwnerUUID()
                    + " canAttack=" + pet.canAttack(foe)
                    + " distSq=" + String.format("%.2f", pet.distanceToSqr(foe))
                    + " follow=" + pet.getAttributeValue(Attributes.FOLLOW_RANGE);
            helper.assertTrue(target == foe, "随从没有主动攻击敌对生物：" + diag);
            helper.assertTrue(pet.getTarget() != cow, "随从误把被动生物设为目标");
            helper.succeed();
        });
    }
}
