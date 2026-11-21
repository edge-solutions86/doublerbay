package io.github.rkaushikdev.doublerbay.fleet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ModelTest {

    private static final Bulletin SB_53_018 = new Bulletin(53, 18);
    private static final Bulletin SB_57_004 = new Bulletin(57, 4);

    private static boolean covers(Effectivity effectivity) {
        return covers(effectivity, "5127", Series.STANDARD, List.of());
    }

    private static boolean covers(Effectivity effectivity, String serial) {
        return covers(effectivity, serial, Series.STANDARD, List.of());
    }

    private static boolean covers(Effectivity effectivity, Series series) {
        return covers(effectivity, "5127", series, List.of());
    }

    private static boolean covers(Effectivity effectivity, List<Bulletin> embodied) {
        return covers(effectivity, "5127", Series.STANDARD, embodied);
    }

    private static boolean covers(Effectivity effectivity, String serial, Series series, List<Bulletin> embodied) {
        return effectivity.covers(series, new Serial(serial), Set.copyOf(embodied));
    }

    @Test
    void aSeriesNamesItselfTheWayTheManualDoes() {
        assertThat(Series.STANDARD).hasToString("V320-200");
        assertThat(Series.LONG_RANGE).hasToString("V320-200LR");
        assertThat(Series.STANDARD.isStretched()).isTrue();
        assertThat(Series.SHORT.isStretched()).isFalse();
        assertThat(Series.fromValue("200LR")).isSameAs(Series.LONG_RANGE);
    }

    @Test
    void aBulletinReadsAndPrintsPadded() {
        assertThat(SB_53_018).hasToString("SB 53-018");
        assertThat(Bulletin.parse("SB 53-018")).isEqualTo(SB_53_018);
        assertThat(Bulletin.parse("sb53-018")).isEqualTo(SB_53_018);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "53-018", "SB 53-18", "SB 530-18", "bulletin"})
    void aBulletinRefusesAnythingElse(String text) {
        assertThatThrownBy(() -> Bulletin.parse(text))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not a bulletin number");
    }

    @Test
    void aBulletinChapterAndNumberAreBounded() {
        assertThatThrownBy(() -> new Bulletin(10, 18))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("does not carry bulletins");
        assertThatThrownBy(() -> new Bulletin(53, 0))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not a bulletin number");
    }

    @Test
    void effectivityWithNothingSetCoversTheWholeFleet() {
        Effectivity everything = Effectivity.allAircraft();
        assertThat(covers(everything)).isTrue();
        assertThat(covers(everything, Series.SHORT)).isTrue();
        assertThat(covers(everything, "999")).isTrue();
        assertThat(everything.isOpenEnded()).isTrue();
    }

    @Test
    void aSeriesOutsideTheListIsNotCovered() {
        Effectivity onlyLong = Effectivity.allAircraft().withSeries(Set.of(Series.LONG_RANGE));
        assertThat(covers(onlyLong, Series.LONG_RANGE)).isTrue();
        assertThat(covers(onlyLong, Series.STANDARD)).isFalse();
    }

    @Test
    void effectivityAgainstNoSeriesIsRefused() {
        assertThatThrownBy(() -> Effectivity.allAircraft().withSeries(Set.of()))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("applies to nothing");
    }

    @Test
    void aSerialRangeTakesBothItsEnds() {
        Effectivity block = Effectivity.allAircraft().withSerials(5001, 5240);
        assertThat(covers(block, "5001")).isTrue();
        assertThat(covers(block, "5240")).isTrue();
        assertThat(covers(block, "5000")).isFalse();
        assertThat(covers(block, "5241")).isFalse();
        assertThat(block.isOpenEnded()).isFalse();
    }

    @Test
    void anOpenEndedRangeRunsOnFromItsFirstSerial() {
        Effectivity fromHere = Effectivity.allAircraft().withFirstSerial(5200);
        assertThat(covers(fromHere, "9999")).isTrue();
        assertThat(covers(fromHere, "5199")).isFalse();
        assertThat(fromHere.isOpenEnded()).isTrue();
    }

    @Test
    void aRangeThatRunsBackwardsIsRefused() {
        assertThatThrownBy(() -> Effectivity.allAircraft().withSerials(5240, 5001))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("run backwards");
    }

    @Test
    void aRequiredBulletinHasToBeEmbodied() {
        Effectivity afterMod = Effectivity.allAircraft().withRequires(SB_53_018);
        assertThat(covers(afterMod, List.of(SB_53_018))).isTrue();
        assertThat(covers(afterMod, List.of())).isFalse();
        assertThat(covers(afterMod, List.of(SB_57_004))).isFalse();
    }

    @Test
    void anExcludedBulletinHasToBeAbsent() {
        Effectivity beforeMod = Effectivity.allAircraft().withExcludes(SB_53_018);
        assertThat(covers(beforeMod, List.of())).isTrue();
        assertThat(covers(beforeMod, List.of(SB_57_004))).isTrue();
        assertThat(covers(beforeMod, List.of(SB_53_018))).isFalse();
    }

    @Test
    void theSameBulletinCannotBeRequiredAndExcluded() {
        assertThatThrownBy(() -> Effectivity.of(Set.of(Series.values()), null, null, SB_53_018, SB_53_018))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("both required and excluded");
    }

    @Test
    void everyClauseHasToBeSatisfiedAtOnce() {
        Effectivity narrow = Effectivity.of(Set.of(Series.STANDARD), 5100, 5200, SB_53_018, null);
        assertThat(covers(narrow, "5150", Series.STANDARD, List.of(SB_53_018))).isTrue();
        assertThat(covers(narrow, "5150", Series.STANDARD, List.of())).isFalse();
        assertThat(covers(narrow, "5300", Series.STANDARD, List.of(SB_53_018))).isFalse();
        assertThat(covers(narrow, "5150", Series.SHORT, List.of(SB_53_018))).isFalse();
    }

    @Test
    void effectivityReadsBackTheWayThePagePrintsIt() {
        assertThat(Effectivity.allAircraft().withSeries(Set.of(Series.STANDARD)))
                .hasToString("200");
        Effectivity narrow = Effectivity.of(Set.of(Series.STANDARD), 5100, 5200, SB_53_018, null);
        assertThat(narrow).hasToString("200; MSN 5100 to 5200; with SB 53-018");
    }

    @Test
    void anOpenEndedRangeSaysSoWhenItPrints() {
        assertThat(Effectivity.allAircraft().withFirstSerial(5100).toString()).contains("MSN 5100 to on");
        assertThat(Effectivity.allAircraft().withLastSerial(5200).toString()).contains("MSN first to 5200");
        assertThat(Effectivity.allAircraft().withExcludes(SB_53_018).toString()).contains("without SB 53-018");
    }
}
