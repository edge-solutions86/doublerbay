package io.github.rkaushikdev.doublerbay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.fleet.Snapshot;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.store.Database;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import io.github.rkaushikdev.doublerbay.support.Cli;
import io.github.rkaushikdev.doublerbay.support.Cli.Ran;
import io.github.rkaushikdev.doublerbay.support.Desk;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServiceReadingsTest {

    private static final String HEADER = "serial,taken,cycles,hours\n";
    private static final Serial MSN = new Serial("5127");
    private static final Serial OTHER = new Serial("5240");

    private Database database;

    @BeforeEach
    void twoTails() {
        database = Desk.stocked();
        Desk.enrolled(database, "5240");
    }

    private static String csvOf(String... rows) {
        StringBuilder text = new StringBuilder(HEADER);
        for (String one : rows) {
            text.append(one).append('\n');
        }
        return text.toString();
    }

    private Taken posted(String text) {
        return UnitOfWork.apply(database, unit -> Readings.postAll(unit, text));
    }

    private int latest(Serial serial) {
        return UnitOfWork.apply(
                database,
                unit -> unit.aircraft().logFor(serial).latest().usage().cycles().count());
    }

    @Test
    void aFileWithTheWrongHeaderIsRefused() {
        assertThatThrownBy(() -> Readings.linesFrom("serial,taken,cycles\n"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("missing the hours column");
    }

    @Test
    void aFileMissingTwoColumnsNamesBoth() {
        assertThatThrownBy(() -> Readings.linesFrom("serial,taken\n"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("missing the cycles, hours column");
    }

    @Test
    void aFileWithOnlyAHeaderReadsNoLines() {
        assertThat(Readings.linesFrom(HEADER)).isEmpty();
    }

    @Test
    void aLineCarriesItsPositionInTheFile() {
        List<ReadingLine> lines = Readings.linesFrom(csvOf("5127,2025-06-01,14000,24500"));
        assertThat(lines.get(0).at()).isEqualTo(2);
        assertThat(lines.get(0).serial()).isEqualTo("5127");
    }

    @Test
    void aLineReadsBackWithItsPosition() {
        ReadingLine line = new ReadingLine(2, "5127", "2025-06-01", "14000", "24500");
        assertThat(line).hasToString("line 2, 5127 on 2025-06-01");
    }

    @Test
    void aGoodLineBecomesAReading() {
        Snapshot snapshot = Readings.snapshotFrom(new ReadingLine(2, "5127", "2025-06-01", "14000", "24500"));
        assertThat(snapshot.taken()).isEqualTo(LocalDate.of(2025, 6, 1));
        assertThat(snapshot.usage().cycles().count()).isEqualTo(14000);
    }

    @Test
    void aLineWithNoSerialIsRefused() {
        assertThatThrownBy(() -> Readings.snapshotFrom(new ReadingLine(2, "", "2025-06-01", "14000", "24500")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("no serial");
    }

    @Test
    void aLineWithABadDateIsRefused() {
        assertThatThrownBy(() -> Readings.snapshotFrom(new ReadingLine(2, "5127", "01/06/2025", "14000", "24500")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not a date");
    }

    @Test
    void aLineWithBadCyclesIsRefused() {
        assertThatThrownBy(() -> Readings.snapshotFrom(new ReadingLine(2, "5127", "2025-06-01", "lots", "24500")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not a count of cycles");
    }

    @Test
    void aLineWithBlankHoursIsRefused() {
        assertThatThrownBy(() -> Readings.snapshotFrom(new ReadingLine(2, "5127", "2025-06-01", "14000", "")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not a count of hours");
    }

    @Test
    void oneGoodLineLands() {
        Taken taken = posted(csvOf("5127,2025-06-01,14000,24500"));
        assertThat(taken.isClean()).isTrue();
        assertThat(taken.posted()).containsExactly(MSN);
        assertThat(latest(MSN)).isEqualTo(14000);
    }

    @Test
    void twoGoodLinesBothLand() {
        Taken taken = posted(csvOf("5127,2025-06-01,14000,24500", "5240,2025-06-01,14100,24600"));
        assertThat(taken.read()).isEqualTo(2);
        assertThat(taken.isClean()).isTrue();
    }

    @Test
    void aTailThatIsNotOnTheRegisterIsRefusedByItself() {
        Taken taken = posted(csvOf("9999,2025-06-01,14000,24500", "5127,2025-06-01,14000,24500"));
        assertThat(taken.posted()).containsExactly(MSN);
        assertThat(taken.refused()).hasSize(1);
        assertThat(taken.refused().get(0).toString()).contains("9999");
    }

    @Test
    void theGoodLinesAfterABadOneStillLand() {
        Taken taken = posted(
                csvOf("9999,2025-06-01,14000,24500", "5127,2025-06-01,14000,24500", "5240,2025-06-01,14100,24600"));
        assertThat(taken.posted()).hasSize(2);
        assertThat(latest(OTHER)).isEqualTo(14100);
    }

    @Test
    void aReadingThatRunsACounterBackwardsIsRefused() {
        Taken taken = posted(csvOf("5127,2025-06-01,100,200"));
        assertThat(taken.isClean()).isFalse();
        assertThat(taken.refused().get(0).why()).contains("cycles fell");
    }

    @Test
    void aRefusedLineLeavesTheLogAlone() {
        int before = latest(MSN);
        posted(csvOf("5127,2025-06-01,100,200"));
        assertThat(latest(MSN)).isEqualTo(before);
    }

    @Test
    void aLineThatCannotBeReadIsRefusedWithItsReason() {
        Taken taken = posted(csvOf("5127,01/06/2025,14000,24500"));
        assertThat(taken.refused().get(0).why()).contains("not a date");
    }

    @Test
    void aBlankSerialIsRefusedWithItsReason() {
        Taken taken = posted(csvOf(",2025-06-01,14000,24500"));
        assertThat(taken.refused().get(0).why()).contains("no serial");
    }

    @Test
    void aRefusalReadsBackWithItsLine() {
        Taken taken = posted(csvOf("9999,2025-06-01,14000,24500"));
        assertThat(taken.refused().get(0).toString()).startsWith("line 2, 9999 on 2025-06-01: ");
    }

    @Test
    void theResultCountsWhatItRead() {
        Taken taken = posted(csvOf("5127,2025-06-01,14000,24500", "9999,2025-06-01,14000,24500"));
        assertThat(taken.read()).isEqualTo(2);
        assertThat(taken).hasToString("1 readings posted, 1 refused");
    }

    @Test
    void aCleanRunReadsBackWithoutARefusalCount() {
        assertThat(new Taken(List.of(MSN), List.of())).hasToString("1 readings posted");
    }

    @Test
    void anEmptyFilePostsNothing() {
        Taken taken = posted(HEADER);
        assertThat(taken.read()).isZero();
        assertThat(taken.isClean()).isTrue();
    }

    @Test
    void theCommandPostsAFile(@TempDir Path tmp) {
        Path path = Cli.writtenText(tmp, "counters.csv", csvOf("5127,2025-06-01,14000,24500"));
        Ran answer = Cli.invoke(database, "fleet", "import", path.toString());
        assertThat(answer.exitCode()).as(answer.output()).isZero();
        assertThat(answer.output()).contains("1 readings posted");
    }

    @Test
    void theCommandPrintsEveryRefusal(@TempDir Path tmp) {
        Path path = Cli.writtenText(tmp, "counters.csv", csvOf("9999,2025-06-01,14000,24500"));
        Ran answer = Cli.invoke(database, "fleet", "import", path.toString());
        assertThat(answer.output()).contains("1 refused");
        assertThat(answer.output()).contains("line 2, 9999");
    }

    @Test
    void theCommandRefusesAFileWithTheWrongHeader(@TempDir Path tmp) {
        Path path = Cli.writtenText(tmp, "counters.csv", "tail,day\n");
        Ran answer = Cli.invoke(database, "fleet", "import", path.toString());
        assertThat(answer.exitCode()).isNotZero();
    }
}
