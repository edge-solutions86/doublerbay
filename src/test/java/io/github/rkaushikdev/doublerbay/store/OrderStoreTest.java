package io.github.rkaushikdev.doublerbay.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.disposition.EngineeringOrder;
import io.github.rkaushikdev.doublerbay.disposition.OrderId;
import io.github.rkaushikdev.doublerbay.disposition.OrderState;
import io.github.rkaushikdev.doublerbay.disposition.RepairApproval;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.repair.RepairCategory;
import io.github.rkaushikdev.doublerbay.support.Desk;
import io.github.rkaushikdev.doublerbay.support.Sample;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderStoreTest {

    private static final LocalDate DAY = LocalDate.of(2025, 3, 14);
    private static final LocalDate LATER = LocalDate.of(2025, 4, 2);
    private static final FindingId AGAINST = new FindingId(2025, 1);
    private static final OrderId FIRST = new OrderId(2025, 1);
    private static final String QUESTION = "dent in the chem mill land at FR15, past the allowable, what may we do";

    private Database database;

    @BeforeEach
    void oneCardRaised() {
        database = Desk.stocked();
        UnitOfWork.run(database, unit -> unit.findings().raiseFinding(Sample.card(1)));
    }

    private static EngineeringOrder order(int sequence, FindingId against, LocalDate raised) {
        return new EngineeringOrder(new OrderId(2025, sequence), against, QUESTION, raised);
    }

    private static EngineeringOrder order(int sequence, LocalDate raised) {
        return order(sequence, AGAINST, raised);
    }

    private static EngineeringOrder order(int sequence) {
        return order(sequence, DAY);
    }

    private static RepairApproval approval(List<String> conditions) {
        return RepairApproval.of("RAS-2025-4417", RepairCategory.B, conditions, LocalDate.of(2027, 1, 1));
    }

    private static RepairApproval approval() {
        return approval(List.of("inspect at every C check"));
    }

    private void raised(EngineeringOrder one) {
        UnitOfWork.run(database, unit -> unit.orders().raiseOrder(one));
    }

    private void raised() {
        raised(order(1));
    }

    private EngineeringOrder held() {
        return UnitOfWork.apply(database, unit -> unit.orders().get(FIRST));
    }

    private void approvedWith(RepairApproval sheet) {
        UnitOfWork.run(database, unit -> {
            EngineeringOrder sent = unit.orders().get(FIRST).sentOn(LATER);
            unit.orders().save(sent.approved(sheet, LocalDate.of(2025, 6, 1)));
        });
    }

    @Test
    void anOrderGoesInAndComesBack() {
        raised();
        EngineeringOrder held = held();
        assertThat(held.against()).isEqualTo(AGAINST);
        assertThat(held.question()).isEqualTo(QUESTION);
        assertThat(held.state()).isSameAs(OrderState.RAISED);
    }

    @Test
    void anOrderRaisedTwiceIsRefused() {
        raised();
        assertThatThrownBy(this::raised).isInstanceOf(BadReading.class).hasMessageContaining("already been raised");
    }

    @Test
    void anOrderThatIsNotOnFileIsRefused() {
        assertThatThrownBy(() -> UnitOfWork.run(database, unit -> unit.orders().get(new OrderId(2025, 9))))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("not an order on file");
    }

    @Test
    void aMovedOrderReplacesTheRowItCameOff() {
        raised();
        UnitOfWork.run(
                database, unit -> unit.orders().save(unit.orders().get(FIRST).sentOn(LATER)));
        EngineeringOrder held = held();
        assertThat(held.state()).isSameAs(OrderState.SENT);
        assertThat(held.sent()).contains(LATER);
    }

    @Test
    void savingOneThatWasNeverRaisedIsRefused() {
        assertThatThrownBy(() -> UnitOfWork.run(database, unit -> unit.orders().save(order(9))))
                .isInstanceOf(NotOnFile.class);
    }

    @Test
    void anApprovalSurvivesTheRoundTrip() {
        raised();
        approvedWith(approval());
        RepairApproval held = held().approval().orElseThrow();
        assertThat(held.sheet()).isEqualTo("RAS-2025-4417");
        assertThat(held.category()).isSameAs(RepairCategory.B);
        assertThat(held.validUntil()).contains(LocalDate.of(2027, 1, 1));
    }

    @Test
    void theConditionsSurviveTheRoundTrip() {
        raised();
        List<String> every = List.of("inspect at every C check", "do not extend past the next heavy");
        approvedWith(approval(every));
        assertThat(held().approval().orElseThrow().conditions()).isEqualTo(every);
    }

    @Test
    void anApprovalWithNoConditionsComesBackEmpty() {
        raised();
        approvedWith(approval(List.of()));
        assertThat(held().approval().orElseThrow().conditions()).isEmpty();
    }

    @Test
    void conditionsFoldOntoOneColumnAndBack() {
        String text = Decisions.conditionsToText(List.of("one", "two")).orElseThrow();
        assertThat(Decisions.conditionsFromText(text)).containsExactly("one", "two");
    }

    @Test
    void noConditionsStoreAsNothing() {
        assertThat(Decisions.conditionsToText(List.of())).isEmpty();
        assertThat(Decisions.conditionsToText(List.of("  "))).isEmpty();
        assertThat(Decisions.conditionsFromText(null)).isEmpty();
    }

    @Test
    void aBlankLineInTheStoredColumnIsDropped() {
        assertThat(Decisions.conditionsFromText("one\n\ntwo")).containsExactly("one", "two");
    }

    @Test
    void aConditionThatIsNotOneLineIsRefused() {
        assertThatThrownBy(() -> Decisions.conditionsToText(List.of("one\ntwo")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("one line");
    }

    @Test
    void aRowWithHalfAnApprovalIsRefused() {
        OrderRow row = new OrderRow()
                .setReference("EO-2025-0001")
                .setAgainst(AGAINST)
                .setQuestion(QUESTION)
                .setRaised(DAY)
                .setState("approved")
                .setSheet("RAS-2025-4417")
                .setApprovedCategory(null);
        assertThatThrownBy(() -> Decisions.rowToOrder(row))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("half an approval");
    }

    @Test
    void theOpenOrdersComeBackOldestFirst() {
        raised(order(1, LATER));
        raised(order(2, DAY));
        List<EngineeringOrder> open =
                UnitOfWork.apply(database, unit -> unit.orders().openOrders());
        assertThat(open).extracting(one -> one.reference().sequence()).containsExactly(2, 1);
    }

    @Test
    void anAnsweredOrderDropsOffTheOpenList() {
        raised();
        UnitOfWork.run(database, unit -> {
            EngineeringOrder sent = unit.orders().get(FIRST).sentOn(LATER);
            unit.orders().save(sent.rejected(LocalDate.of(2025, 6, 1)));
        });
        List<EngineeringOrder> open =
                UnitOfWork.apply(database, unit -> unit.orders().openOrders());
        assertThat(open).isEmpty();
    }

    @Test
    void theOrdersAgainstOneCardComeBackTogether() {
        raised(order(1));
        raised(order(2, new FindingId(2025, 2), LATER));
        List<EngineeringOrder> against =
                UnitOfWork.apply(database, unit -> unit.orders().against(AGAINST));
        assertThat(against).hasSize(1);
    }

    @Test
    void aCardWithNoOrdersComesBackEmpty() {
        List<EngineeringOrder> against =
                UnitOfWork.apply(database, unit -> unit.orders().against(AGAINST));
        assertThat(against).isEmpty();
    }

    @Test
    void anOrderOutALongTimeNeedsChasing() {
        raised();
        UnitOfWork.run(
                database, unit -> unit.orders().save(unit.orders().get(FIRST).sentOn(DAY)));
        List<EngineeringOrder> chasing =
                UnitOfWork.apply(database, unit -> unit.orders().needingChase(LocalDate.of(2026, 1, 1)));
        assertThat(chasing).hasSize(1);
    }

    @Test
    void anOrderJustSentDoesNotNeedChasing() {
        raised();
        UnitOfWork.run(
                database, unit -> unit.orders().save(unit.orders().get(FIRST).sentOn(DAY)));
        List<EngineeringOrder> chasing =
                UnitOfWork.apply(database, unit -> unit.orders().needingChase(DAY));
        assertThat(chasing).isEmpty();
    }

    @Test
    void theChaseListPutsTheLongestWaitFirst() {
        raised(order(1, LocalDate.of(2024, 1, 1)));
        raised(order(2, new FindingId(2025, 2), DAY));
        UnitOfWork.run(database, unit -> {
            unit.orders().save(unit.orders().get(FIRST).sentOn(LocalDate.of(2024, 1, 1)));
            unit.orders().save(unit.orders().get(new OrderId(2025, 2)).sentOn(DAY));
        });
        List<EngineeringOrder> chasing =
                UnitOfWork.apply(database, unit -> unit.orders().needingChase(LocalDate.of(2026, 1, 1)));
        assertThat(chasing).extracting(one -> one.reference().sequence()).containsExactly(1, 2);
    }

    @Test
    void theNextReferenceStartsAtOne() {
        OrderId next = UnitOfWork.apply(database, unit -> unit.orders().nextReference(2025));
        assertThat(next).isEqualTo(new OrderId(2025, 1));
    }

    @Test
    void theNextReferenceFollowsWhatIsAlreadyThere() {
        raised();
        OrderId next = UnitOfWork.apply(database, unit -> unit.orders().nextReference(2025));
        assertThat(next).isEqualTo(new OrderId(2025, 2));
    }

    @Test
    void theNextReferenceStartsAgainInANewYear() {
        raised();
        OrderId next = UnitOfWork.apply(database, unit -> unit.orders().nextReference(2026));
        assertThat(next).isEqualTo(new OrderId(2026, 1));
    }
}
