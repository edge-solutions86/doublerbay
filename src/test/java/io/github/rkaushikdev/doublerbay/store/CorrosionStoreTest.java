package io.github.rkaushikdev.doublerbay.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.support.Desk;
import io.github.rkaushikdev.doublerbay.tolerance.CorrosionEntry;
import io.github.rkaushikdev.doublerbay.tolerance.CorrosionLevel;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CorrosionStoreTest {

    private static final LocalDate DAY = LocalDate.of(2025, 3, 14);
    private static final LocalDate EARLIER = LocalDate.of(2023, 6, 1);
    private static final Serial MSN = new Serial("5127");
    private static final Serial OTHER = new Serial("5240");
    private static final PanelId PART = new PanelId(53, 10, 143, "L");
    private static final PanelId ELSEWHERE = new PanelId(53, 10, 210, "R");

    private Database database;

    @BeforeEach
    void twoTails() {
        database = Desk.stocked();
        Desk.enrolled(database, "5240");
    }

    private static CorrosionEntry entry(PanelId part, CorrosionLevel level, LocalDate found) {
        return new CorrosionEntry(part, level, found);
    }

    private static CorrosionEntry entry(CorrosionLevel level) {
        return entry(PART, level, DAY);
    }

    private static CorrosionEntry found(LocalDate on) {
        return entry(PART, CorrosionLevel.ONE, on);
    }

    private static CorrosionEntry on(PanelId part) {
        return entry(part, CorrosionLevel.ONE, DAY);
    }

    private void record(CorrosionEntry one, Serial serial, int sequence, LocalDate reported) {
        UnitOfWork.run(database, unit -> unit.corrosion().record(one, serial, new FindingId(2025, sequence), reported));
    }

    private void record(CorrosionEntry one, Serial serial, int sequence) {
        record(one, serial, sequence, null);
    }

    private void record(CorrosionEntry one, int sequence) {
        record(one, MSN, sequence);
    }

    private void record(CorrosionEntry one) {
        record(one, 1);
    }

    private List<CorrosionEntry> onTail(Serial serial) {
        return UnitOfWork.apply(database, unit -> unit.corrosion().onTail(serial));
    }

    private List<CorrosionEntry> unreported() {
        return UnitOfWork.apply(database, unit -> unit.corrosion().unreported());
    }

    private boolean hasForm() {
        return UnitOfWork.apply(database, unit -> unit.corrosion().hasFormHere(MSN, PART, DAY));
    }

    @Test
    void aGradedFindingGoesInAndComesBack() {
        record(entry(CorrosionLevel.ONE));
        List<CorrosionEntry> held = onTail(MSN);
        assertThat(held).hasSize(1);
        assertThat(held.get(0).part()).isEqualTo(PART);
        assertThat(held.get(0).level()).isSameAs(CorrosionLevel.ONE);
    }

    @Test
    void theLevelSurvivesAsItsNumber() {
        record(entry(CorrosionLevel.THREE));
        assertThat(onTail(MSN).get(0).level()).isSameAs(CorrosionLevel.THREE);
    }

    @Test
    void entriesComeBackOldestFirst() {
        record(found(DAY), 1);
        record(found(EARLIER), 2);
        assertThat(onTail(MSN)).extracting(CorrosionEntry::foundOn).containsExactly(EARLIER, DAY);
    }

    @Test
    void anotherTailsCorrosionIsNotOnThisOne() {
        record(entry(CorrosionLevel.ONE), OTHER, 1);
        assertThat(onTail(MSN)).isEmpty();
        assertThat(onTail(OTHER)).hasSize(1);
    }

    @Test
    void onePanelCanBeAskedFor() {
        record(on(PART), 1);
        record(on(ELSEWHERE), 2);
        List<CorrosionEntry> onPanel =
                UnitOfWork.apply(database, unit -> unit.corrosion().onPanel(MSN, PART));
        assertThat(onPanel).hasSize(1);
    }

    @Test
    void aPanelWithNothingOnItComesBackEmpty() {
        List<CorrosionEntry> onPanel =
                UnitOfWork.apply(database, unit -> unit.corrosion().onPanel(MSN, PART));
        assertThat(onPanel).isEmpty();
    }

    @Test
    void theFleetCanBeAskedForOneLevel() {
        record(entry(CorrosionLevel.TWO), 1);
        record(entry(CorrosionLevel.ONE), 2);
        record(entry(CorrosionLevel.TWO), OTHER, 3);
        List<CorrosionEntry> atTwo =
                UnitOfWork.apply(database, unit -> unit.corrosion().atLevel(CorrosionLevel.TWO));
        assertThat(atTwo).hasSize(2);
    }

    @Test
    void aLevelNobodyHasReachedComesBackEmpty() {
        record(entry(CorrosionLevel.ONE));
        List<CorrosionEntry> atThree =
                UnitOfWork.apply(database, unit -> unit.corrosion().atLevel(CorrosionLevel.THREE));
        assertThat(atThree).isEmpty();
    }

    @Test
    void aLevelThreeThatHasNotGoneOutIsListed() {
        record(entry(CorrosionLevel.THREE));
        assertThat(unreported()).hasSize(1);
    }

    @Test
    void aLevelThreeAlreadyReportedDropsOffTheList() {
        record(entry(CorrosionLevel.THREE), MSN, 1, LocalDate.of(2025, 3, 15));
        assertThat(unreported()).isEmpty();
    }

    @Test
    void aLevelOneIsNeverOnTheUnreportedList() {
        record(entry(CorrosionLevel.ONE));
        assertThat(unreported()).isEmpty();
    }

    @Test
    void markingOneReportedTakesItOffTheList() {
        record(entry(CorrosionLevel.THREE));
        UnitOfWork.run(
                database, unit -> unit.corrosion().markReported(new FindingId(2025, 1), LocalDate.of(2025, 3, 15)));
        assertThat(unreported()).isEmpty();
    }

    @Test
    void reportingOneThatIsNotOnTheRegisterIsRefused() {
        assertThatThrownBy(() ->
                        UnitOfWork.run(database, unit -> unit.corrosion().markReported(new FindingId(2025, 9), DAY)))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("not on the corrosion");
    }

    @Test
    void aFindingCannotBeReportedBeforeItWasFound() {
        record(entry(CorrosionLevel.THREE));
        assertThatThrownBy(() -> UnitOfWork.run(database, unit -> unit.corrosion()
                        .markReported(new FindingId(2025, 1), LocalDate.of(2024, 1, 1))))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("before it was found");
    }

    @Test
    void aPanelWithNoHistoryHasNoForm() {
        assertThat(hasForm()).isFalse();
    }

    @Test
    void aPanelGradedBeforeHasForm() {
        record(found(EARLIER));
        assertThat(hasForm()).isTrue();
    }

    @Test
    void anEntryRaisedLaterDoesNotCountAsHistory() {
        record(found(LocalDate.of(2026, 1, 1)));
        assertThat(hasForm()).isFalse();
    }

    @Test
    void anEntryOnTheSameDayDoesNotCountAsHistory() {
        record(found(DAY));
        assertThat(hasForm()).isFalse();
    }

    @Test
    void anotherPanelsHistoryIsNotThisPanels() {
        record(entry(ELSEWHERE, CorrosionLevel.ONE, EARLIER));
        assertThat(hasForm()).isFalse();
    }

    @Test
    void anotherTailsHistoryIsNotThisTails() {
        record(found(EARLIER), OTHER, 1);
        assertThat(hasForm()).isFalse();
    }

    @Test
    void historyIsReadOffRowsWithoutADatabase() {
        List<CorrosionRow> rows = List.of(new CorrosionRow()
                .setSerial(MSN)
                .setAgainst(new FindingId(2025, 1))
                .setPart(PART)
                .setLevel(1)
                .setFoundOn(EARLIER));
        assertThat(Decisions.seenHereBefore(rows, PART, DAY)).isTrue();
        assertThat(Decisions.seenHereBefore(rows, ELSEWHERE, DAY)).isFalse();
    }

    @Test
    void aRowCarryingALevelTheProgrammeDoesNotGradeIsRefused() {
        CorrosionRow row = new CorrosionRow()
                .setSerial(MSN)
                .setAgainst(new FindingId(2025, 1))
                .setPart(PART)
                .setLevel(9)
                .setFoundOn(DAY);
        assertThatThrownBy(() -> Decisions.rowToCorrosion(row))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not one the programme grades");
    }
}
