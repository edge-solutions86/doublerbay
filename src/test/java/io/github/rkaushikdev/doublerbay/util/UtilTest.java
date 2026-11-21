package io.github.rkaushikdev.doublerbay.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UtilTest {

    @Test
    void textIsQuotedSoItsEdgesShow() {
        assertThat(Literal.of("0.0405 ")).isEqualTo("'0.0405 '");
        assertThat(Literal.of("it's")).isEqualTo("\"it's\"");
        assertThat(Literal.of("both ' and \"")).isEqualTo("'both \\' and \"'");
        assertThat(Literal.of("a\tb\nc\\")).isEqualTo("'a\\tb\\nc\\\\'");
        assertThat(Literal.of("\u0007​é")).isEqualTo("'\\x07\\u200bé'");
        assertThat(Literal.of(" ")).isEqualTo("'\\xa0'");
        assertThat(Literal.of(new String(Character.toChars(0xF0000)))).isEqualTo("'\\U000f0000'");
        assertThat(Literal.of(42)).isEqualTo("42");
    }

    @Test
    void whiteSpaceIncludesTheNoBreakSpaces() {
        assertThat(Text.strip("  0.040 \t")).isEqualTo("0.040");
        assertThat(Text.strip("   ")).isEmpty();
        assertThat(Text.isBlank("\u0085")).isTrue();
        assertThat(Text.words("  one\ttwo  three ")).containsExactly("one", "two", "three");
        assertThat(Text.words("   ")).isEmpty();
        assertThat(Text.stripTrailing("a b  ")).isEqualTo("a b");
        assertThat(Text.padRight("ab", 4)).isEqualTo("ab  ");
        assertThat(Text.padLeft("ab", 4)).isEqualTo("  ab");
        assertThat(Text.padLeft("abcde", 4)).isEqualTo("abcde");
        assertThat(Text.upper("i")).isEqualTo("I");
        assertThat(Text.lower("I")).isEqualTo("i");
    }

    @Test
    void linesSplitAtEveryKindOfBreak() {
        assertThat(Text.lines("a\nb\r\nc\rd e\n")).containsExactly("a", "b", "c", "d", "e");
        assertThat(Text.lines("")).isEmpty();
        assertThat(Text.lines("a\n\nb")).containsExactly("a", "", "b");
    }

    @Test
    void decimalTextAllowsSpaceAndUnderscores() {
        assertThat(Decimals.parse(" 1_000.5 ")).isEqualByComparingTo("1000.5");
        assertThatThrownBy(() -> Decimals.parse("1__0")).isInstanceOf(NumberFormatException.class);
        assertThatThrownBy(() -> Decimals.parse("_1")).isInstanceOf(NumberFormatException.class);
        assertThatThrownBy(() -> Decimals.parse("wide")).isInstanceOf(NumberFormatException.class);
    }

    @Test
    void divisionCarriesTwentyEightDigitsAndBreaksTiesToEven() {
        assertThat(Decimals.over(BigDecimal.ONE, 3)).hasToString("0.3333333333333333333333333333");
        assertThat(Decimals.over(BigDecimal.TEN, 4)).hasToString("2.5");
        assertThat(Decimals.quantize(new BigDecimal("0.125"), 2)).hasToString("0.12");
        assertThat(Decimals.toInt(new BigDecimal("2.5"))).isEqualTo(2);
        assertThat(Decimals.isWhole(new BigDecimal("2.000"))).isTrue();
        assertThat(Decimals.text(new BigDecimal("1E-7"))).isEqualTo("1E-7");
        assertThat(Decimals.times(new BigDecimal("1.5"), 2)).isEqualByComparingTo("3");
    }

    @Test
    void aWholeNumberAllowsASignAndUnderscores() {
        assertThat(Whole.parse(" -21 ")).isEqualTo(-21);
        assertThat(Whole.parse("1_000")).isEqualTo(1000);
        assertThatThrownBy(() -> Whole.parse("1_")).isInstanceOf(NumberFormatException.class);
        assertThatThrownBy(() -> Whole.parse("soon")).isInstanceOf(NumberFormatException.class);
    }

    @Test
    void jsonIsWrittenInOneFixedForm() {
        Map<String, Object> members = new LinkedHashMap<>();
        members.put("text", "café \"q\" \\ \n\t\r\b\f\u0001");
        members.put("count", 3);
        members.put("yes", true);
        members.put("none", null);
        members.put("list", List.of(1, "a"));
        members.put("nested", Map.of("k", "v"));
        members.put("other", new StringBuilder("sb"));
        assertThat(JsonText.object(members))
                .isEqualTo("{\"text\": \"caf\\u00e9 \\\"q\\\" \\\\ \\n\\t\\r\\b\\f\\u0001\", \"count\": 3,"
                        + " \"yes\": true, \"none\": null, \"list\": [1, \"a\"], \"nested\": {\"k\": \"v\"},"
                        + " \"other\": \"sb\"}");
    }

    @Test
    void aMomentIsWrittenWithItsOffset() {
        assertThat(Iso.of(OffsetDateTime.of(2025, 3, 14, 6, 30, 0, 0, ZoneOffset.UTC)))
                .isEqualTo("2025-03-14T06:30:00+00:00");
        assertThat(Iso.of(OffsetDateTime.of(2025, 3, 14, 6, 30, 0, 250_000_000, ZoneOffset.ofHoursMinutes(-5, -30))))
                .isEqualTo("2025-03-14T06:30:00.250000-05:30");
        assertThat(Iso.of(OffsetDateTime.of(2025, 3, 14, 6, 30, 0, 0, ZoneOffset.ofTotalSeconds(3601))))
                .isEqualTo("2025-03-14T06:30:00+01:00:01");
        assertThat(Iso.of(LocalTime.of(23, 59, 59))).isEqualTo("23:59:59");
    }
}
