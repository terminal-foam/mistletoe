package dev.xingguang.terminalblade.combat;

/**
 * 槲寄生伤害与献祭的纯计算。
 * 生命值一律使用 Minecraft 内部 HP（2 HP = 1 颗红心）。
 * 待定项按设计文档的建议默认处理，并全部可以由配置覆盖调用方传入的参数。
 */
public final class MistletoeFormulas {
    private MistletoeFormulas() {
    }

    public record SuperSacrifice(float cost, float remainingHealth, boolean lethal) {
    }

    public static float lifeGap(float targetMaxHealth, float playerCurrentHealth, boolean clampNegative) {
        float gap = targetMaxHealth - playerCurrentHealth;
        if (clampNegative && gap < 0.0F) {
            return 0.0F;
        }
        return gap;
    }

    /**
     * D = D0 + k × (目标最大生命 − 玩家当前生命)。
     */
    public static float normalDamage(float baseDamage, float gapCoefficient, float targetMaxHealth,
                                      float playerCurrentHealth, boolean clampNegative) {
        return baseDamage + gapCoefficient * lifeGap(targetMaxHealth, playerCurrentHealth, clampNegative);
    }

    /**
     * 普通 SA 的强化阈值是严格大于。100 HP 本身仍走普通 SA。
     */
    public static boolean isEnhancedSlashArt(float targetMaxHealth, float threshold) {
        return targetMaxHealth > threshold;
    }

    public static float sacrificeCost(float pool, float ratio) {
        if (pool <= 0.0F || ratio <= 0.0F) {
            return 0.0F;
        }
        return (float) ((double) pool * (double) ratio);
    }

    /**
     * 强化 SA：D = D0 + k × (目标最大生命 − 献祭后玩家当前生命) + 额外比例 × 目标最大生命。
     * 未强化时退回普通攻击公式，不附加最大生命伤害。
     */
    public static float slashArtDamage(float baseDamage, float gapCoefficient, float targetMaxHealth,
                                        float playerHealthForGap, boolean clampNegative, float threshold,
                                        float maxHealthBonusRatio, float justMultiplier) {
        float damage = normalDamage(baseDamage, gapCoefficient, targetMaxHealth, playerHealthForGap, clampNegative);
        if (isEnhancedSlashArt(targetMaxHealth, threshold)) {
            damage += maxHealthBonusRatio * targetMaxHealth;
        }
        return damage * justMultiplier;
    }

    /**
     * 献祭量按最大生命计算。当前生命大于献祭量时存活并留下差额；
     * 小于或等于献祭量时，攻击仍可先结算，随后施法者死亡。
     */
    public static SuperSacrifice superSacrifice(float currentHealth, float maxHealth, float ratio) {
        float cost = sacrificeCost(maxHealth, ratio);
        if (currentHealth > cost) {
            return new SuperSacrifice(cost, currentHealth - cost, false);
        }
        return new SuperSacrifice(cost, 0.0F, true);
    }

    /**
     * 球形判定：到目标碰撞箱最近点的距离，而不是立方体 AABB 本身。
     */
    public static boolean withinSphere(double dx, double dy, double dz, double radius) {
        if (radius < 0.0D || Double.isNaN(radius)) {
            return false;
        }
        return dx * dx + dy * dy + dz * dz <= radius * radius + 1.0E-4D;
    }

    public static double axisDistance(double point, double min, double max) {
        if (point < min) {
            return min - point;
        }
        if (point > max) {
            return point - max;
        }
        return 0.0D;
    }
}
