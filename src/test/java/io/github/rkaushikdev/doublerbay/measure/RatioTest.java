package io.github.rkaushikdev.doublerbay.measure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RatioTest {

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    @Test
    void aRatioIsOneLengthOverAnother() {
        assertThat(Ratio.between(inch("0.040"), inch("2.000")).value()).isEqualByComparingTo("0.0200");
    }

    @Test
    void aRatioOverNothingIsRefused() {
        assertThatThrownBy(() -> Ratio.between(inch("0.040"), inch("0"))).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void aRatioReadsAsAPercentage() {
        assertThat(new Ratio("0.025")).hasToString("2.50%");
        assertThat(new Ratio("0.025").percent()).isEqualByComparingTo("2.50");
    }

    @Test
    void aRatioScalesALength() {
        assertThat(new Ratio("0.5").of(inch("0.040"))).isEqualTo(inch("0.020"));
    }

    @Test
    void ratiosOrderAndCompare() {
        assertThat(new Ratio("0.01").isLessThan(new Ratio("0.02"))).isTrue();
        assertThat(new Ratio("0.02").isAtLeast(new Ratio("0.02"))).isTrue();
        assertThat(new Ratio("0.0200")).isEqualTo(new Ratio("0.02"));
        assertThat(new HashSet<>(List.of(new Ratio("0.02"), new Ratio("0.0200"))))
                .hasSize(1);
    }

    @Test
    void theShallowDentAndTheCreaseGradeApart() {
        Ratio shallow = Ratio.sharpness(inch("0.250"), inch("6.000"));
        Ratio crease = Ratio.sharpness(inch("0.250"), inch("0.500"));
        assertThat(shallow.isLessThan(crease)).isTrue();
        assertThat(crease.percent()).isEqualByComparingTo("50.00");
    }

    @Test
    void aTaperReadsAndWritesTheWayTheShopDoes() {
        assertThat(Taper.parse("20:1")).hasToString("20:1");
        assertThat(Taper.parse(" 30 : 1 ").run()).isEqualTo(30);
    }

    @ParameterizedTest
    @ValueSource(strings = {"20", "1:20", "20:2", "", "twenty:1"})
    void theTaperParserRefusesAnythingElse(String text) {
        assertThatThrownBy(() -> Taper.parse(text)).isInstanceOf(BadReading.class);
    }

    @Test
    void aTaperFlatterThanOneToOneIsNotATaper() {
        assertThatThrownBy(() -> new Taper(0))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("at least one to one");
    }

    @Test
    void theRunoutIsTheDepthTimesTheRun() {
        assertThat(new Taper(20).runout(inch("0.005"))).isEqualTo(inch("0.100"));
    }

    @Test
    void theBiggerNumberIsTheGentlerBlend() {
        assertThat(new Taper(30).isGentlerThan(new Taper(20))).isTrue();
        assertThat(new Taper(20).isGentlerThan(new Taper(30))).isFalse();
    }

    @Test
    void aGentlerBlendSatisfiesASharperRequirement() {
        assertThat(new Taper(30).satisfies(new Taper(20))).isTrue();
        assertThat(new Taper(20).satisfies(new Taper(20))).isTrue();
        assertThat(new Taper(15).satisfies(new Taper(20))).isFalse();
    }

    @Test
    void tapersOrderByTheirRun() {
        List<Taper> sorted = new ArrayList<>(List.of(new Taper(30), new Taper(10), new Taper(20)));
        sorted.sort(null);
        assertThat(sorted).containsExactly(new Taper(10), new Taper(20), new Taper(30));
        assertThat(new HashSet<>(List.of(new Taper(20), new Taper(20)))).hasSize(1);
    }

    @Test
    void aTaperIsNotARatio() {
        assertThat((Object) new Taper(20)).isNotEqualTo(new Ratio("20"));
        assertThat((Object) new Ratio("20")).isNotEqualTo(new Taper(20));
    }
}
