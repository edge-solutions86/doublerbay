package io.github.rkaushikdev.doublerbay.measure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class FastenerTest {

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    @Test
    void aDashNumberIsThirtySecondsOfAnInch() {
        assertThat(new FastenerSize(4).diameter()).isEqualTo(inch("0.125"));
        assertThat(new FastenerSize(8).diameter()).isEqualTo(inch("0.250"));
    }

    @Test
    void theOddSizesRoundToTheThousandthTheDrawingCarries() {
        assertThat(new FastenerSize(5).diameter()).isEqualTo(inch("0.156"));
        assertThat(new FastenerSize(3).diameter()).isEqualTo(inch("0.094"));
    }

    @Test
    void theExactShankIsKeptForAnythingThatAccumulates() {
        assertThat(new FastenerSize(5).exactDiameter())
                .isEqualByComparingTo(new BigDecimal("5").divide(new BigDecimal("32")));
    }

    @Test
    void aSizePrintsTheWayItIsCalledOut() {
        assertThat(new FastenerSize(5)).hasToString("-5");
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"-5|5", "5|5", "' -12 '|12"})
    void theParserTakesTheCalloutWithOrWithoutTheDash(String text, int dash) {
        assertThat(FastenerSize.parse(text).dash()).isEqualTo(dash);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "-", "five", "-5.5", "-123"})
    void theParserRefusesAnythingElse(String text) {
        assertThatThrownBy(() -> FastenerSize.parse(text)).isInstanceOf(BadReading.class);
    }

    @Test
    void aDashZeroIsNotAFastener() {
        assertThatThrownBy(() -> new FastenerSize(0))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("counts thirty seconds");
    }

    @Test
    void aDistanceInDTurnsIntoALength() {
        assertThat(new FastenerSize(8).diameters(2)).isEqualTo(inch("0.500"));
        assertThat(new FastenerSize(5).diameters("2.5")).isEqualTo(inch("0.391"));
    }

    @Test
    void aMeasuredDistanceReadsBackInD() {
        assertThat(new FastenerSize(8).inDiameters(inch("0.500"))).isEqualByComparingTo("2.00");
        assertThat(new FastenerSize(8).inDiameters(inch("0.375"))).isEqualByComparingTo("1.50");
    }

    @Test
    void theSpacingRulesMoveWithTheSize() {
        FastenerSize small = new FastenerSize(4);
        FastenerSize large = new FastenerSize(8);
        assertThat(small.minEdgeDistance()).isEqualTo(inch("0.250"));
        assertThat(large.minEdgeDistance()).isEqualTo(inch("0.500"));
        assertThat(large.minPitch()).isEqualTo(inch("1.000"));
        assertThat(large.minRowSpacing()).isEqualTo(inch("1.000"));
    }

    @Test
    void thePreferredEdgeDistanceIsFurtherOutThanTheFloor() {
        FastenerSize size = new FastenerSize(5);
        assertThat(size.preferredEdgeDistance().isGreaterThan(size.minEdgeDistance()))
                .isTrue();
    }

    @Test
    void theStockedListClimbsAndHoldsNoDuplicates() {
        List<Integer> dashes =
                FastenerSize.stocked().stream().map(FastenerSize::dash).toList();
        List<Integer> sorted = new ArrayList<>(dashes);
        sorted.sort(null);
        assertThat(dashes).isEqualTo(sorted);
        assertThat(new HashSet<>(dashes)).hasSameSizeAs(dashes);
    }

    @Test
    void aSizeOffTheListIsNotStocked() {
        assertThat(FastenerSize.isStocked(new FastenerSize(5))).isTrue();
        assertThat(FastenerSize.isStocked(new FastenerSize(7))).isFalse();
    }

    @Test
    void aWorkedHoleGoesUpToTheNextStockedSize() {
        assertThat(FastenerSize.nextSizeUp(new FastenerSize(5))).isEqualTo(new FastenerSize(6));
        assertThat(FastenerSize.nextSizeUp(new FastenerSize(6))).isEqualTo(new FastenerSize(8));
    }

    @Test
    void nothingGoesUpFromTheTopOfTheList() {
        assertThatThrownBy(() -> FastenerSize.nextSizeUp(new FastenerSize(12)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("largest stocked fastener");
    }

    @Test
    void sizesOrderAndHashByTheirDash() {
        assertThat(new FastenerSize(4)).isLessThan(new FastenerSize(5));
        List<FastenerSize> sorted = new ArrayList<>(List.of(new FastenerSize(8), new FastenerSize(3)));
        sorted.sort(null);
        assertThat(sorted).containsExactly(new FastenerSize(3), new FastenerSize(8));
        assertThat(new HashSet<>(List.of(new FastenerSize(5), new FastenerSize(5))))
                .hasSize(1);
    }
}
