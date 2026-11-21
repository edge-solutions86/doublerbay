package io.github.rkaushikdev.doublerbay.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The exact text a csv export writes, byte for byte, so a spreadsheet on either platform reads it. */
class ExportBytesTest {

    @Test
    void onlyAValueThatNeedsItIsQuotedAndEveryRowEndsInACarriageReturnAndBreak() {
        String written = Export.asCsv(
                List.of("a", "b"),
                List.of(
                        List.of("one, two", "he \"x\""),
                        List.of("", ""),
                        List.of(" c ", "l\nm"),
                        List.of("#x", "c\rd")));
        assertThat(written).isEqualTo("a,b\r\n\"one, two\",\"he \"\"x\"\"\"\r\n,\r\n c ,\"l\nm\"\r\n#x,\"c\rd\"\r\n");
    }

    @Test
    void aRowThatIsOneEmptyValueIsWrittenAsAPairOfQuotes() {
        assertThat(Export.asCsv(List.of("a"), List.of(List.of(""), List.of("x"))))
                .isEqualTo("a\r\n\"\"\r\nx\r\n");
    }
}
