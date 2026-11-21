package io.github.rkaushikdev.doublerbay.structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * The two questions every ladder of members is asked: whether its rungs climb, and how many of them
 * a reading has passed.
 */
final class Ladders {

    private Ladders() {}

    /** Whether a run of numbers is already in order with no two the same. */
    static boolean climbs(List<Integer> values) {
        List<Integer> sorted = new ArrayList<>(values);
        sorted.sort(null);
        return values.equals(sorted) && new HashSet<>(values).size() == values.size();
    }

    /**
     * How many rungs of a climbing ladder are at or below a reading, which is the index of the first
     * rung past it.
     */
    static int countAtOrBelow(int[] climbing, int reading) {
        int found = Arrays.binarySearch(climbing, reading);
        return found >= 0 ? found + 1 : -(found + 1);
    }
}
