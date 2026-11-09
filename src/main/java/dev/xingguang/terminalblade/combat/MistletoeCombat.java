package dev.xingguang.terminalblade.combat;

import dev.xingguang.terminalblade.TerminalBlade;
import dev.xingguang.terminalblade.TerminalBladeConfig;
import dev.xingguang.terminalblade.blade.MistletoeBlade;
import dev.xingguang.terminalblade.combat.execution.SuperExecutionChannel;
import dev.xingguang.terminalblade.combat.execution.SuperSaExecutionService;
import dev.xingguang.terminalblade.registry.ModComboStates;
import dev.xingguang.terminalblade.registry.ModSounds;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.util.AttackManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class MistletoeCombat {
    /**
     * ComboState 时间轴用的是进入状态后的游戏 tick，不是 VMD 帧。
     * 6 tick 大约对应 9 帧，落在当前 49 帧以上的技能窗口内部。
     */
    public static final int HIT_TICK = 6;
    public static final long CAST_COOLDOWN_TICKS = 8L;
    /** 熹微：固定伤害、固定恢复时长，不参与暮色的生命差/最大生命公式。 */
    public static final float XIWEI_DAMAGE = 12.0F;
    public static final int XIWEI_REGEN_TICKS = 100;
    /** 霜刃：7 段高速斩击，固定 tick 间隔。 */
    public static final int FROST_HITS = 7;
    public static final int FROST_FIRST_TICK = 4;
    public static final int FROST_INTERVAL = 2;
    public static final double FROST_RANGE = 6.0D;
    public static final double FROST_CONE_DEGREES = 100.0D;

    private static final String SA_TOKEN = "mistletoe:sa_token";
    private static final String SA_ARMED = "mistletoe:sa_armed";
    private static final String SA_JUST = "mistletoe:sa_just";
    private static final String SA_COMBO = "mistletoe:sa_combo";
    public static final String SUPER_TOKEN = "mistletoe:super_token";
    private static final String SUPER_ARMED = "mistletoe:super_armed";
    private static final String SUPER_COMBO = "mistletoe:super_combo";
    private static final String FROST_TOKEN = "mistletoe:frost_token";
    private static final String FROST_ARMED = "mistletoe:frost_armed";
    private static final String FROST_COMBO = "mistletoe:frost_combo";
    private static final String FROST_ACTIVE = "mistletoe:frost_active";
    private static final String FROST_DAMAGE = "mistletoe:frost_damage";

    private MistletoeCombat() {
    }

    public static void beginSlashArt(LivingEntity user, boolean just) {
        if (user.level().isClientSide() || !user.isAlive() || !canFight(user)) {
            disarm(user, SA_ARMED);
            return;
        }
        if (!arm(user, SA_TOKEN, SA_ARMED)) {
            return;
        }
        CompoundTag tag = user.getPersistentData();
        tag.putBoolean(SA_JUST, just);
        tag.putString(SA_COMBO, comboId(user));
        if (user.level() instanceof ServerLevel level) {
            Vec3 eye = user.getEyePosition();
            MistletoeDamage.burst(level, eye.x, eye.y, eye.z, 6, 0.15D);
        }
        log(user, "enter", just ? "just_sa" : "sa", false);
    }

    public static void resolveSlashArt(LivingEntity user) {
        if (user.level().isClientSide() || !claim(user, SA_ARMED)) {
            return;
        }
        boolean just = user.getPersistentData().getBoolean(SA_JUST);
        String expected = user.getPersistentData().getString(SA_COMBO);
        log(user, "hit", just ? "just_sa" : "sa", false);
        if (!user.isAlive() || !canFight(user) || !expected.equals(comboId(user))) {
            log(user, "abort", just ? "just_sa" : "sa", false);
            return;
        }
        ServerLevel level = (ServerLevel) user.level();
        spawnSlash(user, just ? -18.0F : -30.0F, just);
        spawnSlash(user, just ? 24.0F : 35.0F, just);
        Vec3 eye = user.getEyePosition();
        MistletoeDamage.burst(level, eye.x, eye.y, eye.z, just ? 16 : 10, 0.35D);

        LivingEntity target = MistletoeTargets.selectSlashArtTarget(user, TerminalBladeConfig.slashArtRange());
        if (target == null) {
            log(user, "whiff", just ? "just_sa" : "sa", false);
            return;
        }

        if (MistletoeBlade.isDawn(user.getMainHandItem())) {
            float healthBefore = target.getHealth();
            MistletoeDamage.hurt(user, target, MistletoeDamage.SLASH, XIWEI_DAMAGE);
            boolean connected = !target.isAlive() || target.getHealth() < healthBefore;
            if (connected) {
                user.addEffect(new MobEffectInstance(MobEffects.REGENERATION, XIWEI_REGEN_TICKS, 0, false, true, true));
                MistletoeDamage.burst(level, target.getX(), target.getY(0.6D), target.getZ(), 8, 0.25D);
            }
            if (TerminalBladeConfig.logDamage()) {
                TerminalBlade.LOGGER.info("[槲寄生] 普通 SA art=xiwei 固定伤害={} 命中={} 恢复={}tick",
                        XIWEI_DAMAGE, connected, connected ? XIWEI_REGEN_TICKS : 0);
            }
            return;
        }

        float targetMax = target.getMaxHealth();
        boolean enhanced = MistletoeFormulas.isEnhancedSlashArt(targetMax, TerminalBladeConfig.slashArtThreshold());
        boolean payHealth = paysHealthCost(user);
        float current = user.getHealth();
        float cost = enhanced && payHealth
                ? MistletoeFormulas.sacrificeCost(current, TerminalBladeConfig.slashArtSacrificeRatio())
                : 0.0F;
        float healthForGap = enhanced ? Math.max(0.0F, current - cost) : current;
        float multiplier = just ? TerminalBladeConfig.justMultiplier() : 1.0F;
        MistletoeFormulas.SlashArtDamage split = MistletoeFormulas.slashArtDamageSplit(
                TerminalBladeConfig.baseDamage(),
                TerminalBladeConfig.gapCoefficient(),
                targetMax,
                healthForGap,
                TerminalBladeConfig.clampNegativeGap(),
                TerminalBladeConfig.slashArtThreshold(),
                TerminalBladeConfig.slashArtMaxHealthBonus(),
                multiplier);

        if (enhanced && payHealth && TerminalBladeConfig.slashArtPayOnWhiff()) {
            paySlashArt(user, level, cost);
            log(user, "sacrifice", just ? "just_sa" : "sa", false);
        }
        boolean connected = MistletoeDamage.hurt(user, target, MistletoeDamage.SLASH, split.normal());
        boolean trueConnected = false;
        // 普通段命中后才追加真伤段：保留“SA 打中才结算”的旧语义，
        // 避免普通段被取消/挡下时，20% 真伤单独漏出去。
        if (connected && enhanced && split.trueDamage() > 0.0F && target.isAlive()) {
            trueConnected = MistletoeDamage.hurt(user, target, MistletoeDamage.SLASH_TRUE, split.trueDamage());
        }
        if (enhanced && payHealth && connected && !TerminalBladeConfig.slashArtPayOnWhiff() && user.isAlive()) {
            paySlashArt(user, level, cost);
            // 普通 SA 的献祭固定是当前生命的 10%，不会致死；true 只属于 Super SA。
            log(user, "sacrifice", just ? "just_sa" : "sa", false);
        }
        if (connected) {
            MistletoeDamage.burst(level, target.getX(), target.getY(0.6D), target.getZ(), enhanced ? 18 : 8, 0.25D);
        }
        if (TerminalBladeConfig.logDamage()) {
            TerminalBlade.LOGGER.info(
                    "[槲寄生] 普通 SA just={} 强化={} 目标最大生命={} 献祭={} 命中={} 普通段={} 真伤段={} 真伤命中={} 公式伤害={}",
                    just, enhanced, targetMax, enhanced && (connected || TerminalBladeConfig.slashArtPayOnWhiff()) ? cost : 0.0F,
                    connected, split.normal(), split.trueDamage(), trueConnected, split.total());
        }
    }

    public static void beginFrostBlade(LivingEntity user) {
        if (user.level().isClientSide() || !user.isAlive() || !MistletoeBlade.isFirstSnow(user)
                || MistletoeBlade.isBrokenOrSealed(user.getMainHandItem())) {
            abortFrostBlade(user);
            return;
        }
        if (!arm(user, FROST_TOKEN, FROST_ARMED)) {
            return;
        }
        CompoundTag tag = user.getPersistentData();
        tag.putString(FROST_COMBO, comboId(user));
        tag.putBoolean(FROST_ACTIVE, false);
        // 七刀共用起手时的攻击力快照，避免中途属性变化导致七段不同伤。
        tag.putFloat(FROST_DAMAGE, (float) user.getAttributeValue(Attributes.ATTACK_DAMAGE));
        if (user.level() instanceof ServerLevel level) {
            MistletoeDamage.burst(level, user.getX(), user.getY(0.8D), user.getZ(), 8, 0.2D);
        }
        log(user, "enter", "frost_blade", false);
    }

    /**
     * 霜刃的单段斩击。第 0 段负责认领本次施法，之后每 2 tick 追加一段；
     * 伤害走起手快照的普通攻击属性，不套暮色的生命差/真伤公式。
     */
    public static void frostBladeHit(LivingEntity user, int index) {
        if (user.level().isClientSide() || index < 0 || index >= FROST_HITS
                || !user.isAlive() || !MistletoeBlade.isFirstSnow(user)
                || MistletoeBlade.isBrokenOrSealed(user.getMainHandItem())) {
            abortFrostBlade(user);
            return;
        }
        CompoundTag tag = user.getPersistentData();
        if (index == 0) {
            if (!claim(user, FROST_ARMED)) {
                return;
            }
            // 不覆盖起手时保存的连段 ID；换刀/切连段后旧回调必须失效。
            tag.putBoolean(FROST_ACTIVE, true);
        } else if (!tag.getBoolean(FROST_ACTIVE)) {
            return;
        }
        String expected = tag.getString(FROST_COMBO);
        if (expected.isEmpty() || !expected.equals(comboId(user))) {
            abortFrostBlade(user);
            return;
        }
        if (!tag.contains(FROST_DAMAGE)) {
            abortFrostBlade(user);
            return;
        }

        ServerLevel level = (ServerLevel) user.level();
        spawnSlash(user, -35.0F + index * 12.0F, true, 0xA9D8FF);
        MistletoeDamage.burst(level, user.getX(), user.getY(0.8D), user.getZ(), 5, 0.2D);

        List<LivingEntity> targets = MistletoeTargets.selectFrostBladeTargets(
                user, FROST_RANGE, FROST_CONE_DEGREES);
        if (!targets.isEmpty()) {
            float damage = Math.max(1.0F, tag.getFloat(FROST_DAMAGE));
            DamageSource source = MistletoeDamage.source(user, MistletoeDamage.FROST_BLADE);
            for (LivingEntity target : targets) {
                // 不重置 invulnerableTime；霜刃伤害类型只带 bypasses_cooldown，
                // 因此能连续命中，但不绕过实体无敌、阶段锁或护甲。
                target.hurt(source, damage);
            }
        }
        level.playSound(null, user.getX(), user.getY(), user.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.55F, 1.35F + index * 0.03F);

        if (index == FROST_HITS - 1) {
            abortFrostBlade(user);
        }
        log(user, "hit", "frost_blade", false);
    }

    private static void abortFrostBlade(LivingEntity user) {
        CompoundTag tag = user.getPersistentData();
        tag.putBoolean(FROST_ACTIVE, false);
        tag.remove(FROST_COMBO);
        tag.remove(FROST_DAMAGE);
        disarm(user, FROST_ARMED);
    }

    public static void beginSuper(LivingEntity user) {
        if (user.level().isClientSide() || !user.isAlive() || !canFight(user)) {
            disarm(user, SUPER_ARMED);
            return;
        }
        if (!arm(user, SUPER_TOKEN, SUPER_ARMED)) {
            return;
        }
        user.getPersistentData().putString(SUPER_COMBO, comboId(user));
        if (user.level() instanceof ServerLevel level) {
            MistletoeDamage.burst(level, user.getX(), user.getY(0.8D), user.getZ(), 8, 0.2D);
        }
        log(user, "enter", "super", false);
    }

    public static void resolveSuper(LivingEntity user) {
        if (user.level().isClientSide() || !claim(user, SUPER_ARMED)) {
            return;
        }
        String expected = user.getPersistentData().getString(SUPER_COMBO);
        log(user, "hit", "super", false);
        if (!user.isAlive() || !canFight(user) || !expected.equals(comboId(user))) {
            log(user, "abort", "super", false);
            return;
        }
        ServerLevel level = (ServerLevel) user.level();
        double radius = TerminalBladeConfig.superRadius();
        for (int i = 0; i < 8; i++) {
            spawnSlash(user, i * 22.5F, true);
        }
        Vec3 center = MistletoeTargets.center(user);
        MistletoeDamage.burst(level, center.x, center.y, center.z, 48, radius * 0.35D);
        for (int i = 0; i < 24; i++) {
            double angle = i / 24.0D * Math.PI * 2.0D;
            level.sendParticles(dev.xingguang.terminalblade.registry.ModParticles.SPARK.get(),
                    center.x + Math.cos(angle) * radius,
                    center.y,
                    center.z + Math.sin(angle) * radius,
                    2, 0.05D, 0.2D, 0.05D, 0.01D);
        }

        List<LivingEntity> targets = MistletoeTargets.selectSuperTargets(user, radius);
        int killed = 0;
        int phaseLocked = 0;
        int failed = 0;
        for (LivingEntity target : targets) {
            SuperSaExecutionService.Result result = SuperSaExecutionService.execute(user, target);
            switch (result) {
                case KILLED_BY_DAMAGE, KILLED_BY_FORCE -> killed++;
                case PHASE_LOCKED -> phaseLocked++;
                case FORCE_FAILED -> {
                    failed++;
                    TerminalBlade.LOGGER.warn("[槲寄生] Super SA 没有完成处决：{} ({})",
                            target.getName().getString(),
                            net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()));
                    // 有限伤/阶段窗口/自定义生命值 Boss：不伪造死亡，改为有时限地重复绝对伤害。
                    if (target.isAlive() && !(target instanceof Player)) {
                        SuperExecutionChannel.start(user, target);
                    }
                }
                case SKIPPED -> {
                    // 目标已经不在/同一施法者；不计入失败。
                }
            }
        }

        if (user.isAlive()) {
            MistletoeFormulas.SuperSacrifice sacrifice = applySuperSacrifice(user);
            if (TerminalBladeConfig.logDamage()) {
                TerminalBlade.LOGGER.info(
                        "[槲寄生] Super SA 半径={} 目标={} 击杀={} 阶段锁={} 失败={} 献祭={} 致死={} 支付生命={}",
                        radius, targets.size(), killed, phaseLocked, failed, sacrifice.cost(), sacrifice.lethal(),
                        paysHealthCost(user));
            }
        }
    }

    /**
     * Super SA 的自身献祭结算。创造/旁观玩家不扣血、不播献祭音效；
     * 单独抽出是为了让 GameTest 不依赖 ComboState 和斩击实体就能验证这条分支。
     */
    public static MistletoeFormulas.SuperSacrifice applySuperSacrifice(LivingEntity user) {
        boolean payHealth = paysHealthCost(user);
        MistletoeFormulas.SuperSacrifice sacrifice = payHealth
                ? MistletoeFormulas.superSacrifice(
                        user.getHealth(), user.getMaxHealth(), TerminalBladeConfig.superSacrificeRatio())
                : new MistletoeFormulas.SuperSacrifice(0.0F, user.getHealth(), false);
        log(user, "sacrifice", "super", sacrifice.lethal());
        if (!payHealth) {
            return sacrifice;
        }
        play(user, ModSounds.SACRIFICE.get(), 1.0F, 0.7F);
        if (sacrifice.lethal()) {
            MistletoeDamage.finish(user, MistletoeDamage.SACRIFICE);
        } else {
            user.setHealth(sacrifice.remainingHealth());
            user.hurtTime = 10;
            user.hurtDuration = 10;
            user.level().broadcastEntityEvent(user, (byte) 2);
        }
        return sacrifice;
    }

    public static boolean releaseSuperFromInput(ServerPlayer player) {
        return releaseSuperFromInput((LivingEntity) player);
    }

    /**
     * 与 {@link #releaseSuperFromInput(ServerPlayer)} 相同的入口，供 GameTest 用非玩家施法者验证
     * ComboState / 耐久顺序；真人输入仍走 ServerPlayer 重载。
     */
    public static boolean releaseSuperFromInput(LivingEntity player) {
        ItemStack stack = player.getMainHandItem();
        if (!MistletoeBlade.isMistletoe(stack)) {
            return false;
        }
        // 朝露没有 Super；它的强度只来自熹微的固定伤害与恢复。
        if (MistletoeBlade.isDawn(stack)) {
            return false;
        }
        if (!canFight(player)) {
            return true;
        }
        if (TerminalBladeConfig.superRequiresGround() && !player.onGround()) {
            return true;
        }

        // M1-04：ComboState 转换和冷却都必须在扣耐久之前确认，
        // 否则“技能没放出来但耐久已经扣了”，重复输入还会重复扣。
        if (isSuperCoolingDown(player)) {
            return true;
        }
        if (!canEnterSuper(player)) {
            return true;
        }
        if (TerminalBladeConfig.superConsumeHalfDurability() && !applyHalfDurabilityCost(player)) {
            return true;
        }
        // 扣耐久可能触发物品损坏/ComboState 变化；切换前再确认一次，避免扣完才发现进不去。
        if (!canEnterSuper(player)) {
            return true;
        }

        ISlashBladeState state = MistletoeBlade.state(player.getMainHandItem());
        state.updateComboSeq(player, ModComboStates.SUPER.getId());
        return true;
    }

    /**
     * 只检查能否转入 SUPER，不写状态。确保扣耐久之前就能拒绝非法转换。
     */
    private static boolean canEnterSuper(LivingEntity player) {
        if (!MistletoeBlade.isMistletoe(player.getMainHandItem())) {
            return false;
        }
        if (MistletoeBlade.isDawn(player.getMainHandItem())) {
            return false;
        }
        ISlashBladeState state = MistletoeBlade.state(player.getMainHandItem());
        var currentLoc = state.resolvCurrentComboStateTicks(player);
        ComboState current = ComboStateRegistry.REGISTRY.get(currentLoc.getValue());
        var destination = ModComboStates.SUPER.getId();
        ComboState next = ComboStateRegistry.REGISTRY.get(destination);
        return current != null && next != null
                && !currentLoc.getValue().equals(destination)
                && current.getPriority() > next.getPriority();
    }

    private static boolean isSuperCoolingDown(LivingEntity user) {
        var tag = user.getPersistentData();
        long now = user.level().getGameTime();
        return MistletoeCastGate.isCoolingDown(
                tag.contains(SUPER_TOKEN), tag.getLong(SUPER_TOKEN), now, CAST_COOLDOWN_TICKS);
    }

    /**
     * 扣除一半最大耐久后，必须重新确认刀还能战斗。损坏或封印时返回 false。
     */
    public static boolean applyHalfDurabilityCost(LivingEntity user) {
        ItemStack stack = user.getMainHandItem();
        if (stack.isEmpty()) {
            return false;
        }
        stack.hurtAndBreak(Math.max(1, stack.getMaxDamage() / 2), user, EquipmentSlot.MAINHAND);
        return canFight(user);
    }

    private static void paySlashArt(LivingEntity user, ServerLevel level, float cost) {
        MistletoeDamage.drainHealth(user, cost);
        play(user, ModSounds.SACRIFICE.get(), 0.6F, 1.2F);
        MistletoeDamage.burst(level, user.getX(), user.getY(0.8D), user.getZ(), 8, 0.2D);
    }

    private static void spawnSlash(LivingEntity user, float roll, boolean critical) {
        spawnSlash(user, roll, critical, 0xF4FFF6);
    }

    private static void spawnSlash(LivingEntity user, float roll, boolean critical, int color) {
        var slash = AttackManager.doSlash(user, roll, true, critical, 0.0D);
        if (slash != null) {
            slash.setColor(color);
            slash.setBaseSize(critical ? 2.4F : 1.8F);
            slash.setLifetime(8);
        }
    }

    /**
     * 创造/旁观玩家不支付生命献祭。原逻辑直接 setHealth / finish，会把创造玩家扣到 0 血，
     * 第二次 Super SA 就会真的死亡；这里统一给普通 SA 和 Super SA 使用。
     */
    public static boolean paysHealthCost(LivingEntity user) {
        return !(user instanceof Player player) || (!player.isCreative() && !player.isSpectator());
    }

    private static boolean canFight(LivingEntity user) {
        return MistletoeBlade.isMistletoe(user) && !MistletoeBlade.isBrokenOrSealed(user.getMainHandItem());
    }

    private static boolean arm(LivingEntity user, String tokenKey, String armedKey) {
        CompoundTag tag = user.getPersistentData();
        long now = user.level().getGameTime();
        if (MistletoeCastGate.isCoolingDown(tag.contains(tokenKey), tag.getLong(tokenKey), now, CAST_COOLDOWN_TICKS)) {
            tag.putBoolean(armedKey, false);
            return false;
        }
        tag.putLong(tokenKey, now);
        tag.putBoolean(armedKey, true);
        return true;
    }

    private static boolean claim(LivingEntity user, String armedKey) {
        CompoundTag tag = user.getPersistentData();
        if (!tag.getBoolean(armedKey)) {
            return false;
        }
        tag.putBoolean(armedKey, false);
        return true;
    }

    private static void disarm(LivingEntity user, String armedKey) {
        user.getPersistentData().putBoolean(armedKey, false);
    }

    private static String comboId(LivingEntity user) {
        ItemStack stack = user.getMainHandItem();
        if (!MistletoeBlade.isMistletoe(stack) && !MistletoeBlade.isFirstSnow(stack)) {
            return "";
        }
        return MistletoeBlade.state(stack).getComboSeq().toString();
    }

    private static void log(LivingEntity user, String phase, String art, boolean lethal) {
        if (!TerminalBladeConfig.logDamage()) {
            return;
        }
        long elapsed = mods.flammpfeil.slashblade.registry.combo.ComboState.getElapsed(user);
        TerminalBlade.LOGGER.info("[槲寄生] phase={} art={} combo={} elapsedTick={} lethal={}",
                phase, art, comboId(user), elapsed, lethal);
    }

    private static void play(LivingEntity user, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        user.level().playSound(null, user.getX(), user.getY(), user.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }
}
