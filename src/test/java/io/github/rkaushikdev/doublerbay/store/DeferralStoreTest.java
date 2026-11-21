package io.github.rkaushikdev.doublerbay.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.disposition.Deferral;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.measure.Cycles;
import io.github.rkaushikdev.doublerbay.measure.Interval;
import io.github.rkaushikdev.doublerbay.support.Desk;
import io.github.rkaushikdev.doublerbay.support.Sample;
import io.github.rkaushikdev.doublerbay.tolerance.InspectionMethod;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeferralStoreTest {

    private static final FindingId REFERENCE = new FindingId(2025, 41);
    private static final FindingId OTHER = new FindingId(2025, 42);
    private static final LocalDate DAY = Sample.DAY;

    private Database database;

    @BeforeEach
    void stocked() {
        database = Desk.stocked();
    }

    private static Deferral deferral(FindingId against, LocalDate on, InspectionMethod method, Interval limit) {
        return Deferral.grant(against, on, Sample.flown(13800, 24200), "j.mercer", method, limit, null, "");
    }

    private static Deferral deferral(FindingId against, LocalDate on) {
        return deferral(against, on, InspectionMethod.DVI, null);
    }

    private static Deferral deferral(FindingId against) {
        return deferral(against, DAY);
    }

    private static Deferral deferral() {
        return deferral(REFERENCE);
    }

    private Deferral held(FindingId reference) {
        return UnitOfWork.apply(database, unit -> unit.deferrals().get(reference));
    }

    @Test
    void aDeferralGoesInAndComesBackTheSame() {
        UnitOfWork.run(database, unit -> unit.deferrals().grant(deferral()));
        Deferral held = held(REFERENCE);
        assertThat(held.against()).isEqualTo(REFERENCE);
        assertThat(held.by()).isEqualTo("j.mercer");
        assertThat(held.limit().cycles()).isPresent();
    }

    @Test
    void theFourClocksSurviveTheRoundTrip() {
        Interval limit = Interval.parse("300 FC / 400 FH / 90 DY");
        UnitOfWork.run(database, unit -> unit.deferrals().grant(deferral(REFERENCE, DAY, InspectionMethod.DVI, limit)));
        Deferral held = held(REFERENCE);
        assertThat(held.limit().cycles().orElseThrow().count()).isEqualTo(300);
        assertThat(held.limit().days()).contains(90);
        assertThat(held.limit().months()).isEmpty();
    }

    @Test
    void theMethodSurvivesTheRoundTrip() {
        UnitOfWork.run(database, unit -> unit.deferrals().grant(deferral(REFERENCE, DAY, InspectionMethod.HFEC, null)));
        assertThat(held(REFERENCE).reInspectWith()).isSameAs(InspectionMethod.HFEC);
    }

    @Test
    void aSecondLiveDeferralAgainstOneCardIsRefused() {
        UnitOfWork.run(database, unit -> {
            unit.deferrals().grant(deferral());
            assertThatThrownBy(() -> unit.deferrals().grant(deferral()))
                    .isInstanceOf(BadReading.class)
                    .hasMessageContaining("already deferred");
        });
    }

    @Test
    void aCardThatIsNotDeferredIsNotOnFile() {
        assertThatThrownBy(
                        () -> UnitOfWork.run(database, unit -> unit.deferrals().get(OTHER)))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("not deferred");
    }

    @Test
    void liftingTakesItOffTheLiveList() {
        UnitOfWork.run(database, unit -> unit.deferrals().grant(deferral()));
        UnitOfWork.run(database, unit -> unit.deferrals().lift(REFERENCE, LocalDate.of(2025, 6, 1)));
        List<Deferral> live =
                UnitOfWork.apply(database, unit -> unit.deferrals().live());
        assertThat(live).isEmpty();
    }

    @Test
    void aLiftedDeferralKeepsItsRow() {
        UnitOfWork.run(database, unit -> unit.deferrals().grant(deferral()));
        UnitOfWork.run(database, unit -> unit.deferrals().lift(REFERENCE, LocalDate.of(2025, 6, 1)));
        UnitOfWork.run(database, unit -> {
            DeferralRow row = unit.session().find(DeferralRow.class, REFERENCE.toString());
            assertThat(row).isNotNull();
            assertThat(row.getLifted()).contains(LocalDate.of(2025, 6, 1));
        });
    }

    @Test
    void aDeferralCannotBeLiftedBeforeItWasGranted() {
        UnitOfWork.run(database, unit -> {
            unit.deferrals().grant(deferral());
            assertThatThrownBy(() -> unit.deferrals().lift(REFERENCE, LocalDate.of(2024, 1, 1)))
                    .isInstanceOf(BadReading.class)
                    .hasMessageContaining("cannot be lifted before");
        });
    }

    @Test
    void liftingOneThatWasNeverGrantedIsRefused() {
        assertThatThrownBy(
                        () -> UnitOfWork.run(database, unit -> unit.deferrals().lift(OTHER, DAY)))
                .isInstanceOf(NotOnFile.class);
    }

    @Test
    void aCardCanBeDeferredAgainAfterTheFirstWasLifted() {
        UnitOfWork.run(database, unit -> {
            unit.deferrals().grant(deferral());
            unit.deferrals().lift(REFERENCE, LocalDate.of(2025, 6, 1));
        });
        UnitOfWork.run(database, unit -> unit.deferrals().grant(deferral(REFERENCE, LocalDate.of(2025, 6, 2))));
        assertThat(held(REFERENCE).granted()).isEqualTo(LocalDate.of(2025, 6, 2));
    }

    @Test
    void tighteningKeepsTheDayItWasGranted() {
        UnitOfWork.run(database, unit -> unit.deferrals().grant(deferral()));
        UnitOfWork.run(database, unit -> {
            Deferral held = unit.deferrals().get(REFERENCE);
            unit.deferrals().tighten(held.tightenedTo(Interval.parse("50 FC"), "a.okonkwo", "wing root"));
        });
        Deferral after = held(REFERENCE);
        assertThat(after.granted()).isEqualTo(DAY);
        assertThat(after.limit().cycles().orElseThrow().count()).isEqualTo(50);
        assertThat(after.by()).isEqualTo("a.okonkwo");
        assertThat(after.reason()).isEqualTo("wing root");
    }

    @Test
    void tighteningACardThatIsNotDeferredIsRefused() {
        assertThatThrownBy(
                        () -> UnitOfWork.run(database, unit -> unit.deferrals().tighten(deferral(OTHER))))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("not deferred");
    }

    @Test
    void aRowIsNotAmendedWithAnotherCardsDeferral() {
        DeferralRow row = Decisions.deferralToRow(deferral());
        assertThatThrownBy(() -> Decisions.applyToRow(row, deferral(OTHER)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("another card");
    }

    @Test
    void theLiveListComesBackInTheOrderGranted() {
        UnitOfWork.run(database, unit -> {
            unit.deferrals().grant(deferral(OTHER, LocalDate.of(2025, 1, 4)));
            unit.deferrals().grant(deferral(REFERENCE, DAY));
        });
        List<Deferral> live =
                UnitOfWork.apply(database, unit -> unit.deferrals().live());
        assertThat(live).extracting(Deferral::against).containsExactly(OTHER, REFERENCE);
    }

    @Test
    void theOnesAgainstANamedSetComeBackInTheOrderAskedFor() {
        UnitOfWork.run(database, unit -> {
            unit.deferrals().grant(deferral(OTHER, LocalDate.of(2025, 1, 4)));
            unit.deferrals().grant(deferral(REFERENCE, DAY));
        });
        List<Deferral> asked =
                UnitOfWork.apply(database, unit -> unit.deferrals().against(List.of(REFERENCE, OTHER)));
        assertThat(asked).extracting(Deferral::against).containsExactly(REFERENCE, OTHER);
    }

    @Test
    void aCardInTheSetThatIsNotDeferredIsLeftOut() {
        UnitOfWork.run(database, unit -> unit.deferrals().grant(deferral()));
        List<Deferral> asked =
                UnitOfWork.apply(database, unit -> unit.deferrals().against(List.of(REFERENCE, OTHER)));
        assertThat(asked).hasSize(1);
    }

    @Test
    void aWindowPicksUpWhatWasGrantedInsideIt() {
        UnitOfWork.run(database, unit -> {
            unit.deferrals().grant(deferral(OTHER, LocalDate.of(2025, 1, 4)));
            unit.deferrals().grant(deferral(REFERENCE, DAY));
        });
        List<Deferral> window = UnitOfWork.apply(database, unit -> unit.deferrals()
                .grantedBetween(LocalDate.of(2025, 2, 1), LocalDate.of(2025, 12, 31)));
        assertThat(window).extracting(Deferral::against).containsExactly(REFERENCE);
    }

    @Test
    void aWindowPicksUpALiftedOneToo() {
        UnitOfWork.run(database, unit -> {
            unit.deferrals().grant(deferral());
            unit.deferrals().lift(REFERENCE, LocalDate.of(2025, 6, 1));
        });
        List<Deferral> window =
                UnitOfWork.apply(database, unit -> unit.deferrals().grantedBetween(DAY, LocalDate.of(2025, 12, 31)));
        assertThat(window).hasSize(1);
    }

    @Test
    void aWindowThatEndsBeforeItStartsIsRefused() {
        assertThatThrownBy(() -> UnitOfWork.run(
                        database, unit -> unit.deferrals().grantedBetween(LocalDate.of(2025, 12, 31), DAY)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("ends after it starts");
    }

    @Test
    void aStoredRowWithNoClocksLeftOnItIsRefusedByName() {
        DeferralRow row = new DeferralRow()
                .setAgainst(REFERENCE)
                .setGranted(DAY)
                .setAtCycles(Sample.flown(13800, 24200).cycles())
                .setAtHours(Sample.flown(13800, 24200).hours())
                .setLimitCycles(null)
                .setLimitHours(null)
                .setLimitMonths(null)
                .setLimitDays(null)
                .setMethod("detailed visual")
                .setGrantedBy("j.mercer")
                .setReason("");
        assertThatThrownBy(() -> Decisions.rowToDeferral(row))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("has no limit on it");
    }

    @Test
    void aDeferralSurvivesBesideTheCardItIsAgainst() {
        UnitOfWork.run(database, unit -> {
            Finding raised = Sample.card(41);
            unit.findings().raiseFinding(raised);
            unit.deferrals().grant(deferral(raised.reference()));
        });
        assertThat(held(REFERENCE).against()).isEqualTo(REFERENCE);
    }

    @Test
    void theClocksGoDownAsTheirOwnColumns() {
        Interval limit = Interval.parse("300 FC / 90 DY");
        DeferralRow row = Decisions.deferralToRow(deferral(REFERENCE, DAY, InspectionMethod.DVI, limit));
        assertThat(row.getLimitCycles()).contains(new Cycles(300));
        assertThat(row.getLimitDays()).contains(90);
        assertThat(row.getLimitHours()).isEmpty();
        assertThat(Decisions.isLifted(row)).isFalse();
        assertThat(Decisions.referenceOf(row)).isEqualTo(REFERENCE);
    }
}
