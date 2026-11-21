package io.github.rkaushikdev.doublerbay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.OutOfSequence;
import io.github.rkaushikdev.doublerbay.disposition.DispositionRecord;
import io.github.rkaushikdev.doublerbay.disposition.DispositionState;
import io.github.rkaushikdev.doublerbay.disposition.EngineeringOrder;
import io.github.rkaushikdev.doublerbay.disposition.OrderId;
import io.github.rkaushikdev.doublerbay.disposition.OrderState;
import io.github.rkaushikdev.doublerbay.disposition.RepairApproval;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.repair.RepairCategory;
import io.github.rkaushikdev.doublerbay.store.Database;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import io.github.rkaushikdev.doublerbay.support.Desk;
import io.github.rkaushikdev.doublerbay.support.Sample;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServiceOrdersTest {

    private static final LocalDate DAY = LocalDate.of(2025, 3, 14);
    private static final LocalDate SENT = LocalDate.of(2025, 3, 20);
    private static final LocalDate ANSWERED = LocalDate.of(2025, 6, 1);
    private static final FindingId AGAINST = new FindingId(2025, 1);
    private static final OrderId FIRST = new OrderId(2025, 1);
    private static final String QUESTION = "dent in the chem mill land at FR15, past the allowable, what may we do";

    private Database database;

    @BeforeEach
    void stocked() {
        database = Desk.stocked();
    }

    /** One card, raised and looked up, off the shared helper. */
    private FindingId assessed(int sequence) {
        return Desk.assessedCard(database, sequence);
    }

    private FindingId assessed() {
        return assessed(1);
    }

    private static RepairApproval approval(String sheet, LocalDate until) {
        return new RepairApproval(sheet, RepairCategory.B, List.of("inspect at every C check"), Optional.of(until));
    }

    private static RepairApproval approval(LocalDate until) {
        return approval("RAS-2025-4417", until);
    }

    private static RepairApproval approval() {
        return approval(LocalDate.of(2027, 1, 1));
    }

    private Raised out(FindingId against, String question, LocalDate on) {
        return UnitOfWork.apply(database, unit -> Orders.raiseOrder(unit, against, question, on, "a.okonkwo"));
    }

    private Raised out(FindingId against) {
        return out(against, QUESTION, DAY);
    }

    private Raised out() {
        return out(AGAINST);
    }

    private DispositionState cardState() {
        return UnitOfWork.apply(
                database, unit -> unit.dispositions().get(AGAINST).state());
    }

    private Optional<RepairCategory> category(LocalDate on) {
        return UnitOfWork.apply(database, unit -> Orders.approvedCategory(unit, AGAINST, on));
    }

    @Test
    void raisingAnOrderHoldsTheCard() {
        assessed();
        Raised answer = out();
        assertThat(answer.cardState()).isSameAs(DispositionState.HELD);
        assertThat(cardState()).isSameAs(DispositionState.HELD);
    }

    @Test
    void theOrderGoesOnFile() {
        assessed();
        out();
        EngineeringOrder held = UnitOfWork.apply(database, unit -> unit.orders().get(FIRST));
        assertThat(held.question()).isEqualTo(QUESTION);
    }

    @Test
    void theOrderNumberIsAllocatedHere() {
        assessed(1);
        assessed(2);
        out();
        Raised second = out(new FindingId(2025, 2));
        assertThat(second.order().reference()).isEqualTo(new OrderId(2025, 2));
    }

    @Test
    void aRaisedOrderReadsBackAsOneLine() {
        assessed();
        assertThat(out()).hasToString("EO-2025-0001 raised, card held for engineering");
    }

    @Test
    void aQuestionThatSaysNothingIsRefused() {
        assessed();
        assertThatThrownBy(() -> out(AGAINST, "help", DAY))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("says what is being asked");
    }

    @Test
    void aCardThatIsNotOnFileCannotGoOut() {
        assertThatThrownBy(() -> out(new FindingId(2025, 9))).isInstanceOf(NotOnFile.class);
    }

    @Test
    void aCardAlreadyOutCannotGoOutAgain() {
        assessed();
        out();
        assertThatThrownBy(this::out)
                .isInstanceOf(OutOfSequence.class)
                .hasMessageContaining("already out to the manufacturer");
    }

    @Test
    void aRaisedCardCannotBeHeldBeforeItIsAssessed() {
        UnitOfWork.run(database, unit -> {
            unit.findings().raiseFinding(Sample.card(1));
            unit.dispositions().save(DispositionRecord.raised(AGAINST));
        });
        assertThatThrownBy(this::out)
                .isInstanceOf(OutOfSequence.class)
                .hasMessageContaining("cannot go from raised to held");
    }

    @Test
    void sendingRecordsTheDayItActuallyWent() {
        assessed();
        out();
        EngineeringOrder answer = UnitOfWork.apply(database, unit -> Orders.send(unit, FIRST, SENT));
        assertThat(answer.state()).isSameAs(OrderState.SENT);
        assertThat(answer.sent()).contains(SENT);
    }

    @Test
    void acknowledgingMovesTheOrderOn() {
        assessed();
        out();
        UnitOfWork.run(database, unit -> {
            Orders.send(unit, FIRST, SENT);
            assertThat(Orders.acknowledge(unit, FIRST).state()).isSameAs(OrderState.ACKNOWLEDGED);
        });
    }

    @Test
    void anApprovalReleasesTheCard() {
        assessed();
        out();
        UnitOfWork.run(database, unit -> {
            Orders.send(unit, FIRST, SENT);
            Orders.approve(unit, FIRST, approval(), ANSWERED, "a.okonkwo");
        });
        assertThat(cardState()).isSameAs(DispositionState.ASSESSED);
    }

    @Test
    void anApprovalKeepsItsSheetAndConditions() {
        assessed();
        out();
        UnitOfWork.run(database, unit -> {
            Orders.send(unit, FIRST, SENT);
            Orders.approve(unit, FIRST, approval(), ANSWERED, "a.okonkwo");
        });
        RepairApproval held = UnitOfWork.apply(
                database, unit -> unit.orders().get(FIRST).approval().orElseThrow());
        assertThat(held.sheet()).isEqualTo("RAS-2025-4417");
        assertThat(held.conditions()).containsExactly("inspect at every C check");
    }

    @Test
    void aRejectionAlsoReleasesTheCard() {
        assessed();
        out();
        EngineeringOrder answer = UnitOfWork.apply(database, unit -> {
            Orders.send(unit, FIRST, SENT);
            return Orders.reject(unit, FIRST, ANSWERED, "a.okonkwo");
        });
        assertThat(answer.state()).isSameAs(OrderState.REJECTED);
        assertThat(cardState()).isSameAs(DispositionState.ASSESSED);
    }

    @Test
    void withdrawingReleasesTheCard() {
        assessed();
        out();
        EngineeringOrder answer =
                UnitOfWork.apply(database, unit -> Orders.withdraw(unit, FIRST, ANSWERED, "a.okonkwo"));
        assertThat(answer.state()).isSameAs(OrderState.WITHDRAWN);
        assertThat(cardState()).isSameAs(DispositionState.ASSESSED);
    }

    @Test
    void aCardSupersededWhileTheOrderWasOutIsLeftAlone() {
        assessed(1);
        assessed(2);
        out();
        UnitOfWork.run(database, unit -> {
            DispositionRecord held = unit.dispositions().get(AGAINST);
            unit.dispositions().save(held.movedTo(DispositionState.SUPERSEDED, DAY, "a.okonkwo", "carried elsewhere"));
        });
        UnitOfWork.run(database, unit -> Orders.withdraw(unit, FIRST, ANSWERED, "a.okonkwo"));
        assertThat(cardState()).isSameAs(DispositionState.SUPERSEDED);
    }

    @Test
    void anAnsweredOrderDropsOffTheOpenList() {
        assessed();
        out();
        UnitOfWork.run(database, unit -> {
            Orders.send(unit, FIRST, SENT);
            Orders.reject(unit, FIRST, ANSWERED, "a.okonkwo");
        });
        List<EngineeringOrder> open =
                UnitOfWork.apply(database, unit -> unit.orders().openOrders());
        assertThat(open).isEmpty();
    }

    @Test
    void anApprovedCategoryComesBackForTheCard() {
        assessed();
        out();
        UnitOfWork.run(database, unit -> {
            Orders.send(unit, FIRST, SENT);
            Orders.approve(unit, FIRST, approval(), ANSWERED, "a.okonkwo");
        });
        assertThat(category(ANSWERED)).containsSame(RepairCategory.B);
    }

    @Test
    void anApprovalPastItsValidityNoLongerStands() {
        assessed();
        out();
        UnitOfWork.run(database, unit -> {
            Orders.send(unit, FIRST, SENT);
            Orders.approve(unit, FIRST, approval(ANSWERED), ANSWERED, "a.okonkwo");
        });
        assertThat(category(LocalDate.of(2028, 1, 1))).isEmpty();
    }

    @Test
    void aRejectedOrderGivesNoCategory() {
        assessed();
        out();
        UnitOfWork.run(database, unit -> {
            Orders.send(unit, FIRST, SENT);
            Orders.reject(unit, FIRST, ANSWERED, "a.okonkwo");
        });
        assertThat(category(ANSWERED)).isEmpty();
    }

    @Test
    void aCardWithNoOrdersGivesNoCategory() {
        assessed();
        assertThat(category(ANSWERED)).isEmpty();
    }

    @Test
    void anOrderThatIsNotOnFileCannotBeSent() {
        UnitOfWork.run(database, unit -> assertThatThrownBy(() -> Orders.send(unit, new OrderId(2025, 9), SENT))
                .isInstanceOf(NotOnFile.class));
    }
}
