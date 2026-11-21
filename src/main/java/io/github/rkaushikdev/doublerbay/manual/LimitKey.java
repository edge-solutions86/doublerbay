package io.github.rkaushikdev.doublerbay.manual;

import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.structure.PanelKind;
import io.github.rkaushikdev.doublerbay.structure.Region;
import java.util.Objects;
import java.util.Optional;

/**
 * What a row is keyed on. A field left out means any.
 *
 * <p>The three keys do not weigh the same. Where the damage is sitting beats what the skin is,
 * because a fastener row is a fastener row on every panel on the aeroplane, and a page that says
 * nothing is allowable on a row means it whether the row is in the crown or the belly. So a row keyed
 * on region alone outranks a row keyed on panel kind alone, and a row keyed on both outranks either.
 */
public record LimitKey(DamageKind damageKind, Optional<PanelKind> panelKind, Optional<Region> region) {

    /** What a row earns for naming the damage kind, which every row does. */
    public static final int DAMAGE_WEIGHT = 1;

    /** What a row earns for naming the kind of skin. */
    public static final int PANEL_WEIGHT = 2;

    /** What a row earns for naming where on the panel, which outranks the kind of skin. */
    public static final int REGION_WEIGHT = 4;

    /** A key on a damage kind, and on the skin and the region when they are given. */
    public LimitKey {
        Objects.requireNonNull(damageKind, "damageKind");
        Objects.requireNonNull(panelKind, "panelKind");
        Objects.requireNonNull(region, "region");
    }

    /** A key on the damage kind alone, which matches it on any skin anywhere. */
    public LimitKey(DamageKind damageKind) {
        this(damageKind, Optional.empty(), Optional.empty());
    }

    /** A key on the damage kind and the kind of skin, anywhere on the panel. */
    public LimitKey(DamageKind damageKind, PanelKind panelKind) {
        this(damageKind, Optional.of(panelKind), Optional.empty());
    }

    /** A key on the damage kind and where on the panel, on any skin. */
    public LimitKey(DamageKind damageKind, Region region) {
        this(damageKind, Optional.empty(), Optional.of(region));
    }

    /** A key on all three. */
    public LimitKey(DamageKind damageKind, PanelKind panelKind, Region region) {
        this(damageKind, Optional.of(panelKind), Optional.of(region));
    }

    /** A key from what was to hand; a null skin or region is one the key does not narrow on. */
    public static LimitKey of(DamageKind damageKind, PanelKind panelKind, Region region) {
        return new LimitKey(damageKind, Optional.ofNullable(panelKind), Optional.ofNullable(region));
    }

    /** How tightly the row is keyed, with region weighing more than panel kind. */
    public int specificity() {
        int score = DAMAGE_WEIGHT;
        if (panelKind.isPresent()) {
            score += PANEL_WEIGHT;
        }
        if (region.isPresent()) {
            score += REGION_WEIGHT;
        }
        return score;
    }

    /** Whether a row keyed on this answers for a damage of a kind, on a skin, in a region. */
    public boolean matches(DamageKind damage, PanelKind panel, Region at) {
        if (damageKind != damage) {
            return false;
        }
        if (panelKind.isPresent() && panelKind.get() != panel) {
            return false;
        }
        return !(region.isPresent() && region.get() != at);
    }

    /** {@code dent on pressurised skin on a fastener row}, or {@code dent on any skin anywhere}. */
    @Override
    public String toString() {
        String panel = panelKind.map(PanelKind::value).orElse("any skin");
        String where = region.map(Region::value).orElse("anywhere");
        return damageKind + " on " + panel + " " + where;
    }
}
