package io.github.rkaushikdev.doublerbay.measure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.support.Types;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class UsageTest {

    @Test
    void cyclesAreAWholeCount() {
        assertThat(new Cycles(1200).count()).isEqualTo(1200);
        assertThat(new Cycles(1200)).hasToString("1200 FC");
        // A fraction of a cycle does not compile: the only constructor takes an int.
        Types.constructorsTakeOnly(Cycles.class, int.class);
    }

    @Test
    void noCounterRunsBackwards() {
        assertThatThrownBy(() -> new Cycles(-1)).isInstanceOf(BadReading.class).hasMessageContaining("negative cycle");
        assertThatThrownBy(() -> new Hours(-1)).isInstanceOf(BadReading.class).hasMessageContaining("negative hour");
    }

    @Test
    void hoursAreHeldAsMinutes() {
        assertThat(Hours.ofHours("1.5").minutes()).isEqualTo(90);
        assertThat(new Hours(90).hours()).isEqualByComparingTo("1.5");
        assertThat(new Hours(90)).hasToString("1.5 FH");
    }

    @Test
    void anHourFinerThanAMinuteIsRefused() {
        assertThatThrownBy(() -> Hours.ofHours("1.001"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("finer than a minute");
    }

    @Test
    void countersAddAndSubtractWithinTheirOwnKind() {
        assertThat(new Cycles(10).plus(new Cycles(5))).isEqualTo(new Cycles(15));
        assertThat(new Hours(120).minus(new Hours(30))).isEqualTo(new Hours(90));
    }

    @Test
    void aCounterWillNotMixWithTheOtherOne() {
        // Adding or comparing across the two counters does not compile.
        Types.methodTakesOnly(Cycles.class, "plus", Cycles.class);
        Types.methodTakesOnly(Cycles.class, "isLessThan", Cycles.class);
        assertThat((Object) new Cycles(10)).isNotEqualTo(new Hours(10));
    }

    @Test
    void countersOrderAndHashWithinTheirKind() {
        assertThat(new Cycles(10)).isLessThan(new Cycles(20));
        assertThat(new Hours(60)).isGreaterThanOrEqualTo(new Hours(60));
        assertThat(new HashSet<>(List.of(new Cycles(10), new Cycles(10)))).hasSize(1);
        assertThat(new HashSet<>(List.of(new Hours(60), new Hours(60)))).hasSize(1);
    }

    @Test
    void aUsageCarriesBothCounters() {
        Usage usage = new Usage(new Cycles(1200), Hours.ofHours(2400));
        assertThat(usage).hasToString("1200 FC / 2400.0 FH");
    }

    @Test
    void usagesAddAndSubtractTogether() {
        Usage a = new Usage(new Cycles(100), Hours.ofHours(200));
        Usage b = new Usage(new Cycles(20), Hours.ofHours(50));
        assertThat(a.plus(b)).isEqualTo(new Usage(new Cycles(120), Hours.ofHours(250)));
        assertThat(a.minus(b)).isEqualTo(new Usage(new Cycles(80), Hours.ofHours(150)));
    }

    @Test
    void aShortHaulTailAndALongHaulTailDoNotOrder() {
        Usage shortHaul = new Usage(new Cycles(9000), Hours.ofHours(11000));
        Usage longHaul = new Usage(new Cycles(3000), Hours.ofHours(18000));
        assertThat(shortHaul.reachesBoth(longHaul)).isFalse();
        assertThat(longHaul.reachesBoth(shortHaul)).isFalse();
        assertThat(shortHaul.reachesEither(longHaul)).isTrue();
        assertThat(longHaul.reachesEither(shortHaul)).isTrue();
    }

    @Test
    void aLimitFallsDueOnWhicheverCounterGetsThereFirst() {
        Usage limit = new Usage(new Cycles(3000), Hours.ofHours(6000));
        Usage flown = new Usage(new Cycles(3000), Hours.ofHours(4100));
        assertThat(flown.reachesEither(limit)).isTrue();
        assertThat(flown.reachesBoth(limit)).isFalse();
    }

    @Test
    void neitherCounterThereYetIsNotDue() {
        Usage limit = new Usage(new Cycles(3000), Hours.ofHours(6000));
        Usage flown = new Usage(new Cycles(2999), Hours.ofHours(5999));
        assertThat(flown.reachesEither(limit)).isFalse();
    }

    @Test
    void theAverageLegSeparatesTheTwoOperations() {
        Usage shortHaul = new Usage(new Cycles(9000), Hours.ofHours(11000));
        Usage longHaul = new Usage(new Cycles(3000), Hours.ofHours(18000));
        assertThat(shortHaul.averageLeg()).isEqualByComparingTo("1.22");
        assertThat(longHaul.averageLeg()).isEqualByComparingTo("6.00");
    }

    @Test
    void aTailThatHasNotFlownHasNoAverageLeg() {
        assertThatThrownBy(() -> Usage.zero().averageLeg()).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void zeroIsZeroOnBothCounters() {
        assertThat(Usage.zero()).isEqualTo(new Usage(new Cycles(0), new Hours(0)));
    }
}
