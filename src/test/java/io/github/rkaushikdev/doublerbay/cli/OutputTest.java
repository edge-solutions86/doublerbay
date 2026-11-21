package io.github.rkaushikdev.doublerbay.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OutputTest {

    @Test
    void aDateIsReadTheOneWay() {
        assertThat(Output.asDate("2025-03-14")).isEqualTo(LocalDate.of(2025, 3, 14));
        assertThat(Output.asDate("2025-03-14")).hasToString("2025-03-14");
    }

    @Test
    void aShortMonthOrDayIsStillADate() {
        assertThat(Output.asDate("2025-3-4")).isEqualTo(LocalDate.of(2025, 3, 4));
        assertThat(Output.asDate("2025-03- 4")).isEqualTo(LocalDate.of(2025, 3, 4));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "14/03/2025",
                "2025-02-30",
                "0000-01-01",
                "2025-13-01",
                "2025-03-14 ",
                " 2025-03-14",
                "25-03-14",
                "2025-003-14",
                "2025-03-04x",
                "2025-03-39"
            })
    void anythingElseIsRefusedByName(String text) {
        assertThatThrownBy(() -> Output.asDate(text))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is not a date, write it as yyyy-mm-dd");
    }

    @Test
    void aRefusedDateIsQuoted() {
        assertThatThrownBy(() -> Output.asDate("tuesday"))
                .isInstanceOf(BadReading.class)
                .hasMessage("'tuesday' is not a date, write it as yyyy-mm-dd");
    }

    @Test
    void columnsLineUpOnTheWidestCell() {
        String laid = Output.columns(List.of(List.of("a", "bbbb"), List.of("cccc", "d")));
        assertThat(Text.lines(laid)).containsExactly("a     bbbb", "cccc  d");
    }

    @Test
    void columnsOverNothingIsNothing() {
        assertThat(Output.columns(List.of())).isEmpty();
    }

    @Test
    void columnsPadAShortRowOut() {
        String laid = Output.columns(List.of(List.of("a", "b"), List.of("c")));
        assertThat(Text.lines(laid).get(1)).isEqualTo("c");
        assertThat(Text.lines(laid).get(0)).isEqualTo("a  b");
    }

    @Test
    void aTrailingEmptyColumnLeavesNoTrailingSpace() {
        String laid = Output.columns(List.of(List.of("tail", ""), List.of("N412VD", "")));
        assertThat(laid).isEqualTo("tail\nN412VD");
    }

    @Test
    void anEmptyReportHasASentenceForIt() {
        assertThat(Output.sayNothingFound("open on 5127")).isEqualTo("nothing open on 5127");
    }
}
