package io.github.rkaushikdev.doublerbay.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.OutOfSequence;
import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.damage.Dent;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.disposition.DispositionRecord;
import io.github.rkaushikdev.doublerbay.disposition.DispositionState;
import io.github.rkaushikdev.doublerbay.fleet.Aircraft;
import io.github.rkaushikdev.doublerbay.fleet.Bulletin;
import io.github.rkaushikdev.doublerbay.fleet.Snapshot;
import io.github.rkaushikdev.doublerbay.fleet.UtilisationLog;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.Registration;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.support.Desk;
import io.github.rkaushikdev.doublerbay.support.Sample;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RepositoryTest {

    private static final Serial MSN = new Serial("5127");
    private static final FindingId DR = new FindingId(2025, 4182);
    private static final FindingId FIRST = new FindingId(2025, 1);

    private static Database empty() {
        return Desk.newDatabase();
    }

    /** A database with one tail and its log already on it. */
    private static Database stocked() {
        return Desk.stocked();
    }

    private static Finding laterRevision(Finding card, LocalDate on, int cycles, long hours) {
        return card.reMeasured(
                new Dent(Sample.inch("0.040"), Sample.inch("3.000"), Sample.inch("2.000")),
                on,
                Sample.flown(cycles, hours));
    }

    private static void raised(Database made, int... sequences) {
        UnitOfWork.run(made, unit -> {
            for (int sequence : sequences) {
                unit.findings().raiseFinding(Sample.card(sequence));
            }
        });
    }

    @Test
    void aTailGoesInAndComesBackAsTheDomainObject() {
        Database made = stocked();
        Aircraft held = UnitOfWork.apply(made, unit -> unit.aircraft().get(MSN));
        assertThat(held).isEqualTo(Sample.aircraft());
    }

    @Test
    void aTailAddedTwiceIsRefused() {
        Database made = stocked();
        assertThatThrownBy(() -> UnitOfWork.run(made, unit -> unit.aircraft().add(Sample.aircraft())))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("already on the register");
    }

    @Test
    void aTailThatIsNotThereIsALookupMiss() {
        Database made = empty();
        assertThatThrownBy(() -> UnitOfWork.run(made, unit -> unit.aircraft().get(MSN)))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("is not on the register");
    }

    @Test
    void aTailCanBeFoundByTheMarksItIsWearing() {
        Database made = stocked();
        UnitOfWork.run(made, unit -> {
            assertThat(unit.aircraft()
                            .byRegistration(new Registration("N412VD"))
                            .serial())
                    .isEqualTo(MSN);
            assertThatThrownBy(() -> unit.aircraft().byRegistration(new Registration("N1")))
                    .isInstanceOf(NotOnFile.class)
                    .hasMessageContaining("is not on the register");
        });
    }

    @Test
    void amendingATailKeepsTheNewConfiguration() {
        Database made = stocked();
        UnitOfWork.run(
                made, unit -> unit.aircraft().amend(unit.aircraft().get(MSN).withEmbodied(new Bulletin(53, 18))));
        Aircraft held = UnitOfWork.apply(made, unit -> unit.aircraft().get(MSN));
        assertThat(held.hasEmbodied(new Bulletin(53, 18))).isTrue();
    }

    @Test
    void amendingATailThatIsNotThereIsRefused() {
        Database made = empty();
        assertThatThrownBy(() -> UnitOfWork.run(made, unit -> unit.aircraft().amend(Sample.aircraft())))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("is not on the register");
    }

    @Test
    void theLogComesBackAsALog() {
        Database made = stocked();
        UtilisationLog held = UnitOfWork.apply(made, unit -> unit.aircraft().logFor(MSN));
        assertThat(held).isEqualTo(Sample.flyingLog());
    }

    @Test
    void aTailWithNoLogIsALookupMiss() {
        Database made = empty();
        UnitOfWork.run(made, unit -> unit.aircraft().add(Sample.aircraft()));
        assertThatThrownBy(() -> UnitOfWork.run(made, unit -> unit.aircraft().logFor(MSN)))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("has no log");
    }

    @Test
    void aPostedReadingLandsOnTheLog() {
        Database made = stocked();
        UnitOfWork.run(made, unit -> unit.aircraft()
                .postReading(MSN, new Snapshot(LocalDate.of(2025, 7, 1), Sample.flown(14700, 25800))));
        UtilisationLog held = UnitOfWork.apply(made, unit -> unit.aircraft().logFor(MSN));
        assertThat(held.latest().usage()).isEqualTo(Sample.flown(14700, 25800));
    }

    @Test
    void aReadingThatRunsTheCounterBackwardsIsRefused() {
        Database made = stocked();
        assertThatThrownBy(() -> UnitOfWork.run(made, unit -> unit.aircraft()
                        .postReading(MSN, new Snapshot(LocalDate.of(2025, 7, 1), Sample.flown(1, 1)))))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("cycles fell");
    }

    @Test
    void theInServiceListComesOffTheDeliveryDates() {
        Database made = stocked();
        UnitOfWork.run(made, unit -> {
            assertThat(unit.aircraft().inServiceOn(LocalDate.of(2020, 1, 1))).hasSize(1);
            assertThat(unit.aircraft().inServiceOn(LocalDate.of(2000, 1, 1))).isEmpty();
        });
    }

    @Test
    void aCardGoesInAndComesBackAsTheDomainObject() {
        Database made = stocked();
        UnitOfWork.run(made, unit -> unit.findings().raiseFinding(Sample.card()));
        Finding held = UnitOfWork.apply(made, unit -> unit.findings().latest(FIRST));
        assertThat(held).isEqualTo(Sample.card());
    }

    @Test
    void aCardRaisedTwiceIsRefused() {
        Database made = stocked();
        UnitOfWork.run(made, unit -> unit.findings().raiseFinding(Sample.card()));
        assertThatThrownBy(() -> UnitOfWork.run(made, unit -> unit.findings().raiseFinding(Sample.card())))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("already been raised");
    }

    @Test
    void aCardRaisedAtALaterRevisionIsRefused() {
        Database made = stocked();
        Finding later = laterRevision(Sample.card(), LocalDate.of(2025, 6, 1), 1, 1);
        assertThatThrownBy(() -> UnitOfWork.run(made, unit -> unit.findings().raiseFinding(later)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("raised at revision one");
    }

    @Test
    void aLaterRevisionLandsOnTheSameCard() {
        Database made = stocked();
        UnitOfWork.run(made, unit -> unit.findings().raiseFinding(Sample.card()));
        UnitOfWork.run(made, unit -> unit.findings()
                .recordRevision(laterRevision(unit.findings().latest(FIRST), LocalDate.of(2025, 6, 1), 14200, 24900)));
        UnitOfWork.run(made, unit -> {
            assertThat(unit.findings().latest(FIRST).revision()).isEqualTo(2);
            assertThat(unit.findings().history(FIRST)).hasSize(2);
        });
    }

    @Test
    void aRevisionOfACardThatIsNotOnFileIsRefused() {
        Database made = stocked();
        assertThatThrownBy(() -> UnitOfWork.run(made, unit -> unit.findings().recordRevision(Sample.card())))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("not a finding on this register");
    }

    @Test
    void aRevisionThatIsNotLaterIsRefused() {
        Database made = stocked();
        UnitOfWork.run(made, unit -> unit.findings().raiseFinding(Sample.card()));
        assertThatThrownBy(() -> UnitOfWork.run(made, unit -> unit.findings().recordRevision(Sample.card())))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("already at revision 1");
    }

    @Test
    void theCardsOnATailComeBackInNumberOrder() {
        Database made = stocked();
        raised(made, 3, 1, 2);
        List<Finding> found = UnitOfWork.apply(made, unit -> unit.findings().onTail(MSN));
        assertThat(found).extracting(one -> one.reference().sequence()).containsExactly(1, 2, 3);
    }

    @Test
    void theOpenListDropsWhatHasBeenSuperseded() {
        Database made = stocked();
        raised(made, 1, 2);
        UnitOfWork.run(made, unit -> unit.findings().supersede(FIRST, new FindingId(2025, 2)));
        List<Finding> open = UnitOfWork.apply(made, unit -> unit.findings().openOn(MSN));
        assertThat(open).extracting(one -> one.reference().sequence()).containsExactly(2);
    }

    @Test
    void supersedingDoesNotMakeARevisionBecauseNothingWasReMeasured() {
        Database made = stocked();
        raised(made, 1, 2);
        UnitOfWork.run(made, unit -> unit.findings().supersede(FIRST, new FindingId(2025, 2)));
        UnitOfWork.run(made, unit -> {
            assertThat(unit.findings().history(FIRST)).hasSize(1);
            assertThat(unit.findings().latest(FIRST).revision()).isEqualTo(1);
        });
    }

    @Test
    void supersedingACardThatIsNotOnFileIsRefused() {
        Database made = stocked();
        assertThatThrownBy(() -> UnitOfWork.run(made, unit -> unit.findings().supersede(FIRST, new FindingId(2025, 2))))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("not a finding on this register");
    }

    @Test
    void cardsCanBePulledOutByKind() {
        Database made = stocked();
        UnitOfWork.run(made, unit -> unit.findings().raiseFinding(Sample.card()));
        UnitOfWork.run(made, unit -> {
            assertThat(unit.findings().ofKind(DamageKind.DENT)).hasSize(1);
            assertThat(unit.findings().ofKind(DamageKind.CRACK)).isEmpty();
        });
    }

    @Test
    void theNextNumberCarriesOnFromTheHighestUsed() {
        Database made = stocked();
        raised(made, 7);
        UnitOfWork.run(made, unit -> {
            assertThat(unit.findings().nextReference(2025)).isEqualTo(new FindingId(2025, 8));
            assertThat(unit.findings().nextReference(2026)).isEqualTo(new FindingId(2026, 1));
        });
    }

    @Test
    void aDispositionSavesAndComesBackWithItsHistory() {
        Database made = stocked();
        DispositionRecord record = DispositionRecord.raised(DR)
                .movedTo(DispositionState.ASSESSED, LocalDate.of(2025, 3, 14), "structures");
        UnitOfWork.run(made, unit -> unit.dispositions().save(record));
        DispositionRecord held =
                UnitOfWork.apply(made, unit -> unit.dispositions().get(DR));
        assertThat(held).isEqualTo(record);
    }

    @Test
    void savingADispositionAgainReplacesWhatWasThere() {
        Database made = stocked();
        DispositionRecord first = DispositionRecord.raised(DR)
                .movedTo(DispositionState.ASSESSED, LocalDate.of(2025, 3, 14), "structures");
        UnitOfWork.run(made, unit -> unit.dispositions().save(first));
        UnitOfWork.run(made, unit -> unit.dispositions()
                .save(first.movedTo(DispositionState.IN_WORK, LocalDate.of(2025, 4, 1), "hangar")));
        DispositionRecord held =
                UnitOfWork.apply(made, unit -> unit.dispositions().get(DR));
        assertThat(held.state()).isSameAs(DispositionState.IN_WORK);
        assertThat(held.movesMade()).isEqualTo(2);
    }

    @Test
    void dispositionsCanBePulledOutByState() {
        Database made = stocked();
        UnitOfWork.run(made, unit -> {
            unit.dispositions().save(DispositionRecord.raised(DR));
            unit.dispositions().save(DispositionRecord.raised(new FindingId(2025, 4183)));
        });
        UnitOfWork.run(made, unit -> {
            assertThat(unit.dispositions().inState("raised")).hasSize(2);
            assertThat(unit.dispositions().inState("closed")).isEmpty();
            assertThat(unit.dispositions().all()).hasSize(2);
        });
    }

    @Test
    void aDispositionThatIsNotOnFileIsALookupMiss() {
        Database made = stocked();
        assertThatThrownBy(
                        () -> UnitOfWork.run(made, unit -> unit.dispositions().get(DR)))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("has no disposition on file");
    }

    @Test
    void nothingIsWrittenWhenTheWorkThrows() {
        Database made = stocked();
        assertThatThrownBy(() -> UnitOfWork.run(made, unit -> {
                    unit.findings().raiseFinding(Sample.card());
                    unit.dispositions().save(DispositionRecord.raised(FIRST));
                    throw new IllegalStateException("something went wrong");
                }))
                .isInstanceOf(IllegalStateException.class);
        UnitOfWork.run(made, unit -> {
            assertThat(unit.findings().onTail(MSN)).isEmpty();
            assertThat(unit.dispositions().all()).isEmpty();
        });
    }

    @Test
    void aUnitOfWorkOutsideItsWorkRefusesToDoAnything() {
        UnitOfWork leaked = UnitOfWork.apply(stocked(), unit -> unit);
        assertThatThrownBy(() -> leaked.findings().latest(DR))
                .isInstanceOf(OutOfSequence.class)
                .hasMessageContaining("not open, use it inside its work");
    }

    @Test
    void aFlushMakesAWriteVisibleInsideTheSameWork() {
        Database made = stocked();
        UnitOfWork.run(made, unit -> {
            unit.findings().raiseFinding(Sample.card());
            unit.flush();
            assertThat(unit.findings().latest(FIRST).reference()).isEqualTo(FIRST);
        });
    }

    @Test
    void aRollbackInsideTheWorkUndoesWhatCameBeforeIt() {
        Database made = stocked();
        UnitOfWork.run(made, unit -> {
            unit.findings().raiseFinding(Sample.card());
            unit.rollback();
        });
        List<Finding> left = UnitOfWork.apply(made, unit -> unit.findings().onTail(MSN));
        assertThat(left).isEmpty();
    }
}
