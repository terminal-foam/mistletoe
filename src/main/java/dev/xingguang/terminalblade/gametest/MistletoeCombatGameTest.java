package dev.xingguang.terminalblade.gametest;

import dev.xingguang.terminalblade.TerminalBlade;
import dev.xingguang.terminalblade.TerminalBladeConfig;
import dev.xingguang.terminalblade.blade.MistletoeBlade;
import dev.xingguang.terminalblade.combat.MistletoeCombat;
import dev.xingguang.terminalblade.combat.MistletoeDamage;
import dev.xingguang.terminalblade.combat.execution.SuperExecutionChannel;
import dev.xingguang.terminalblade.registry.ModComboStates;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(TerminalBlade.MOD_ID)
public class MistletoeCombatGameTest {
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void slashArtWaitsForTheHitTick(GameTestHelper helper) {
        Zombie caster = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 1, 2);
        Pig target = helper.spawnWithNoFreeWill(EntityType.PIG, 2, 1, 4);
        prepare(caster, target);
        giveBlade(helper, caster);
        setMaxHealth(target, 300.0D);
        float targetBefore = target.getHealth();
        float casterBefore = caster.getHealth();

        enter(caster, ModComboStates.SLASH_ART.getId());
        helper.assertValueEqual(targetBefore, target.getHealth(), "进入普通 SA 时目标已经掉血");
        helper.assertValueEqual(casterBefore, caster.getHealth(), "进入普通 SA 时已经献祭");

        fireHit(caster, ModComboStates.SLASH_ART.getId());
        helper.assertTrue(target.getHealth() < targetBefore, "命中帧没有打中目标");
        helper.assertTrue(caster.getHealth() < casterBefore, "强化 SA 命中后没有献祭");
        float targetAfter = target.getHealth();
        float casterAfter = caster.getHealth();
        fireHit(caster, ModComboStates.SLASH_ART.getId());
        helper.assertValueEqual(targetAfter, target.getHealth(), "同一次普通 SA 结算了两次伤害");
        helper.assertValueEqual(casterAfter, caster.getHealth(), "同一次普通 SA 献祭了两次");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void normalAttackVersusEnhancedSlashArt(GameTestHelper helper) {
        Zombie caster = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 1, 2);
        Pig target = helper.spawnWithNoFreeWill(EntityType.PIG, 2, 1, 4);
        prepare(caster, target);
        giveBlade(helper, caster);
        setMaxHealth(target, 300.0D);
        caster.setHealth(20.0F);
        target.setHealth(300.0F);
        target.invulnerableTime = 0;
        MistletoeDamage.clearSlashStack();

        target.hurt(caster.level().damageSources().mobAttack(caster), 8.0F);
        float normalLoss = 300.0F - target.getHealth();

        target.setHealth(300.0F);
        target.invulnerableTime = 0;
        caster.setHealth(20.0F);
        enter(caster, ModComboStates.SLASH_ART.getId());
        fireHit(caster, ModComboStates.SLASH_ART.getId());
        float saLoss = 300.0F - target.getHealth();
        float casterAfter = caster.getHealth();
        TerminalBlade.LOGGER.info(
                "[GameTest] 同一目标 平A掉血={} 强化SA掉血={} 施法者献祭后={} 目标剩余={}",
                normalLoss, saLoss, casterAfter, target.getHealth());

        helper.assertTrue(normalLoss > 60.0F && normalLoss < 68.0F,
                "平 A 实测不在 8 + 0.2×280 = 64 附近：" + normalLoss);
        helper.assertTrue(saLoss > normalLoss,
                "强化 SA 实测没有高于平 A：平A=" + normalLoss + " SA=" + saLoss);
        helper.assertTrue(saLoss > 120.0F && saLoss < 129.0F,
                "强化 SA 实测不在 8 + 0.2×282 + 60 = 124.4 附近：" + saLoss);
        helper.assertTrue(casterAfter > 17.0F && casterAfter < 19.0F,
                "强化 SA 后施法者不是剩 18 HP：" + casterAfter);
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void normalAttackFormulaAppliesOncePerTargetTick(GameTestHelper helper) {
        Zombie caster = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 1, 2);
        Pig target = helper.spawnWithNoFreeWill(EntityType.PIG, 2, 1, 4);
        prepare(caster, target);
        giveBlade(helper, caster);
        setMaxHealth(target, 300.0D);
        caster.setHealth(20.0F);
        target.setHealth(300.0F);
        target.invulnerableTime = 0;
        MistletoeDamage.clearSlashStack();

