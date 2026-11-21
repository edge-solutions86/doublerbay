package io.github.rkaushikdev.doublerbay.structure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class LaddersTest {

    @Test
    void aLadderClimbsOnlyInOrderWithNoRepeats() {
        assertThat(Ladders.climbs(List.of(1, 2, 5))).isTrue();
        assertThat(Ladders.climbs(List.of(1))).isTrue();
        assertThat(Ladders.climbs(List.of(2, 1))).isFalse();
        assertThat(Ladders.climbs(List.of(1, 1, 2))).isFalse();
    }

    @Test
    void aReadingOnARungCountsThatRung() {
        int[] rungs = {0, 8, 16};
        assertThat(Ladders.countAtOrBelow(rungs, 0)).isEqualTo(1);
        assertThat(Ladders.countAtOrBelow(rungs, 8)).isEqualTo(2);
        assertThat(Ladders.countAtOrBelow(rungs, 16)).isEqualTo(3);
    }

    @Test
    void aReadingBetweenRungsCountsTheRungsBelowIt() {
        int[] rungs = {0, 8, 16};
        assertThat(Ladders.countAtOrBelow(rungs, -1)).isZero();
        assertThat(Ladders.countAtOrBelow(rungs, 7)).isEqualTo(1);
        assertThat(Ladders.countAtOrBelow(rungs, 9)).isEqualTo(2);
        assertThat(Ladders.countAtOrBelow(rungs, 99)).isEqualTo(3);
    }
}
