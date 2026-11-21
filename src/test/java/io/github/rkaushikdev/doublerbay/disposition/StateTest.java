package io.github.rkaushikdev.doublerbay.disposition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.OutOfSequence;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class StateTest {

    private static final FindingId DR = new FindingId(2025, 4182);
    private static final LocalDate DAY = LocalDate.of(2025, 3, 14);

    private static DispositionRecord raised() {
        return DispositionRecord.raised(DR);
    }

    private static DispositionRecord assessed() {
        return assessed(DAY);
    }

    private static DispositionRecord assessed(LocalDate on) {
        return raised().movedTo(DispositionState.ASSESSED, on, "structures");
    }

    @Test
    void aCardStartsRaisedWithNothingBehindIt() {
        assertThat(raised().state()).isSameAs(DispositionState.RAISED);
        assertThat(raised().movesMade()).isZero();
        assertThat(raised()).hasToString("DR-2025-004182 raised, 0 moves");
    }

    @Test
    void onlySupersededIsTerminal() {
        for (DispositionState state : DispositionState.values()) {
            assertThat(state.isTerminal()).isEqualTo(state == DispositionState.SUPERSEDED);
        }
        assertThat(DispositionState.MOVES.get(DispositionState.SUPERSEDED)).isEmpty();
    }

    @Test
    void closedAndSupersededAreTheTwoTheDeskNoLongerOwns() {
        assertThat(DispositionState.CLOSED.isOpen()).isFalse();
        assertThat(DispositionState.SUPERSEDED.isOpen()).isFalse();
        for (DispositionState state : List.of(
                DispositionState.RAISED,
                DispositionState.ASSESSED,
                DispositionState.DEFERRED,
                DispositionState.HELD,
                DispositionState.IN_WORK)) {
            assertThat(state.isOpen()).isTrue();
        }
    }

    @Test
    void theStatesWhereTheAeroplaneIsFlyingWithTheDamage() {
        assertThat(DispositionState.DEFERRED.isFlyingWithIt()).isTrue();
        assertThat(DispositionState.HELD.isFlyingWithIt()).isTrue();
        assertThat(DispositionState.IN_WORK.isFlyingWithIt()).isFalse();
        assertThat(DispositionState.CLOSED.isFlyingWithIt()).isFalse();
    }

    @Test
    void aCardCannotSkipTheAssessmentOnItsWayToClosed() {
        assertThat(DispositionState.RAISED.canMoveTo(DispositionState.CLOSED)).isFalse();
        assertThatThrownBy(() -> raised().movedTo(DispositionState.CLOSED, DAY, "structures"))
                .isInstanceOf(OutOfSequence.class)
                .hasMessageContaining("cannot go from raised to closed");
    }

    @Test
    void anAssessedCardCanGoFourWays() {
        for (DispositionState state : List.of(
                DispositionState.DEFERRED, DispositionState.HELD, DispositionState.IN_WORK, DispositionState.CLOSED)) {
            assertThat(assessed().movedTo(state, DAY, "structures").state()).isSameAs(state);
        }
    }

    @Test
    void aDeferredCardComesBackToAssessedAfterAReMeasurement() {
        DispositionRecord deferred = assessed().movedTo(DispositionState.DEFERRED, DAY, "structures");
        DispositionRecord again = deferred.movedTo(DispositionState.ASSESSED, LocalDate.of(2025, 6, 1), "structures");
        assertThat(again.state()).isSameAs(DispositionState.ASSESSED);
    }

    @Test
    void aCardInWorkDoesNotGoBackToDeferred() {
        DispositionRecord inWork = assessed().movedTo(DispositionState.IN_WORK, DAY, "hangar");
        assertThatThrownBy(() -> inWork.movedTo(DispositionState.DEFERRED, DAY, "structures"))
                .isInstanceOf(OutOfSequence.class)
                .hasMessageContaining("cannot go from in work to deferred");
    }

    @Test
    void aClosedCardCanBeReopenedByAssessingItAgain() {
        DispositionRecord closed = assessed().movedTo(DispositionState.CLOSED, DAY, "structures");
        assertThat(closed.movedTo(DispositionState.ASSESSED, LocalDate.of(2026, 1, 1), "structures")
                        .state())
                .isSameAs(DispositionState.ASSESSED);
    }

    @Test
    void aSupersededCardGoesNowhere() {
        DispositionRecord gone = raised().movedTo(DispositionState.SUPERSEDED, DAY, "structures");
        for (DispositionState state : DispositionState.values()) {
            assertThatThrownBy(() -> gone.movedTo(state, DAY, "structures")).isInstanceOf(OutOfSequence.class);
        }
    }

    @Test
    void everyMoveIsWrittenIntoTheHistory() {
        DispositionRecord walked = assessed().movedTo(DispositionState.IN_WORK, LocalDate.of(2025, 4, 1), "hangar");
        assertThat(walked.movesMade()).isEqualTo(2);
        assertThat(walked.history().get(0).now()).isSameAs(DispositionState.ASSESSED);
        assertThat(walked.history().get(1).by()).isEqualTo("hangar");
    }

    @Test
    void aMoveDatedBeforeTheOneBeforeItIsRefused() {
        DispositionRecord walked = assessed(LocalDate.of(2025, 4, 1));
        assertThatThrownBy(() -> walked.movedTo(DispositionState.IN_WORK, LocalDate.of(2025, 1, 1), "hangar"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("dated before the one before it");
    }

    @Test
    void aMoveNeedsSomebodyToHaveMadeIt() {
        assertThatThrownBy(() -> new Transition(DispositionState.RAISED, DispositionState.ASSESSED, DAY, "  "))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("made by somebody");
    }

    @Test
    void aMoveReadsBackWhoMadeItAndWhy() {
        Transition move = new Transition(
                DispositionState.ASSESSED, DispositionState.DEFERRED, DAY, "structures", "awaiting parts");
        assertThat(move).hasToString("2025-03-14 assessed to deferred by structures (awaiting parts)");
    }

    @Test
    void aCardRemembersEveryStateItHasBeenIn() {
        DispositionRecord walked =
                assessed().movedTo(DispositionState.DEFERRED, LocalDate.of(2025, 4, 1), "structures");
        assertThat(walked.hasBeen(DispositionState.RAISED)).isTrue();
        assertThat(walked.hasBeen(DispositionState.ASSESSED)).isTrue();
        assertThat(walked.hasBeen(DispositionState.DEFERRED)).isTrue();
        assertThat(walked.hasBeen(DispositionState.CLOSED)).isFalse();
    }

    @Test
    void aCardRemembersWhenItLastEnteredAState() {
        DispositionRecord walked = assessed(LocalDate.of(2025, 3, 14))
                .movedTo(DispositionState.DEFERRED, LocalDate.of(2025, 4, 1), "structures");
        assertThat(walked.entered(DispositionState.DEFERRED)).contains(LocalDate.of(2025, 4, 1));
        assertThat(walked.entered(DispositionState.CLOSED)).isEmpty();
    }

    @Test
    void reEnteringAStateUpdatesWhenItLastHappened() {
        DispositionRecord walked = assessed(LocalDate.of(2025, 3, 14));
        walked = walked.movedTo(DispositionState.DEFERRED, LocalDate.of(2025, 4, 1), "structures");
        walked = walked.movedTo(DispositionState.ASSESSED, LocalDate.of(2025, 6, 1), "structures");
        walked = walked.movedTo(DispositionState.DEFERRED, LocalDate.of(2025, 7, 1), "structures");
        assertThat(walked.entered(DispositionState.DEFERRED)).contains(LocalDate.of(2025, 7, 1));
    }

    @Test
    void movingACardLeavesTheEarlierRecordAlone() {
        DispositionRecord before = assessed();
        before.movedTo(DispositionState.IN_WORK, DAY, "hangar");
        assertThat(before.state()).isSameAs(DispositionState.ASSESSED);
        assertThat(before.movesMade()).isEqualTo(1);
    }
}
