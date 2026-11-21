package io.github.rkaushikdev.doublerbay.damage;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.util.Literal;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The kinds the report form offers, which is the same list the manual is filed under.
 *
 * <p>The kind is not a label, it is the thing that decides which numbers the report has to carry
 * and which table the assessor reads. A dent needs a depth and a diameter and nothing else. A crack
 * needs a length and a bearing and a note of whether it has broken through. Corrosion needs an
 * area. Asking for a diameter on a crack gets a number nobody can use, and reading a dent table for
 * a crack gets an answer that is wrong in the dangerous direction, because dents have allowable
 * limits and cracks do not.
 *
 * <p>Each kind also carries the causes that could plausibly have produced it. A report pairing a
 * kind with an origin outside those is either a misread form or something worth a phone call.
 *
 * <p>The section is where the limits are filed inside the allowable damage chapter. Deformation,
 * material loss and cracking are three different arguments and the manual keeps them apart.
 */
public enum DamageKind {
    DENT("dent", 10, DamageOrigin.IMPACT, DamageOrigin.HANDLING),
    SCRATCH("scratch", 20, DamageOrigin.IMPACT, DamageOrigin.HANDLING),
    GOUGE("gouge", 20, DamageOrigin.IMPACT, DamageOrigin.HANDLING),
    NICK("nick", 20, DamageOrigin.IMPACT, DamageOrigin.HANDLING),
    CRACK("crack", 40, DamageOrigin.FATIGUE, DamageOrigin.IMPACT, DamageOrigin.MANUFACTURING),
    CORROSION("corrosion", 30, DamageOrigin.ENVIRONMENT),
    LIGHTNING_BURN("lightning burn", 30, DamageOrigin.LIGHTNING),
    HOLE("hole", 40, DamageOrigin.IMPACT, DamageOrigin.HANDLING),
    PUNCTURE("puncture", 40, DamageOrigin.IMPACT, DamageOrigin.HANDLING),
    BUCKLE("buckle", 10, DamageOrigin.IMPACT, DamageOrigin.MANUFACTURING),
    DISBOND("disbond", 50, DamageOrigin.ENVIRONMENT, DamageOrigin.MANUFACTURING, DamageOrigin.IMPACT);

    private final String value;
    private final int section;
    private final List<DamageOrigin> plausible;

    DamageKind(String value, int section, DamageOrigin... plausible) {
        this.value = value;
        this.section = section;
        this.plausible = List.of(plausible);
    }

    /** The word the report form uses. */
    public String value() {
        return value;
    }

    /**
     * The kind the report form names with this word.
     *
     * @throws IllegalArgumentException when no kind is
     */
    public static DamageKind fromValue(String value) {
        for (DamageKind kind : values()) {
            if (kind.value.equals(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException(Literal.of(value) + " is not a valid DamageKind");
    }

    /** Metal moved but not removed. */
    public boolean isDeformation() {
        return this == DENT || this == BUCKLE;
    }

    /** Metal gone, which is what a residual thickness is worked out from. */
    public boolean isMaterialLoss() {
        return EnumSet.of(SCRATCH, GOUGE, NICK, CORROSION, LIGHTNING_BURN, HOLE, PUNCTURE)
                .contains(this);
    }

    /** Damage that has gone all the way through, so there is nothing left to blend. */
    public boolean isThroughThickness() {
        return this == HOLE || this == PUNCTURE;
    }

    /** Kinds the manual has no allowable limit for at any size. */
    public boolean isNeverAllowable() {
        return EnumSet.of(CRACK, HOLE, PUNCTURE, BUCKLE).contains(this);
    }

    /** Kinds that lie along a direction, so the report has to carry one. */
    public boolean hasABearing() {
        return EnumSet.of(CRACK, SCRATCH, GOUGE, BUCKLE).contains(this);
    }

    /** Whether the report carries a depth: wherever metal moved or went. */
    public boolean needsDepth() {
        return isDeformation() || isMaterialLoss();
    }

    /** Kinds measured as a patch rather than as a line or a point. */
    public boolean needsArea() {
        return EnumSet.of(CORROSION, DISBOND, LIGHTNING_BURN).contains(this);
    }

    /** The manual section the limits for this kind are filed under. */
    public int section() {
        return section;
    }

    /** What could have caused a kind of damage, an unknown cause always among them. */
    public static Set<DamageOrigin> plausibleOrigins(DamageKind kind) {
        Set<DamageOrigin> held = EnumSet.copyOf(kind.plausible);
        held.add(DamageOrigin.UNKNOWN);
        return Collections.unmodifiableSet(held);
    }

    /** Whether a kind of damage could have come from a cause. */
    public static boolean isPlausible(DamageKind kind, DamageOrigin origin) {
        return plausibleOrigins(kind).contains(origin);
    }

    /** Refuse a kind and a cause that do not go together. */
    public static void checkPairing(DamageKind kind, DamageOrigin origin) {
        if (!isPlausible(kind, origin)) {
            throw new BadReading("a " + kind + " does not come from " + origin);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
