package io.github.rkaushikdev.doublerbay.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.rkaushikdev.doublerbay.BadReading;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PrintedTest {

    private static final JsonMapper JSON = new JsonMapper();

    private static JsonNode read(String text) throws Exception {
        return JSON.readTree(text);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "'\"chief-key-9004\"'|chief-key-9004",
                "12345678|12345678",
                "123456789012345678901234567890|123456789012345678901234567890",
                "true|True",
                "false|False",
                "null|null",
                "1.5|1.5",
                "100.0|100.0",
                "1e2|100.0",
                "0.0001|0.0001",
                "0.00001|1e-05",
                "1e16|1e+16",
                "1e15|1000000000000000.0",
                "-2.5e-7|-2.5e-07",
                "-0.0|-0.0",
                "1e400|inf",
                "'[\"a\", 1, [true, null]]'|'[''a'', 1, [True, null]]'",
                "'{\"a\": 1, \"b\": \"it''s\"}'|'{''a'': 1, ''b'': \"it''s\"}'",
                "'[]'|'[]'",
                "'{}'|'{}'",
            })
    void aFieldThatIsNotTextIsReadAsItsWrittenForm(String json, String expected) throws Exception {
        assertThat(Printed.of(read(json))).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "'{}'|dict",
                "'[]'|list",
                "'\"x\"'|str",
                "true|bool",
                "7|int",
                "7.5|float",
                "null|null",
            })
    void anEntryNamesTheKindOfValueItIs(String json, String expected) throws Exception {
        assertThat(Printed.kind(read(json))).isEqualTo(expected);
    }

    @Test
    void aFractionThatIsNotANumberSaysSo() {
        assertThat(Printed.fraction(Double.NaN)).isEqualTo("nan");
        assertThat(Printed.fraction(Double.NEGATIVE_INFINITY)).isEqualTo("-inf");
    }

    @Test
    void aKeyWrittenAsANumberIsReadAsItsDigits() {
        KeyRing ring = KeyFile.ringFromText("[{\"key\": 12345678, \"holder\": \"j.mercer\", \"role\": \"chief\"}]");
        assertThat(ring.holderOf("12345678").role()).isSameAs(Role.CHIEF);
    }

    @Test
    void aRoleWrittenAsANumberIsRefusedInItsWrittenForm() {
        assertThatThrownBy(
                        () -> KeyFile.ringFromText("[{\"key\": \"chief-key-9004\", \"holder\": \"j\", \"role\": 5}]"))
                .isInstanceOf(BadReading.class)
                .hasMessageStartingWith("'5' is not a role, it is one of reader, mechanic, engineer, chief engineer");
    }

    @Test
    void textAfterTheListIsNotReadableJson() {
        assertThatThrownBy(() -> KeyFile.ringFromText("[] []"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not readable json");
        assertThatThrownBy(() -> KeyFile.ringFromText("   "))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not readable json");
    }

    @Test
    void aFileThatIsNotUtf8TextIsNotRead(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("keys.json");
        Files.write(path, new byte[] {(byte) 0xff, (byte) 0xfe, '[', ']'});
        assertThatThrownBy(() -> KeyFile.ringFromFile(path)).isInstanceOf(UncheckedIOException.class);
    }
}
