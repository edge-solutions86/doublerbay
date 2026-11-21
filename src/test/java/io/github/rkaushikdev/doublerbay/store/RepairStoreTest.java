package io.github.rkaushikdev.doublerbay.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.assess.Placement;
import io.github.rkaushikdev.doublerbay.fleet.UtilisationLog;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.grid.Reference;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.ids.RepairId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.FastenerSize;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.repair.Clash;
import io.github.rkaushikdev.doublerbay.repair.RepairCategory;
import io.github.rkaushikdev.doublerbay.repair.RepairForm;
import io.github.rkaushikdev.doublerbay.repair.RepairRecord;
import io.github.rkaushikdev.doublerbay.repair.Schemes;
import io.github.rkaushikdev.doublerbay.support.Desk;
import io.github.rkaushikdev.doublerbay.support.Sample;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RepairStoreTest {

    private static final Reference PAGE = Reference.parse("53-40-01");
    private static final LocalDate EMBODIED = LocalDate.of(2025, 4, 2);
    private static final PanelId PART = new PanelId(53, 10, 143, "L");
    private static final Serial MSN = new Serial("5127");
    private static final Serial OTHER = new Serial("5240");

    private Database database;

    @BeforeEach
    void stocked() {
        database = Desk.stocked();
    }

    private static Length inch(String text) {
        return Sample.inch(text);
    }

    private static Placement placed() {
        return Placement.place(Sample.tailContext(), Sample.card(Sample.cabinPointOn(10)));
    }

    private static RepairRecord record(int sequence, String along, String around, FastenerSize size) {
        return new RepairRecord(
                new RepairId(53, sequence),
                PART,
                new FindingId(2025, sequence),
                Schemes.selectScheme(inch("2.000"), inch("2.000"), placed(), PAGE, size),
                new Offset(inch(along), inch(around)),
                EMBODIED,
                UtilisationLog.usage(13900, 24400));
    }

    private static RepairRecord record(int sequence, String along, String around) {
        return record(sequence, along, around, null);
    }

    private static RepairRecord record(int sequence) {
        return record(sequence, "468.000", "91.000");
    }

    private static RepairRecord record() {
        return record(1);
    }

    private void embodied(Serial serial, RepairRecord... records) {
        UnitOfWork.run(database, unit -> {
            for (RepairRecord one : records) {
                unit.repairs().embody(one, serial);
            }
        });
    }

    private void embodied(RepairRecord... records) {
        embodied(MSN, records);
    }

    private RepairRecord held(int sequence) {
        return UnitOfWork.apply(database, unit -> unit.repairs().get(new RepairId(53, sequence)));
    }

    private void enrolOther() {
        Desk.enrolled(database, "5240");
    }

    @Test
    void aRepairGoesInAndComesBackTheSame() {
        embodied(record());
        RepairRecord held = held(1);
        assertThat(held.part()).isEqualTo(PART);
        assertThat(held.against()).isEqualTo(new FindingId(2025, 1));
        assertThat(held.embodied()).isEqualTo(EMBODIED);
    }

    @Test
    void theWholeDrawingSurvivesTheRoundTrip() {
        RepairRecord original = record(1, "468.000", "91.000", new FastenerSize(6));
        embodied(original);
        RepairRecord held = held(1);
        assertThat(held.scheme().doubler()).isEqualTo(original.scheme().doubler());
        assertThat(held.scheme().doubler().size().dash()).isEqualTo(6);
    }

    @Test
    void theMaterialComesBackOffTheCatalogueRatherThanTheRow() {
        RepairRecord original = record();
        embodied(original);
        assertThat(held(1).scheme().doubler().material())
                .isSameAs(original.scheme().doubler().material());
    }

    @Test
    void theFormAndCategorySurvive() {
        RepairRecord original = record();
        embodied(original);
        RepairRecord held = held(1);
        assertThat(held.scheme().form())
                .isInstanceOf(RepairForm.class)
                .isSameAs(original.scheme().form());
        assertThat(held.scheme().category())
                .isInstanceOf(RepairCategory.class)
                .isSameAs(original.scheme().category());
    }

    @Test
    void thePageItWasDrawnAgainstSurvives() {
        embodied(record());
        assertThat(held(1).scheme().reference()).isEqualTo(PAGE);
    }

    @Test
    void theCentreSurvivesAsTwoComponents() {
        embodied(record(1, "468.000", "-91.000"));
        Offset centre = held(1).centre();
        assertThat(centre.along()).isEqualTo(inch("468.000"));
        assertThat(centre.around()).isEqualTo(inch("-91.000"));
    }

    @Test
    void aRepairNumberUsedTwiceIsRefused() {
        embodied(record());
        assertThatThrownBy(() -> UnitOfWork.run(database, unit -> unit.repairs().embody(record(), MSN)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("already on file");
    }

    @Test
    void aRepairThatIsNotOnFileIsRefused() {
        assertThatThrownBy(() -> held(99)).isInstanceOf(NotOnFile.class).hasMessageContaining("not a repair on file");
    }

    @Test
    void removingTakesItOffTheFittedList() {
        embodied(record());
        UnitOfWork.run(database, unit -> unit.repairs().remove(new RepairId(53, 1), LocalDate.of(2026, 1, 5)));
        UnitOfWork.run(database, unit -> {
            assertThat(unit.repairs().onTail(MSN)).isEmpty();
            assertThat(unit.repairs().onTail(MSN, false)).hasSize(1);
        });
    }

    @Test
    void aRemovedRepairKeepsTheDayItCameOff() {
        embodied(record());
        UnitOfWork.run(database, unit -> unit.repairs().remove(new RepairId(53, 1), LocalDate.of(2026, 1, 5)));
        assertThat(held(1).removed()).contains(LocalDate.of(2026, 1, 5));
    }

    @Test
    void removingOneTwiceIsRefused() {
        embodied(record());
        UnitOfWork.run(database, unit -> {
            unit.repairs().remove(new RepairId(53, 1), LocalDate.of(2026, 1, 5));
            assertThatThrownBy(() -> unit.repairs().remove(new RepairId(53, 1), LocalDate.of(2026, 2, 5)))
                    .isInstanceOf(BadReading.class)
                    .hasMessageContaining("already removed");
        });
    }

    @Test
    void aRepairCannotComeOffBeforeItWentOn() {
        embodied(record());
        assertThatThrownBy(() -> UnitOfWork.run(
                        database, unit -> unit.repairs().remove(new RepairId(53, 1), LocalDate.of(2024, 1, 1))))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("before it went on");
    }

    @Test
    void removingOneThatIsNotOnFileIsRefused() {
        assertThatThrownBy(
                        () -> UnitOfWork.run(database, unit -> unit.repairs().remove(new RepairId(53, 99), EMBODIED)))
                .isInstanceOf(NotOnFile.class);
    }

    @Test
    void theRepairsOnATailComeBackOldestFirst() {
        embodied(record(1), record(2));
        List<RepairRecord> fitted =
                UnitOfWork.apply(database, unit -> unit.repairs().onTail(MSN));
        assertThat(fitted).extracting(one -> one.reference().sequence()).containsExactly(1, 2);
    }

    @Test
    void anotherTailsRepairsAreNotOnThisOne() {
        enrolOther();
        embodied(record(1));
        embodied(OTHER, record(2));
        UnitOfWork.run(database, unit -> {
            assertThat(unit.repairs().onTail(MSN)).hasSize(1);
            assertThat(unit.repairs().onTail(OTHER)).hasSize(1);
        });
    }

    @Test
    void aPanelIsAskedForOnOneTailAtATime() {
        enrolOther();
        embodied(record(1));
        embodied(OTHER, record(2));
        List<RepairRecord> onPanel =
                UnitOfWork.apply(database, unit -> unit.repairs().onPanel(MSN, PART));
        assertThat(onPanel).hasSize(1);
    }

    @Test
    void theRepairsAgainstOneCardComeBackTogether() {
        embodied(record(1), record(2));
        List<RepairRecord> against =
                UnitOfWork.apply(database, unit -> unit.repairs().against(new FindingId(2025, 1)));
        assertThat(against).hasSize(1);
    }

    @Test
    void twoRepairsTooCloseTogetherComeBackAsAClash() {
        embodied(record(1, "468.000", "91.000"), record(2, "469.000", "91.000"));
        List<Clash> clashing = UnitOfWork.apply(database, unit -> unit.repairs().clashingOn(MSN, PART));
        assertThat(clashing).hasSize(1);
    }

    @Test
    void twoRepairsFarApartDoNotClash() {
        embodied(record(1, "468.000", "91.000"), record(2, "620.000", "91.000"));
        List<Clash> clashing = UnitOfWork.apply(database, unit -> unit.repairs().clashingOn(MSN, PART));
        assertThat(clashing).isEmpty();
    }

    @Test
    void aRemovedRepairDoesNotClashWithTheOneThatReplacedIt() {
        embodied(record(1, "468.000", "91.000"), record(2, "469.000", "91.000"));
        UnitOfWork.run(database, unit -> unit.repairs().remove(new RepairId(53, 1), LocalDate.of(2026, 1, 5)));
        List<Clash> clashing = UnitOfWork.apply(database, unit -> unit.repairs().clashingOn(MSN, PART));
        assertThat(clashing).isEmpty();
    }

    @Test
    void theOnesCarryingARepeatInspectionAreListed() {
        embodied(record());
        UnitOfWork.run(database, unit -> {
            List<RepairRecord> carried = unit.repairs().needingInspection(MSN);
            List<RepairRecord> every = unit.repairs().onTail(MSN);
            assertThat(carried).allMatch(RepairRecord::needsInspection);
            assertThat(carried.size()).isLessThanOrEqualTo(every.size());
        });
    }

    @Test
    void aRowBuiltFromARecordCarriesTheTailItWasFittedTo() {
        RepairRow row = Decisions.repairToRow(record(), MSN);
        assertThat(row.getSerial()).isEqualTo(MSN);
        assertThat(row.getPart()).isEqualTo(PART);
        assertThat(Decisions.rowToRepair(row).reference()).isEqualTo(new RepairId(53, 1));
    }
}
