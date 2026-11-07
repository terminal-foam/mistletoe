package dev.xingguang.terminalblade.combat;

import dev.xingguang.terminalblade.TerminalBlade;
import dev.xingguang.terminalblade.TerminalBladeConfig;
import dev.xingguang.terminalblade.blade.MistletoeBlade;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class MistletoeTargets {
    public static final TagKey<EntityType<?>> SUPER_SA_INCLUDE = TagKey.create(Registries.ENTITY_TYPE, TerminalBlade.id("super_sa_include"));
    public static final TagKey<EntityType<?>> SUPER_SA_EXCLUDE = TagKey.create(Registries.ENTITY_TYPE, TerminalBlade.id("super_sa_exclude"));

    private MistletoeTargets() {
    }

    public static LivingEntity selectSlashArtTarget(LivingEntity user, double range) {
        Entity locked = MistletoeBlade.stateOptional(user.getMainHandItem())
                .map(state -> state.getTargetEntity(user.level()))
                .orElse(null);
        if (locked instanceof LivingEntity living && acceptsSlashArt(user, living, range * 1.25D, false)) {
            return living;
        }

        AABB box = user.getBoundingBox().inflate(range);
        return user.level().getEntitiesOfClass(LivingEntity.class, box, entity -> acceptsSlashArt(user, entity, range, true))
                .stream()
                .min(Comparator.comparingDouble(user::distanceToSqr))
                .orElse(null);
    }

    public static List<LivingEntity> selectSuperTargets(LivingEntity user, double radius) {
        Vec3 center = center(user);
        AABB box = new AABB(center, center).inflate(radius);
        List<LivingEntity> found = new ArrayList<>();
        for (LivingEntity entity : user.level().getEntitiesOfClass(LivingEntity.class, box, entity -> acceptsSuper(user, entity))) {
            if (intersectsSphere(center, entity.getBoundingBox(), radius)) {
                found.add(entity);
            }
        }
        return found;
    }

    public static boolean intersectsSphere(Vec3 center, AABB box, double radius) {
        double dx = MistletoeFormulas.axisDistance(center.x, box.minX, box.maxX);
        double dy = MistletoeFormulas.axisDistance(center.y, box.minY, box.maxY);
        double dz = MistletoeFormulas.axisDistance(center.z, box.minZ, box.maxZ);
        return MistletoeFormulas.withinSphere(dx, dy, dz, radius);
    }

    public static Vec3 center(LivingEntity entity) {
        return entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
    }

    private static boolean acceptsSlashArt(LivingEntity user, LivingEntity target, double range, boolean requireFront) {
        if (!isStrikeable(user, target) || user.distanceTo(target) > range) {
            return false;
        }
        if (!requireFront) {
            return true;
        }
        Vec3 look = user.getLookAngle();
        Vec3 to = target.getEyePosition().subtract(user.getEyePosition());
        double length = to.length();
        if (length < 1.0E-4D) {
            return true;
        }
        return look.dot(to.scale(1.0D / length)) >= 0.20D;
    }

    private static boolean acceptsSuper(LivingEntity user, LivingEntity target) {
        if (!isStrikeable(user, target) || target.getType().is(SUPER_SA_EXCLUDE)) {
            return false;
        }
        if (target instanceof Player player) {
            if (player.isSpectator() || player.isCreative() || !TerminalBladeConfig.superAffectPlayers()) {
                return false;
            }
            return !(user instanceof Player caster) || caster.getTeam() == null || caster.getTeam() != player.getTeam();
        }
        if (target instanceof OwnableEntity ownable && ownable.getOwner() instanceof Player) {
            return TerminalBladeConfig.superAffectTamed();
        }
        if (target.getType().is(SUPER_SA_INCLUDE)) {
            return true;
        }
        return target instanceof Enemy || TerminalBladeConfig.superAffectPassive();
    }

    private static boolean isStrikeable(LivingEntity user, LivingEntity target) {
        return target != user
                && target.isAlive()
                && !target.isSpectator()
                && !(target instanceof ArmorStand)
                && !target.isPassengerOfSameVehicle(user)
                && MistletoeBlade.isMistletoe(user);
    }
}