        target.hurt(caster.level().damageSources().mobAttack(caster), 8.0F);
        float firstLoss = 300.0F - target.getHealth();
        float afterFirst = target.getHealth();
        target.invulnerableTime = 0;
        target.hurt(caster.level().damageSources().mobAttack(caster), 8.0F);
        float secondLoss = afterFirst - target.getHealth();

        TerminalBlade.LOGGER.info(
                "[GameTest] 同一 tick 两次平A事件：第一次={} 第二次={} 目标剩余={}",
                firstLoss, secondLoss, target.getHealth());
        helper.assertTrue(firstLoss > 60.0F && firstLoss < 68.0F,
                "第一次平 A 不在公式 64 附近：" + firstLoss);
        helper.assertTrue(secondLoss < 20.0F,
                "同一 tick 的第二次平 A 又套了一遍公式：" + secondLoss);
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void dawnXiweiUsesFixedDamageAndRegeneration(GameTestHelper helper) {
        Cow caster = helper.spawnWithNoFreeWill(EntityType.COW, 2, 1, 2);
        Pig target = helper.spawnWithNoFreeWill(EntityType.PIG, 2, 1, 4);
        prepare(caster, target);
        giveBlade(helper, caster, MistletoeBlade.DAWN_NAME);
        setMaxHealth(target, 100.0D);
        target.setHealth(100.0F);
        caster.setHealth(10.0F);
        target.invulnerableTime = 0;
        MistletoeDamage.clearSlashStack();

        enter(caster, ModComboStates.SLASH_ART.getId());
        fireHit(caster, ModComboStates.SLASH_ART.getId());
        float loss = 100.0F - target.getHealth();
        var regen = caster.getEffect(MobEffects.REGENERATION);
        TerminalBlade.LOGGER.info("[GameTest] 熹微固定伤害掉血={} 恢复={} 目标剩余={}",
                loss, regen == null ? 0 : regen.getDuration(), target.getHealth());
        helper.assertTrue(loss > 11.5F && loss < 12.5F, "熹微不是固定 12 伤害：" + loss);
        helper.assertTrue(regen != null, "熹微命中后没有获得生命恢复 I");
        helper.assertTrue(regen.getDuration() > 80, "熹微生命恢复持续时间过短：" + regen.getDuration());
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void justSlashArtUsesItsOwnState(GameTestHelper helper) {
        Zombie caster = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 1, 2);
        Pig target = helper.spawnWithNoFreeWill(EntityType.PIG, 2, 1, 4);
        prepare(caster, target);
        giveBlade(helper, caster);
        setMaxHealth(target, 300.0D);
        float before = target.getHealth();
        enter(caster, ModComboStates.SLASH_ART_JUST.getId());
        helper.assertValueEqual(before, target.getHealth(), "进入 Just SA 时目标已经掉血");
        helper.assertValueEqual(ModComboStates.SLASH_ART_JUST.getId().toString(), combo(caster), "没有进入 Just ComboState");
        fireHit(caster, ModComboStates.SLASH_ART_JUST.getId());
        helper.assertTrue(target.getHealth() < before, "Just SA 命中帧没有造成伤害");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void superAttackFinishesBeforeSacrifice(GameTestHelper helper) {
        Zombie caster = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 1, 2);
        Zombie foe = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 1, 4);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, 4, 1, 2);
        ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, 3, 1, 3);
        prepare(caster, foe);
        giveBlade(helper, caster);
        caster.setHealth(caster.getMaxHealth());
        float casterBefore = caster.getHealth();
        enter(caster, ModComboStates.SUPER.getId());
        helper.assertTrue(foe.isAlive(), "进入 Super SA 时目标已经死亡");
        helper.assertTrue(cow.isAlive(), "进入 Super SA 时被动生物已经死亡");
        helper.assertValueEqual(casterBefore, caster.getHealth(), "进入 Super SA 时已经献祭");

        fireHit(caster, ModComboStates.SUPER.getId());
        helper.assertFalse(foe.isAlive(), "Super SA 没有击倒范围内的敌对生物");
        helper.assertTrue(cow.isAlive(), "Super SA 击倒了名单外的被动生物");
        helper.assertTrue(stand.isAlive(), "Super SA 击倒了盔甲架");
        helper.assertValueEqual(2.0F, caster.getHealth(), "满血 Super SA 之后不是剩余 2 点生命");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void superKillsCasterAfterTheAttackWhenLifeIsShort(GameTestHelper helper) {
        Zombie caster = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 1, 2);
        Zombie foe = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 1, 4);
        prepare(caster, foe);
        giveBlade(helper, caster);
        enter(caster, ModComboStates.SUPER.getId());
        caster.setHealth(10.0F);
        fireHit(caster, ModComboStates.SUPER.getId());
        helper.assertFalse(foe.isAlive(), "生命不足时范围攻击没有结算");
        helper.assertFalse(caster.isAlive(), "生命不足时攻击结束后施法者仍存活");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void brokenBladeDoesNotCastAfterDurabilityCost(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack blade = blade(helper);
        blade.setDamageValue(Math.max(0, blade.getMaxDamage() - 1));
        player.setItemSlot(EquipmentSlot.MAINHAND, blade);
        boolean stillFighting = MistletoeCombat.applyHalfDurabilityCost(player);
        helper.assertFalse(stillFighting, "扣除一半耐久后损坏的刀仍被允许发动");
        ItemStack held = player.getMainHandItem();
        helper.assertTrue(held.isEmpty() || MistletoeBlade.isBrokenOrSealed(held), "耐久耗尽后刀没有进入损坏状态");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void superWithoutTargetsStillSacrifices(GameTestHelper helper) {
        Zombie caster = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 1, 2);
        giveBlade(helper, caster);
        caster.setHealth(caster.getMaxHealth());

        enter(caster, ModComboStates.SUPER.getId());
        fireHit(caster, ModComboStates.SUPER.getId());

        helper.assertValueEqual(2.0F, caster.getHealth(), "无目标 Super SA 没有按规则献祭最大生命的 90%");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void creativeCasterDoesNotPaySuperSacrifice(GameTestHelper helper) {
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        caster.setGameMode(GameType.CREATIVE);
        caster.setItemSlot(EquipmentSlot.MAINHAND, blade(helper));
        // 普通生存玩家在这个血量放 Super SA 必死；创造玩家不应被扣血，更不应死亡。
        caster.setHealth(2.0F);

        var sacrifice = MistletoeCombat.applySuperSacrifice(caster);

        helper.assertValueEqual(0.0F, sacrifice.cost(), "创造模式玩家仍然被计算了 Super SA 献祭");
        helper.assertFalse(sacrifice.lethal(), "创造模式玩家被判定为 Super SA 致死");
        helper.assertTrue(caster.isAlive(), "创造模式玩家被 Super SA 献祭杀死");
        helper.assertValueEqual(2.0F, caster.getHealth(), "创造模式玩家仍然被扣了 Super SA 生命");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void frostBladeHitsConeTargets(GameTestHelper helper) {
        Zombie caster = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 1, 2);
        Pig left = helper.spawnWithNoFreeWill(EntityType.PIG, 1, 1, 4);
        Pig right = helper.spawnWithNoFreeWill(EntityType.PIG, 3, 1, 4);
        setMaxHealth(left, 300.0D);
        setMaxHealth(right, 300.0D);
        left.setHealth(300.0F);
        right.setHealth(300.0F);
        caster.setYRot(0.0F);
        caster.setYBodyRot(0.0F);
        caster.setYHeadRot(0.0F);
        caster.setXRot(0.0F);
        giveBlade(helper, caster, MistletoeBlade.FIRST_SNOW_NAME);

        MistletoeCombat.beginFrostBlade(caster);
        for (int i = 0; i < MistletoeCombat.FROST_HITS; i++) {
            MistletoeCombat.frostBladeHit(caster, i);
        }

        helper.assertTrue(left.getHealth() < 300.0F, "霜刃没有命中左侧扇形目标");
        helper.assertTrue(right.getHealth() < 300.0F, "霜刃没有命中右侧扇形目标");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void frostBladeStopsAfterBladeRemoved(GameTestHelper helper) {
        Zombie caster = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 1, 2);
        Pig target = helper.spawnWithNoFreeWill(EntityType.PIG, 2, 1, 4);
        setMaxHealth(target, 100.0D);
        target.setHealth(100.0F);
        giveBlade(helper, caster, MistletoeBlade.FIRST_SNOW_NAME);
        MistletoeCombat.beginFrostBlade(caster);

        caster.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        MistletoeCombat.frostBladeHit(caster, 0);

        helper.assertTrue(target.getHealth() >= 100.0F, "换刀后霜刃仍然结算了伤害");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void frostBladeSkipsPetWithOfflineOwner(GameTestHelper helper) {
        Zombie caster = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 1, 2);
        Wolf pet = helper.spawnWithNoFreeWill(EntityType.WOLF, 2, 1, 4);
        pet.setTame(true, true);
        pet.setOwnerUUID(UUID.randomUUID());
        setMaxHealth(pet, 100.0D);
        pet.setHealth(100.0F);
        caster.setYRot(0.0F);
        caster.setYBodyRot(0.0F);
        caster.setYHeadRot(0.0F);
        giveBlade(helper, caster, MistletoeBlade.FIRST_SNOW_NAME);
        MistletoeCombat.beginFrostBlade(caster);

        MistletoeCombat.frostBladeHit(caster, 0);

        helper.assertTrue(pet.getHealth() >= 100.0F, "主人不在世界中的宠物被霜刃误伤");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void legendaryMonstersBossTakesMistletoeSlashDamage(GameTestHelper helper) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(
                ResourceLocation.fromNamespaceAndPath("legendary_monsters", "posessed_paladin")).orElse(null);
        if (type == null) {
            helper.succeed();
            return;
        }
        Zombie caster = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 1, 2);
        var entity = type.create(helper.getLevel());
        if (!(entity instanceof LivingEntity boss)) {
            helper.succeed();
            return;
        }
        boss.moveTo(2.5D, 1.0D, 4.5D, 0.0F, 0.0F);
        if (boss instanceof Mob mob) {
            mob.setNoAi(true);
        }
        float before = boss.getHealth();

        boolean hit = MistletoeDamage.hurt(caster, boss, MistletoeDamage.SLASH, 100.0F);
        float after = boss.getHealth();
        TerminalBlade.LOGGER.info("[GameTest] LM Boss 普通 SA：before={} after={} hit={}", before, after, hit);

        helper.assertTrue(hit, "LM Boss 拒绝了槲寄生 SA 的伤害事件");
        helper.assertTrue(after < before, "LM Boss 吃到 SA 后生命值没有下降");
        boss.discard();
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty", timeoutTicks = 120)
    public static void persistentExecutionKillsTarget(GameTestHelper helper) {
        Zombie caster = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 1, 2);
        Pig target = helper.spawnWithNoFreeWill(EntityType.PIG, 2, 1, 4);
        setMaxHealth(target, 300.0D);

        SuperExecutionChannel.start(caster, target);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(!target.isAlive() || target.isRemoved(),
                    "持续处决没有在 30 tick 内杀死 300 HP 目标");
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void legendaryMonstersBossRejectsFakeForceKill(GameTestHelper helper) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(
                ResourceLocation.fromNamespaceAndPath("legendary_monsters", "posessed_paladin")).orElse(null);
        if (type == null) {
            helper.succeed();
            return;
        }
        Zombie caster = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 1, 2);
        var entity = type.create(helper.getLevel());
        if (!(entity instanceof LivingEntity boss)) {
            helper.succeed();
            return;
        }
        boss.moveTo(2.5D, 1.0D, 4.5D, 0.0F, 0.0F);
        if (boss instanceof Mob mob) {
            mob.setNoAi(true);
        }
        float before = boss.getHealth();

        boolean killed = MistletoeDamage.forceKill(caster, boss, MistletoeDamage.GODSLAYER_ABSOLUTE);

        helper.assertFalse(killed, "LM Boss 的自定义生命值模型被误判为强制处决成功");
        helper.assertTrue(boss.isAlive(), "LM Boss 被伪造死亡，卡进了死亡动画");
        helper.assertTrue(boss.getHealth() > 0.0F, "LM Boss 生命值被 setHealth(0) 假清零");
        helper.assertTrue(before > 0.0F, "LM Boss 生成时生命值异常");
        boss.discard();
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void durabilityIsNotSpentWhenSuperCannotEnter(GameTestHelper helper) {
        // 用僵尸而不是 makeMockServerPlayerInLevel：SlashBlade 在 mock player 上会尝试发
        // motion_broadcast 包，GameTest 服务器没有真实连接，直接抛异常。
        Zombie caster = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 1, 2);
        giveBlade(helper, caster);
        boolean previous = TerminalBladeConfig.SUPER_CONSUME_HALF_DURABILITY.get();
        TerminalBladeConfig.SUPER_CONSUME_HALF_DURABILITY.set(true);
        try {
            ISlashBladeState state = MistletoeBlade.state(caster.getMainHandItem());

            state.updateComboSeq(caster, ModComboStates.SUPER.getId());
            caster.getPersistentData().putLong(MistletoeCombat.SUPER_TOKEN, caster.level().getGameTime() - 100L);
            int before = caster.getMainHandItem().getDamageValue();
            MistletoeCombat.releaseSuperFromInput(caster);
            helper.assertValueEqual(before, caster.getMainHandItem().getDamageValue(),
                    "ComboState 不允许转入 Super 时仍然扣了耐久");

            state.updateComboSeq(caster, ComboStateRegistry.STANDBY.getId());
            caster.getPersistentData().putLong(MistletoeCombat.SUPER_TOKEN, caster.level().getGameTime() - 100L);
            MistletoeCombat.releaseSuperFromInput(caster);
            helper.assertTrue(caster.getMainHandItem().getDamageValue() > before,
                    "合法转入 Super 时没有扣耐久");
        } finally {
            TerminalBladeConfig.SUPER_CONSUME_HALF_DURABILITY.set(previous);
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = TerminalBlade.MOD_ID, template = "empty")
    public static void damageTypeTagsMatchTheConfirmedRules(GameTestHelper helper) {
        var lookup = helper.getLevel().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE);
        var slash = lookup.getOrThrow(MistletoeDamage.SLASH);
        helper.assertFalse(slash.is(DamageTypeTags.BYPASSES_ARMOR), "普通 SA 目前不应绕过护甲");
        helper.assertFalse(slash.is(DamageTypeTags.BYPASSES_EFFECTS), "普通 SA 目前不应绕过状态效果");
        helper.assertFalse(slash.is(DamageTypeTags.BYPASSES_ENCHANTMENTS), "普通 SA 目前不应绕过附魔减伤");
        helper.assertTrue(slash.is(DamageTypeTags.BYPASSES_INVULNERABILITY),
                "普通 SA 必须绕过 Boss 的伤害上限/阶段无敌，否则会被 Cataclysm 一类 Boss 压到 20");
        var godslayer = lookup.getOrThrow(MistletoeDamage.GODSLAYER);
        helper.assertTrue(godslayer.is(DamageTypeTags.BYPASSES_ARMOR), "Super SA 应绕过护甲");
        helper.assertTrue(godslayer.is(DamageTypeTags.BYPASSES_EFFECTS), "Super SA 应绕过状态效果");
        helper.assertTrue(godslayer.is(DamageTypeTags.BYPASSES_ENCHANTMENTS), "Super SA 应绕过附魔");
        helper.assertTrue(godslayer.is(DamageTypeTags.BYPASSES_COOLDOWN), "Super SA 应绕过受伤冷却");
        helper.assertFalse(godslayer.is(DamageTypeTags.BYPASSES_INVULNERABILITY),
                "普通 godslayer 类型仍是非穿透变体，阶段锁血目标用的就是这种保守语义");
        helper.assertTrue(lookup.getOrThrow(MistletoeDamage.GODSLAYER_ABSOLUTE).is(DamageTypeTags.BYPASSES_INVULNERABILITY),
                "0.1.2 起 Super SA 的默认伤害类型 absolute 必须绕过无敌标签");
        var slashTrue = lookup.getOrThrow(MistletoeDamage.SLASH_TRUE);
        helper.assertTrue(slashTrue.is(DamageTypeTags.BYPASSES_ARMOR),
                "强化 SA 的 20% 最大生命真伤段必须绕过护甲");
        helper.assertTrue(slashTrue.is(DamageTypeTags.BYPASSES_EFFECTS),
                "强化 SA 的 20% 最大生命真伤段必须绕过抗性");
        helper.assertTrue(slashTrue.is(DamageTypeTags.BYPASSES_ENCHANTMENTS),
                "强化 SA 的 20% 最大生命真伤段必须绕过保护附魔");
        helper.assertTrue(lookup.getOrThrow(MistletoeDamage.SACRIFICE).is(DamageTypeTags.BYPASSES_ARMOR),
                "自身献祭应绕过护甲");
        helper.succeed();
    }

    private static void prepare(LivingEntity caster, LivingEntity target) {
        double dx = target.getX() - caster.getX();
        double dz = target.getZ() - caster.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        caster.setYRot(yaw);
        caster.setYBodyRot(yaw);
        caster.setYHeadRot(yaw);
        caster.setXRot(0.0F);
    }

    private static void giveBlade(GameTestHelper helper, LivingEntity entity) {
        giveBlade(helper, entity, MistletoeBlade.NAME);
    }

    private static void giveBlade(GameTestHelper helper, LivingEntity entity, ResourceLocation name) {
        entity.setItemSlot(EquipmentSlot.MAINHAND, blade(helper, name));
    }

    private static ItemStack blade(GameTestHelper helper) {
        return blade(helper, MistletoeBlade.NAME);
    }

    private static ItemStack blade(GameTestHelper helper, ResourceLocation name) {
        ResourceKey<SlashBladeDefinition> key = ResourceKey.create(SlashBladeDefinition.REGISTRY_KEY, name);
        return helper.getLevel().registryAccess()
                .lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY)
                .getOrThrow(key)
                .value()
                .getBlade(helper.getLevel().registryAccess());
    }

    private static void setMaxHealth(LivingEntity entity, double health) {
        var attribute = entity.getAttribute(Attributes.MAX_HEALTH);
        if (attribute != null) {
            attribute.setBaseValue(health);
        }
        entity.setHealth((float) health);
    }

    private static void enter(LivingEntity caster, ResourceLocation combo) {
        ISlashBladeState state = MistletoeBlade.state(caster.getMainHandItem());
        state.updateComboSeq(caster, combo);
    }

    private static void fireHit(LivingEntity caster, ResourceLocation combo) {
        ISlashBladeState state = MistletoeBlade.state(caster.getMainHandItem());
        state.setLastActionTime(caster.level().getGameTime() - MistletoeCombat.HIT_TICK);
        caster.getPersistentData().remove(ComboState.LAST_PROCESSED_TICK_KEY);
        ComboState resolved = ComboStateRegistry.REGISTRY.get(combo);
        resolved.tickAction(caster);
    }

    private static String combo(LivingEntity caster) {
        return MistletoeBlade.state(caster.getMainHandItem()).getComboSeq().toString();
    }
}

