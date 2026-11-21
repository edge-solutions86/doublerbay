package io.github.rkaushikdev.doublerbay.manual;

import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.measure.Taper;
import io.github.rkaushikdev.doublerbay.structure.Region;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * What the book says about grinding damage out, which is not the same as what it allows.
 *
 * <p>There are two different questions and they get run together. Is this damage acceptable as it
 * stands, and if not, can it be blended out until it is. The second has its own rules and they do
 * not come off the same row: how gently the blend has to run out, how much of the panel one blend may
 * take, whether a blend is allowed at all in this part of the structure, and what has to be done
 * afterwards if it has gone through the cladding.
 *
 * <p>The one that catches people is the chem mill step. A blend on a step is stacking one stress
 * raiser on another, and the book will not have it at any depth. So the answer to can this be
 * blended is not a number, it is a look at where the damage is sitting before any number is read at
 * all.
 */
public final class Cleanups {

    /**
     * How far below the bottom of the damage a blend goes. A blend cannot stop at the bottom of the
     * damage. The metal right under a scratch has been worked and has to come out with it, and the
     * shop cannot hold a hand tool to a thousandth anyway.
     */
    public static final Length DEFAULT_CUT_BELOW = Length.ofInches("0.002");

    /** Where the book will not let a blend happen whatever the depth is. */
    public static final Set<Region> NO_BLEND_REGIONS =
            Collections.unmodifiableSet(EnumSet.of(Region.CHEM_MILL_STEP, Region.FASTENER_ROW, Region.UNDER_DOUBLER));

    private Cleanups() {}

    /** The V320 cleanup rules, which are the same for every skin chapter. */
    public static CleanupRules standardRules() {
        Taper gentle = new Taper(20);
        Map<DamageKind, CleanupRule> rules = new LinkedHashMap<>();
        rules.put(DamageKind.DENT, CleanupRule.notBlendable(gentle));
        rules.put(DamageKind.SCRATCH, CleanupRule.blendable(gentle, Length.ofInches("0.250"), new BigDecimal("0.25")));
        rules.put(DamageKind.GOUGE, CleanupRule.blendable(gentle, Length.ofInches("0.250"), new BigDecimal("0.25")));
        rules.put(DamageKind.NICK, CleanupRule.blendable(gentle, Length.ofInches("0.125"), new BigDecimal("0.10")));
        rules.put(
                DamageKind.CORROSION,
                CleanupRule.blendable(new Taper(30), Length.ofInches("0.500"), new BigDecimal("0.35")));
        rules.put(
                DamageKind.LIGHTNING_BURN,
                CleanupRule.blendable(gentle, Length.ofInches("0.250"), new BigDecimal("0.20")));
        rules.put(DamageKind.CRACK, CleanupRule.notBlendable(gentle));
        rules.put(DamageKind.HOLE, CleanupRule.notBlendable(gentle));
        rules.put(DamageKind.PUNCTURE, CleanupRule.notBlendable(gentle));
        rules.put(DamageKind.BUCKLE, CleanupRule.notBlendable(gentle));
        rules.put(DamageKind.DISBOND, CleanupRule.notBlendable(gentle));
        return new CleanupRules(rules);
    }
}
