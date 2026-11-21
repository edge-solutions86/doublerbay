package io.github.rkaushikdev.doublerbay.measure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.support.Types;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class LengthTest {

    @Test
    void aThousandthSurvivesTheRoundTrip() {
        assertThat(Length.ofInches("0.040").mils()).isEqualTo(40);
        assertThat(Length.ofInches("0.040").inches()).isEqualByComparingTo("0.040");
    }

    @Test
    void aBodyStationKeepsItsHalfInch() {
        assertThat(Length.ofInches("727.5").mils()).isEqualTo(727500);
        assertThat(Length.ofInches("727.5")).hasToString("727.500 in");
    }

    @Test
    void finerThanAThousandthIsRefused() {
        assertThatThrownBy(() -> Length.ofInches("0.0405"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("finer than a thousandth");
    }

    @Test
    void milsMustBeWhole() {
        // A fraction of a mil does not compile: the only constructor takes an int.
        Types.constructorsTakeOnly(Length.class, int.class);
    }

    @Test
    void aBoolIsNotACountOfMils() {
        // Nor does a true or a false, for the same reason.
        Types.constructorsTakeOnly(Length.class, int.class);
    }

    @Test
    void millimetresComeBackAsInches() {
        assertThat(Length.ofMm("25.4")).isEqualTo(Length.ofInches("1.000"));
        assertThat(Length.ofInches("1.000").mm()).isEqualByComparingTo("25.40");
    }

    @Test
    void arithmeticStaysInMils() {
        Length a = Length.ofInches("0.040");
        Length b = Length.ofInches("0.016");
        assertThat(a.plus(b).inches()).isEqualByComparingTo("0.056");
        assertThat(a.minus(b).inches()).isEqualByComparingTo("0.024");
        assertThat(a.times(3).inches()).isEqualByComparingTo("0.120");
        assertThat(a.times(BigDecimal.valueOf(3))).isEqualTo(a.times(3));
        assertThat(a.negate().abs()).isEqualTo(a);
        assertThat(a.negate().mils()).isEqualTo(-40);
    }

    @Test
    void scalingByARatioRoundsToTheThousandth() {
        assertThat(Length.ofInches("0.063").times(new BigDecimal("0.5")).mils()).isEqualTo(32);
    }

    @Test
    void orderingAndEquality() {
        Length thin = Length.ofInches("0.032");
        Length thick = Length.ofInches("0.063");
        assertThat(thin.isLessThan(thick)).isTrue();
        assertThat(thick.isAtLeast(thin)).isTrue();
        assertThat(thin).isNotEqualTo(thick);
        assertThat(thin).isEqualTo(Length.ofInches("0.032"));
        List<Length> sorted = new ArrayList<>(List.of(thick, thin));
        sorted.sort(null);
        assertThat(sorted).containsExactly(thin, thick);
    }

    @Test
    void zeroIsFalseyAndEverythingElseIsNot() {
        assertThat(Length.ZERO.isZero()).isTrue();
        assertThat(Length.ofInches("0.001").isZero()).isFalse();
    }

    @Test
    void aLengthHashesByValue() {
        assertThat(new HashSet<>(List.of(Length.ofInches("0.040"), new Length(40))))
                .hasSize(1);
    }

    @Test
    void mixingALengthWithABareNumberIsRefused() {
        // Adding a bare number does not compile: plus takes only another length.
        Types.methodTakesOnly(Length.class, "plus", Length.class);
    }

    @Test
    void comparingALengthWithABareNumberIsRefused() {
        // Comparing with a bare number does not compile either.
        Types.methodTakesOnly(Length.class, "isLessThan", Length.class);
        assertThat(Length.ofInches("0.040")).isNotEqualTo(40);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "0.040|40",
                "' 0.063 '|63",
                ".5|500",
                "727.5|727500",
                "3/32|94",
                "1/4|250",
                "1-1/4|1250",
                "1 1/4|1250",
                "-1/8|-125"
            })
    void theParserTakesBothCalloutForms(String text, int mils) {
        assertThat(Length.parse(text).mils()).isEqualTo(mils);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "3/0", "wide", "1//4", "0.040 in"})
    void theParserRefusesWhatIsNotACallout(String text) {
        assertThatThrownBy(() -> Length.parse(text)).isInstanceOf(BadReading.class);
    }

    @Test
    void aFractionRoundsToTheNearestMil() {
        assertThat(Length.parse("3/32").inches()).isEqualByComparingTo("0.094");
    }

    @Test
    void reprRoundTrips() {
        Length length = Length.ofInches("0.040");
        assertThat(length.repr()).isEqualTo("Length.ofInches(\"0.040\")");
    }
}
