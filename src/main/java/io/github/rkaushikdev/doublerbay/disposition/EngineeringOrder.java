package io.github.rkaushikdev.doublerbay.disposition;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.OutOfSequence;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/**
 * One question to the manufacturer and whatever came back.
 *
 * <p>When the book runs out the operator cannot invent a repair. The question goes to the type
 * certificate holder as an engineering order, and what comes back is a repair approval sheet: a
 * scheme, a category, and usually a set of conditions and a date it stops being valid. Until that
 * arrives the aeroplane is either on the ground or flying under a deferral, and both of those are
 * expensive.
 *
 * <p>Which makes the turnaround the number the desk actually watches. An order sent on Tuesday and
 * acknowledged on Wednesday is being worked. An order sent three weeks ago with no acknowledgement is
 * a phone call somebody has to make.
 *
 * <p>Every move hands back a new order and leaves the one it came from as it was, refusing any move
 * {@link OrderState} does not allow.
 */
public record EngineeringOrder(
        OrderId reference,
        FindingId against,
        String question,
        LocalDate raised,
        OrderState state,
        Optional<LocalDate> sent,
        Optional<LocalDate> answered,
        Optional<RepairApproval> approval) {

    /** How long the manufacturer has to acknowledge before somebody chases it, in days. */
    public static final int ACKNOWLEDGE_WITHIN_DAYS = 5;

    /** An order that asks something, and that carries its sheet if it is approved. */
    public EngineeringOrder {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(against, "against");
        Objects.requireNonNull(question, "question");
        Objects.requireNonNull(raised, "raised");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(sent, "sent");
        Objects.requireNonNull(answered, "answered");
        Objects.requireNonNull(approval, "approval");
        if (Text.strip(question).isEmpty()) {
            throw new BadReading(reference + ": an order asks something");
        }
        if (state == OrderState.APPROVED && approval.isEmpty()) {
            throw new BadReading(reference + ": an approved order carries an approval sheet");
        }
    }

    /** An order just raised. */
    public EngineeringOrder(OrderId reference, FindingId against, String question, LocalDate raised) {
        this(
                reference,
                against,
                question,
                raised,
                OrderState.RAISED,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    /** An order from what was to hand; a null date or approval is one the order does not have yet. */
    public static EngineeringOrder of(
            OrderId reference,
            FindingId against,
            String question,
            LocalDate raised,
            OrderState state,
            LocalDate sent,
            LocalDate answered,
            RepairApproval approval) {
        return new EngineeringOrder(
                reference,
                against,
                question,
                raised,
                state,
                Optional.ofNullable(sent),
                Optional.ofNullable(answered),
                Optional.ofNullable(approval));
    }

    /** Whether the question is still waiting on somebody. */
    public boolean isOpen() {
        return state.isOpen();
    }

    /**
     * How long the question has been out, counting from when it was sent, or from when it was raised
     * until it is sent, and stopping when the answer arrives.
     */
    public int daysWaiting(LocalDate today) {
        if (sent.isEmpty()) {
            return (int) ChronoUnit.DAYS.between(raised, today);
        }
        LocalDate end = answered.orElse(today);
        return (int) ChronoUnit.DAYS.between(sent.get(), end);
    }

    /** Sent, not acknowledged, and past the turnaround the manufacturer works to. */
    public boolean needsChasing(LocalDate today) {
        if (state != OrderState.SENT || sent.isEmpty()) {
            return false;
        }
        return today.isAfter(sent.get().plusDays(ACKNOWLEDGE_WITHIN_DAYS));
    }

    private EngineeringOrder moved(
            OrderState next,
            Optional<LocalDate> sentOn,
            Optional<LocalDate> answeredOn,
            Optional<RepairApproval> sheet) {
        if (!state.canMoveTo(next)) {
            throw new OutOfSequence(reference + " cannot go from " + state + " to " + next);
        }
        return new EngineeringOrder(reference, against, question, raised, next, sentOn, answeredOn, sheet);
    }

    /** The order sent on a day. */
    public EngineeringOrder sentOn(LocalDate day) {
        return moved(OrderState.SENT, Optional.of(day), answered, approval);
    }

    /** The order acknowledged by the manufacturer. */
    public EngineeringOrder acknowledged() {
        return moved(OrderState.ACKNOWLEDGED, sent, answered, approval);
    }

    /** The order approved on a day, carrying the sheet that came back. */
    public EngineeringOrder approved(RepairApproval sheet, LocalDate on) {
        return moved(OrderState.APPROVED, sent, Optional.of(on), Optional.of(sheet));
    }

    /** The order turned down on a day. */
    public EngineeringOrder rejected(LocalDate on) {
        return moved(OrderState.REJECTED, sent, Optional.of(on), approval);
    }

    /** The order taken back on a day. */
    public EngineeringOrder withdrawn(LocalDate on) {
        return moved(OrderState.WITHDRAWN, sent, Optional.of(on), approval);
    }

    /** A rejected question reworked and put back in, which happens often; it starts again unsent. */
    public EngineeringOrder raisedAgain() {
        return moved(OrderState.RAISED, Optional.empty(), Optional.empty(), Optional.empty());
    }

    /** {@code EO-2025-0001 against DR-2025-004182, raised}. */
    @Override
    public String toString() {
        return reference + " against " + against + ", " + state;
    }
}
