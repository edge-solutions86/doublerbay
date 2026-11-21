package io.github.rkaushikdev.doublerbay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class CsvRowsTest {

    @Test
    void plainRowsSplitOnCommas() {
        assertThat(CsvRows.read("a,b\n1,2\n")).containsExactly(List.of("a", "b"), List.of("1", "2"));
    }

    @Test
    void aLastLineWithoutABreakIsStillARow() {
        assertThat(CsvRows.read("a,b")).containsExactly(List.of("a", "b"));
    }

    @Test
    void aCarriageReturnBeforeTheBreakEndsTheRowToo() {
        assertThat(CsvRows.read("a\r\nb\r")).containsExactly(List.of("a"), List.of("b"));
    }

    @Test
    void aBlankLineIsARowWithNoFields() {
        assertThat(CsvRows.read("a\n\nb\n")).containsExactly(List.of("a"), List.of(), List.of("b"));
    }

    @Test
    void emptyFieldsAreKept() {
        assertThat(CsvRows.read(",a,\n")).containsExactly(List.of("", "a", ""));
    }

    @Test
    void aQuotedFieldKeepsItsCommasQuotesAndBreaks() {
        assertThat(CsvRows.read("\"one, two\",\"he said \"\"no\"\"\",\"x\ny\"\n"))
                .containsExactly(List.of("one, two", "he said \"no\"", "x\ny"));
    }

    @Test
    void charactersAfterAClosingQuoteStayInTheField() {
        assertThat(CsvRows.read("\"ab\"cd,e\n")).containsExactly(List.of("abcd", "e"));
    }

    @Test
    void aQuoteInsideAnUnquotedFieldIsKept() {
        assertThat(CsvRows.read("a\"b,c\n")).containsExactly(List.of("a\"b", "c"));
    }

    @Test
    void aQuotedFieldLeftOpenAtTheEndIsKept() {
        assertThat(CsvRows.read("a,\"bc")).containsExactly(List.of("a", "bc"));
    }

    @Test
    void whiteSpaceAroundAFieldIsPartOfIt() {
        assertThat(CsvRows.read(" a , b\n")).containsExactly(List.of(" a ", " b"));
    }

    @Test
    void aBreakInsideAnUnquotedFieldIsRefused() {
        assertThatThrownBy(() -> CsvRows.read("a\rb\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("new-line character seen in unquoted field");
    }

    @Test
    void nothingReadsAsNoRows() {
        assertThat(CsvRows.read("")).isEmpty();
    }
}
