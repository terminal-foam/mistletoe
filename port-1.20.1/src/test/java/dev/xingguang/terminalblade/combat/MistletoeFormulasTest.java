package dev.xingguang.terminalblade.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MistletoeFormulasTest {
    private static final float D0 = 8.0F;
    private static final float K = 0.2F;

    @Test
    void normalHitUsesTargetMaxAndPlayerCurrent() {
        assertEquals(8.0F, MistletoeFormulas.normalDamage(D0, K, 20.0F, 20.0F, true), 1.0E-4F);
        assertEquals(64.0F, MistletoeFormulas.normalDamage(D0, K, 300.0F, 20.0F, true), 1.0E-4F);
    }

    @Test
    void negativeGapClampsAtZeroUnlessDisabled() {
        assertEquals(0.0F, MistletoeFormulas.lifeGap(10.0F, 20.0F, true), 1.0E-4F);
        assertEquals(8.0F, MistletoeFormulas.normalDamage(D0, K, 10.0F, 20.0F, true), 1.0E-4F);
        assertEquals(-10.0F, MistletoeFormulas.lifeGap(10.0F, 20.0F, false), 1.0E-4F);
        assertEquals(6.0F, MistletoeFormulas.normalDamage(D0, K, 10.0F, 20.0F, false), 1.0E-4F);
    }

    @Test
    void slashArtThresholdIsStrictlyAboveOneHundred() {
        assertFalse(MistletoeFormulas.isEnhancedSlashArt(20.0F, 100.0F));
        assertFalse(MistletoeFormulas.isEnhancedSlashArt(100.0F, 100.0F));
        assertTrue(MistletoeFormulas.isEnhancedSlashArt(100.01F, 100.0F));

        float unenhanced = MistletoeFormulas.slashArtDamage(D0, K, 100.0F, 20.0F, true, 100.0F, 0.20F, 1.0F);
        assertEquals(MistletoeFormulas.normalDamage(D0, K, 100.0F, 20.0F, true), unenhanced, 1.0E-4F);
    }

    @Test
    void enhancedSlashArtPaysTenPercentThenAddsTwentyPercentOfTargetMax() {
        float current = 20.0F;
        float cost = MistletoeFormulas.sacrificeCost(current, 0.10F);
        assertEquals(2.0F, cost, 1.0E-4F);
        float after = current - cost;
        float damage = MistletoeFormulas.slashArtDamage(D0, K, 300.0F, after, true, 100.0F, 0.20F, 1.0F);
        float expected = MistletoeFormulas.normalDamage(D0, K, 300.0F, after, true) + 60.0F;
        assertEquals(expected, damage, 1.0E-3F);
        assertEquals(60.0F, 0.20F * 300.0F, 1.0E-4F);
    }

    @Test
    void maxHealthBonusIsSplitOutAsTrueDamage() {
        MistletoeFormulas.SlashArtDamage enhanced = MistletoeFormulas.slashArtDamageSplit(
                D0, K, 300.0F, 18.0F, true, 100.0F, 0.20F, 1.0F);
        assertEquals(64.4F, enhanced.normal(), 1.0E-3F);
        assertEquals(60.0F, enhanced.trueDamage(), 1.0E-3F);
        assertEquals(124.4F, enhanced.total(), 1.0E-3F);
        assertEquals(enhanced.total(),
                MistletoeFormulas.slashArtDamage(D0, K, 300.0F, 18.0F, true, 100.0F, 0.20F, 1.0F),
                1.0E-3F);

        MistletoeFormulas.SlashArtDamage basic = MistletoeFormulas.slashArtDamageSplit(
                D0, K, 100.0F, 20.0F, true, 100.0F, 0.20F, 1.0F);
        assertEquals(24.0F, basic.normal(), 1.0E-3F);
        assertEquals(0.0F, basic.trueDamage(), 1.0E-3F);
        assertEquals(24.0F, basic.total(), 1.0E-3F);
    }

    @Test
    void justSlashArtOnlyAppliesTheConfiguredMultiplier() {
        float base = MistletoeFormulas.slashArtDamage(D0, K, 40.0F, 20.0F, true, 100.0F, 0.20F, 1.0F);
        float unchanged = MistletoeFormulas.slashArtDamage(D0, K, 40.0F, 20.0F, true, 100.0F, 0.20F, 1.0F);
        assertEquals(base, unchanged, 1.0E-4F);
        float just = MistletoeFormulas.slashArtDamage(D0, K, 40.0F, 20.0F, true, 100.0F, 0.20F, 1.5F);
        assertEquals(base * 1.5F, just, 1.0E-3F);
    }

    @Test
    void superSacrificeMatchesTheTwentyHealthCases() {
        MistletoeFormulas.SuperSacrifice survived = MistletoeFormulas.superSacrifice(20.0F, 20.0F, 0.90F);
        assertEquals(18.0F, survived.cost(), 1.0E-4F);
        assertEquals(2.0F, survived.remainingHealth(), 1.0E-4F);
        assertFalse(survived.lethal());

        MistletoeFormulas.SuperSacrifice dying = MistletoeFormulas.superSacrifice(10.0F, 20.0F, 0.90F);
        assertEquals(18.0F, dying.cost(), 1.0E-4F);
        assertTrue(dying.lethal());
        assertEquals(0.0F, dying.remainingHealth(), 1.0E-4F);

        MistletoeFormulas.SuperSacrifice exact = MistletoeFormulas.superSacrifice(18.0F, 20.0F, 0.90F);
        assertTrue(exact.lethal());
    }

    @Test
    void sphereIncludesTheBoundaryAndExcludesTheOutside() {
        assertTrue(MistletoeFormulas.withinSphere(8.0D, 0.0D, 0.0D, 8.0D));
        assertFalse(MistletoeFormulas.withinSphere(8.05D, 0.0D, 0.0D, 8.0D));
        assertTrue(MistletoeFormulas.withinSphere(0.0D, 0.0D, 0.0D, 8.0D));
        double inside = Math.sqrt(3.0D) * 4.0D;
        assertTrue(MistletoeFormulas.withinSphere(4.0D, 4.0D, 4.0D, inside));
        assertFalse(MistletoeFormulas.withinSphere(4.0D, 4.0D, 4.0D, inside - 0.1D));
    }

    @Test
    void axisDistanceUsesTheNearestPointOnABox() {
        assertEquals(0.0D, MistletoeFormulas.axisDistance(1.0D, 0.0D, 2.0D), 1.0E-9D);
        assertEquals(2.0D, MistletoeFormulas.axisDistance(-2.0D, 0.0D, 2.0D), 1.0E-9D);
        assertEquals(3.0D, MistletoeFormulas.axisDistance(5.0D, 0.0D, 2.0D), 1.0E-9D);
    }
}
