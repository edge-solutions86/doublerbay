package io.github.rkaushikdev.doublerbay.damage;

import io.github.rkaushikdev.doublerbay.measure.Length;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Two damages close together are one damage, and working out which ones is not obvious.
 *
 * <p>The manual will not let two findings be assessed separately if they are inside a clear
 * separation of each other, and the separation it asks for is the larger of the two damages. So
 * whether a small dent has to be taken together with a big one nearby depends on the big one, not on
 * the small one, and the answer changes when the big one is re measured.
 *
 * <p>Interaction is also transitive and that is where it catches people. A and B are far enough
 * apart, B and C are far enough apart, but A and C are not, and the result is one cluster of three
 * with an envelope larger than any pair. Nobody gets that right by eye, which is why it is done here
 * with a union find rather than by walking down the list comparing neighbours.
 */
public final class Clusters {

    /**
     * Nothing is ever assessed separately from something inside this, however small both of them are.
     * It is the width of a blend runout on the thinnest skin.
     */
    public static final Length SEPARATION_FLOOR = Length.ofInches("1.000");

    /** Once the larger extent, never less than the floor. */
    public static final SeparationRule DEFAULT_SEPARATION = new SeparationRule();

    private Clusters() {}

    /** Clear separation asked for between two damages, taken off the larger of them. */
    public static Length separationRequired(Placed first, Placed second) {
        return separationRequired(first, second, DEFAULT_SEPARATION);
    }

    /** Clear separation asked for between two damages under a given rule. */
    public static Length separationRequired(Placed first, Placed second, SeparationRule rule) {
        return rule.between(first.extent(), second.extent());
    }

    /** The clear skin between the two, edge to edge. */
    public static Length gapBetween(Placed first, Placed second) {
        return first.footprint().clearanceTo(second.footprint());
    }

    /** Whether the two have to be assessed as one damage. */
    public static boolean interacts(Placed first, Placed second) {
        return interacts(first, second, DEFAULT_SEPARATION);
    }

    /** Whether the two have to be assessed as one damage under a given rule. */
    public static boolean interacts(Placed first, Placed second, SeparationRule rule) {
        return gapBetween(first, second).isLessThan(separationRequired(first, second, rule));
    }

    /** Group findings into the sets that have to be assessed together. */
    public static List<Cluster> clusters(List<Placed> placed) {
        return clusters(placed, DEFAULT_SEPARATION);
    }

    /**
     * Group findings into the sets that have to be assessed together under a given rule.
     *
     * <p>The groups come back in the order their first member was listed, and the members of each in
     * the order they were listed.
     */
    public static List<Cluster> clusters(List<Placed> placed, SeparationRule rule) {
        int[] parent = new int[placed.size()];
        for (int index = 0; index < parent.length; index++) {
            parent[index] = index;
        }
        for (int i = 0; i < placed.size(); i++) {
            for (int j = i + 1; j < placed.size(); j++) {
                if (interacts(placed.get(i), placed.get(j), rule)) {
                    int joined = root(parent, j);
                    parent[root(parent, i)] = joined;
                }
            }
        }
        Map<Integer, List<Placed>> grouped = new LinkedHashMap<>();
        for (int index = 0; index < placed.size(); index++) {
            grouped.computeIfAbsent(root(parent, index), ignored -> new ArrayList<>())
                    .add(placed.get(index));
        }
        return grouped.values().stream().map(Cluster::new).toList();
    }

    /** The representative of an entry's set, halving the path on the way up. */
    private static int root(int[] parent, int start) {
        int index = start;
        while (parent[index] != index) {
            parent[index] = parent[parent[index]];
            index = parent[index];
        }
        return index;
    }
}
