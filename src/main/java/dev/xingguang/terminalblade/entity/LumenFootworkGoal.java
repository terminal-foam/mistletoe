package dev.xingguang.terminalblade.entity;

import java.util.EnumSet;
import java.util.function.BooleanSupplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

/** Occasional short sidestep during neutral combat, without stealing the SA/cast state. */
public final class LumenFootworkGoal extends Goal {
    private final Mob mob;
    private final BooleanSupplier busy;
    private int nextAttempt;
    private int stepsLeft;
    private float sideways;

    public LumenFootworkGoal(Mob mob, BooleanSupplier busy) {
        this.mob = mob;
        this.busy = busy;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (nextAttempt-- > 0) {
            return false;
        }
        nextAttempt = 45 + mob.getRandom().nextInt(35);
        LivingEntity target = mob.getTarget();
        if (busy.getAsBoolean() || target == null || !target.isAlive() || !mob.hasLineOfSight(target)) {
            return false;
        }
        double distanceSq = mob.distanceToSqr(target);
        return distanceSq > 2.5D * 2.5D && distanceSq < 8.0D * 8.0D;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = mob.getTarget();
        return stepsLeft > 0 && !busy.getAsBoolean() && target != null && target.isAlive();
    }

    @Override
    public void start() {
        stepsLeft = 6 + mob.getRandom().nextInt(5);
        sideways = mob.getRandom().nextBoolean() ? 0.65F : -0.65F;
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target != null) {
            mob.getLookControl().setLookAt(target, 90.0F, 90.0F);
            mob.getMoveControl().strafe(0.1F, sideways);
        }
        stepsLeft--;
    }

    @Override
    public void stop() {
        stepsLeft = 0;
    }
}
