package io.github.rkaushikdev.doublerbay.disposition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.OutOfSequence;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.repair.RepairCategory;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EngineeringTest {

    private static final FindingId DR = new FindingId(2025, 4182);
    private static final LocalDate RAISED = LocalDate.of(2025, 3, 14);
    private static final LocalDate SENT = LocalDate.of(2025, 3, 17);

    private static EngineeringOrder order(int sequence) {
        return new EngineeringOrder(new OrderId(2025, sequence), DR, "crack past the book at FR41", RAISED);
    }

    private static EngineeringOrder order() {
        return order(1);
    }

    private static RepairApproval approval(String sheet, LocalDate until) {
        return RepairApproval.of(sheet, RepairCategory.B, List.of("inspect at 3000 FC"), until);
    }

    private static RepairApproval approval(LocalDate until) {
        return approval("RAS-2025-0007", until);
    }

    private static RepairApproval approval() {
        return approval(null);
    }

    private static EngineeringQueue queue(EngineeringOrder... orders) {
        EngineeringQueue held = new EngineeringQueue();
        for (EngineeringOrder one : orders.length > 0 ? orders : new EngineeringOrder[] {order()}) {
            held.raiseOrder(one);
        }
        return held;
    }

    private static List<Integer> sequences(List<EngineeringOrder> orders) {
        return orders.stream().map(one -> one.reference().sequence()).toList();
    }

    @Test
    void anOrderNumberReadsAndPrintsPadded() {
        assertThat(new OrderId(2025, 7)).hasToString("EO-2025-0007");
        assertThat(OrderId.parse("EO-2025-0007")).isEqualTo(new OrderId(2025, 7));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "EO-2025-7", "2025-0007", "RAS-2025-0007"})
    void anOrderNumberRefusesAnythingElse(String text) {
        assertThatThrownBy(() -> OrderId.parse(text))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not an engineering order number");
    }

    @Test
    void anOrderYearAndSequenceAreBounded() {
        assertThatThrownBy(() -> new OrderId(1999, 1))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not an order year");
        assertThatThrownBy(() -> new OrderId(2025, 0))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not an order sequence");
    }

    @Test
    void anOrderAsksSomething() {
        assertThatThrownBy(() -> new EngineeringOrder(new OrderId(2025, 1), DR, "  ", RAISED))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("an order asks something");
    }

    @Test
    void anOrderReadsBackWhatItIsAgainstAndWhereItIs() {
        assertThat(order()).hasToString("EO-2025-0001 against DR-2025-004182, raised");
    }

    @Test
    void theOpenStatesAreTheOnesStillWaiting() {
        assertThat(OrderState.RAISED.isOpen()).isTrue();
        assertThat(OrderState.SENT.isOpen()).isTrue();
        assertThat(OrderState.ACKNOWLEDGED.isOpen()).isTrue();
        assertThat(OrderState.APPROVED.isOpen()).isFalse();
        assertThat(OrderState.REJECTED.isOpen()).isFalse();
    }

    @Test
    void approvedAndRejectedAreTheTwoAnswers() {
        assertThat(OrderState.APPROVED.isAnswered()).isTrue();
        assertThat(OrderState.REJECTED.isAnswered()).isTrue();
        assertThat(OrderState.WITHDRAWN.isAnswered()).isFalse();
    }

    @Test
    void anOrderWalksFromRaisedToApproved() {
        EngineeringOrder walked = order().sentOn(SENT).acknowledged().approved(approval(), LocalDate.of(2025, 4, 10));
        assertThat(walked.state()).isSameAs(OrderState.APPROVED);
        assertThat(walked.approval().orElseThrow().sheet()).isEqualTo("RAS-2025-0007");
        assertThat(walked.isOpen()).isFalse();
    }

    @Test
    void anOrderCanBeApprovedWithoutBeingAcknowledgedFirst() {
        EngineeringOrder walked = order().sentOn(SENT).approved(approval(), LocalDate.of(2025, 4, 10));
        assertThat(walked.state()).isSameAs(OrderState.APPROVED);
    }

    @Test
    void anOrderCannotBeApprovedBeforeItIsSent() {
        assertThatThrownBy(() -> order().approved(approval(), LocalDate.of(2025, 4, 10)))
                .isInstanceOf(OutOfSequence.class)
                .hasMessageContaining("cannot go from raised to approved");
    }

    @Test
    void anApprovedOrderGoesNowhereElse() {
        EngineeringOrder done = order().sentOn(SENT).approved(approval(), LocalDate.of(2025, 4, 10));
        assertThatThrownBy(() -> done.withdrawn(LocalDate.of(2025, 5, 1))).isInstanceOf(OutOfSequence.class);
    }

    @Test
    void aRejectedOrderCanBeRaisedAgain() {
        EngineeringOrder turnedDown = order().sentOn(SENT).rejected(LocalDate.of(2025, 4, 10));
        assertThat(turnedDown.state()).isSameAs(OrderState.REJECTED);
        EngineeringOrder again = turnedDown.raisedAgain();
        assertThat(again.state()).isSameAs(OrderState.RAISED);
        assertThat(again.sent()).isEmpty();
        assertThat(again.answered()).isEmpty();
    }

    @Test
    void anApprovedOrderHasToCarryTheSheet() {
        assertThatThrownBy(() -> EngineeringOrder.of(
                        new OrderId(2025, 1), DR, "question", RAISED, OrderState.APPROVED, SENT, RAISED, null))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("carries an approval sheet");
    }

    @Test
    void aSheetNumberHasToLookLikeOne() {
        assertThatThrownBy(() -> new RepairApproval("EO-2025-0007", RepairCategory.B))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not a repair approval sheet number");
    }

    @Test
    void anApprovalReadsBackItsSheetAndCategory() {
        assertThat(approval()).hasToString("RAS-2025-0007 category B");
        RepairApproval dated = approval(LocalDate.of(2027, 1, 1));
        assertThat(dated.toString()).endsWith("expires 2027-01-01");
    }

    @Test
    void anApprovalWithNoExpiryNeverRunsOut() {
        assertThat(approval().isValidOn(LocalDate.of(2099, 1, 1))).isTrue();
    }

    @Test
    void anApprovalWithAnExpiryStopsBeingValidAfterIt() {
        RepairApproval dated = approval(LocalDate.of(2027, 1, 1));
        assertThat(dated.isValidOn(LocalDate.of(2027, 1, 1))).isTrue();
        assertThat(dated.isValidOn(LocalDate.of(2027, 1, 2))).isFalse();
    }

    @Test
    void theWaitCountsFromWhenItWasRaisedUntilItIsSent() {
        assertThat(order().daysWaiting(LocalDate.of(2025, 3, 20))).isEqualTo(6);
    }

    @Test
    void theWaitCountsFromWhenItWasSentOnceItHasBeen() {
        assertThat(order().sentOn(SENT).daysWaiting(LocalDate.of(2025, 3, 20))).isEqualTo(3);
    }

    @Test
    void theWaitStopsWhenTheAnswerArrives() {
        EngineeringOrder answered = order().sentOn(SENT).approved(approval(), LocalDate.of(2025, 4, 10));
        assertThat(answered.daysWaiting(LocalDate.of(2099, 1, 1))).isEqualTo(24);
    }

    @Test
    void anOrderSentAndNotAcknowledgedNeedsChasingAfterTheTurnaround() {
        EngineeringOrder sent = order().sentOn(SENT);
        assertThat(sent.needsChasing(LocalDate.of(2025, 3, 22))).isFalse();
        assertThat(sent.needsChasing(LocalDate.of(2025, 3, 23))).isTrue();
    }

    @Test
    void anAcknowledgedOrderDoesNotNeedChasing() {
        assertThat(order().sentOn(SENT).acknowledged().needsChasing(LocalDate.of(2025, 6, 1)))
                .isFalse();
    }

    @Test
    void anUnsentOrderDoesNotNeedChasing() {
        assertThat(order().needsChasing(LocalDate.of(2099, 1, 1))).isFalse();
    }

    @Test
    void theQueueCountsWhatIsOnItAndWhatIsOpen() {
        EngineeringQueue held = queue(order(1), order(2));
        assertThat(held).hasToString("2 orders, 2 open");
        assertThat(held.size()).isEqualTo(2);
    }

    @Test
    void anOrderRaisedTwiceIsRefused() {
        EngineeringQueue held = queue(order(1));
        assertThatThrownBy(() -> held.raiseOrder(order(1)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("has already been raised");
    }

    @Test
    void anOrderNotOnTheQueueIsALookupMiss() {
        assertThatThrownBy(() -> queue().order(new OrderId(2025, 9)))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("is not on the queue");
        assertThatThrownBy(() -> queue().replaceOrder(order(9)))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("is not on the queue");
    }

    @Test
    void replacingAnOrderKeepsTheNewState() {
        EngineeringQueue held = queue(order(1));
        held.replaceOrder(held.order(new OrderId(2025, 1)).sentOn(SENT));
        assertThat(held.order(new OrderId(2025, 1)).state()).isSameAs(OrderState.SENT);
    }

    @Test
    void theChaseListIsTheOnesNobodyHasAnswered() {
        EngineeringQueue held = queue(order(1), order(2));
        held.replaceOrder(held.order(new OrderId(2025, 1)).sentOn(SENT));
        assertThat(sequences(held.needingChase(LocalDate.of(2025, 4, 1)))).containsExactly(1);
    }

    @Test
    void ordersCanBePulledOutByTheCardTheyAreAgainst() {
        EngineeringQueue held = queue(order(1), order(2));
        assertThat(held.against(DR)).hasSize(2);
        assertThat(held.against(new FindingId(2025, 9999))).isEqualTo(List.of());
    }

    @Test
    void theNextNumberCarriesOnFromTheHighestUsedThatYear() {
        EngineeringQueue held = queue(order(1), order(7));
        assertThat(held.nextReference(2025)).isEqualTo(new OrderId(2025, 8));
        assertThat(held.nextReference(2026)).isEqualTo(new OrderId(2026, 1));
    }
}
