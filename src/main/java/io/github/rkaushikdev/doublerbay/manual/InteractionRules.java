package io.github.rkaushikdev.doublerbay.manual;

import io.github.rkaushikdev.doublerbay.damage.SeparationRule;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import java.util.Objects;

/**
 * What the book says about damages near each other, as one object: the limits that count damages
 * rather than measure them.
 *
 * <p>The book allows a dent of a size. It separately allows two of them in a bay, six on a panel,
 * and it says two inside a clear separation are assessed as one damage rather than two.
 *
 * <p>Those three interact in a way that is easy to get wrong. Clustering happens first, because it
 * changes how many damages there are. A panel with eight dents on it may be over the panel count at
 * eight and inside it at five once three of them have been taken together as one, and the count that
 * matters is the one after clustering.
 *
 * <p>Two sets of rules are equal when they ask for the same separation and the same counts, however
 * many places the multiple was written to.
 */
public record InteractionRules(SeparationRule separation, CountLimits counts) {

    /** What the V320 book asks for where a page does not say otherwise. */
    public static final InteractionRules DEFAULT_RULES = fromBook("1", "1.000", 2, 6, 3);

    /** The separation and the counts, both stated. */
    public InteractionRules {
        Objects.requireNonNull(separation, "separation");
        Objects.requireNonNull(counts, "counts");
    }

    /**
     * Build the rules the way a limit page states them: the multiple and the floor as decimal text,
     * and each count, where a null count is one the page does not state.
     */
    public static InteractionRules fromBook(
            String multiple, String floor, Integer perBay, Integer perPanel, Integer perCluster) {
        return new InteractionRules(
                new SeparationRule(Decimals.parse(multiple), Length.ofInches(floor)),
                CountLimits.of(perBay, perPanel, perCluster));
    }

    /** A page that states a separation and counts nothing. */
    public static InteractionRules fromBook(String multiple, String floor) {
        return fromBook(multiple, floor, null, null, null);
    }

    /** Once the larger extent, floor 1.000, and nothing counted. */
    public static InteractionRules fromBook() {
        return fromBook("1", "1.000");
    }

    /** The clear separation asked for between two damages of these extents. */
    public Length separationBetween(Length first, Length second) {
        return separation.between(first, second);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof InteractionRules that
                && separation.multiple().compareTo(that.separation.multiple()) == 0
                && separation.floor().equals(that.separation.floor())
                && counts.equals(that.counts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(separation.multiple().stripTrailingZeros(), separation.floor(), counts);
    }

    /** {@code separation 1 times the larger, floor 1.000; counts 3 in a group, 2 a bay, 6 a panel}. */
    @Override
    public String toString() {
        return "separation " + separation + "; counts " + counts;
    }
}
