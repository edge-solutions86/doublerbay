package io.github.rkaushikdev.doublerbay.service;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.OutOfSequence;
import io.github.rkaushikdev.doublerbay.disposition.DispositionRecord;
import io.github.rkaushikdev.doublerbay.disposition.DispositionState;
import io.github.rkaushikdev.doublerbay.disposition.EngineeringOrder;
import io.github.rkaushikdev.doublerbay.disposition.OrderId;
import io.github.rkaushikdev.doublerbay.disposition.OrderState;
import io.github.rkaushikdev.doublerbay.disposition.RepairApproval;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.repair.RepairCategory;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Raising a question to the manufacturer, and doing what the answer says.
 *
 * <p>A card the book has no allowable for is not a card the desk can decide. It goes out to the type
 * certificate holder as an engineering order, and the card sits held until the answer comes back.
 * Holding it is the point: a card that stayed assessed would keep appearing on the list of things
 * somebody could disposition, and somebody eventually would.
 *
 * <p>When the answer comes back approved it carries a repair category, and that category is what the
 * shop is allowed to fit. So approving an order puts the card back to assessed, which is where a
 * repair can be raised against it. A rejection does the same, because a rejection is still an answer
 * and the desk now has to find another way.
 *
 * <p>The question text is the operator's, not this class's. Generating it from a template would
 * produce four hundred identical letters and the manufacturer would start answering them all the
 * same way.
 */
public final class Orders {

    /** The shortest question worth sending, in characters. Anything shorter is a subject line. */
    public static final int SHORTEST_QUESTION = 20;

    private Orders() {}

    /** Send a card out to the manufacturer and hold it until the answer lands. */
    public static Raised raiseOrder(UnitOfWork unit, FindingId against, String question, LocalDate on, String by) {
        String asked = Text.strip(question);
        if (asked.codePointCount(0, asked.length()) < SHORTEST_QUESTION) {
            throw new BadReading("a question to the manufacturer says what is being asked");
        }
        unit.findings().latest(against);
        DispositionRecord held = unit.dispositions().get(against);
        if (held.state() == DispositionState.HELD) {
            throw new OutOfSequence(against + " is already out to the manufacturer");
        }

        EngineeringOrder order = new EngineeringOrder(unit.orders().nextReference(on.getYear()), against, asked, on);
        unit.orders().raiseOrder(order);
        unit.dispositions().save(held.movedTo(DispositionState.HELD, on, by, "out as " + order.reference()));
        unit.flush();
        return new Raised(order, DispositionState.HELD);
    }

    /** Record that the order actually went out, which is not when it was written. */
    public static EngineeringOrder send(UnitOfWork unit, OrderId reference, LocalDate on) {
        EngineeringOrder sent = unit.orders().get(reference).sentOn(on);
        unit.orders().save(sent);
        unit.flush();
        return sent;
    }

    /** Record that the manufacturer has it, which stops the chase clock. */
    public static EngineeringOrder acknowledge(UnitOfWork unit, OrderId reference) {
        EngineeringOrder seen = unit.orders().get(reference).acknowledged();
        unit.orders().save(seen);
        unit.flush();
        return seen;
    }

    /** Record an approval and put the card back where a repair can be raised. */
    public static EngineeringOrder approve(
            UnitOfWork unit, OrderId reference, RepairApproval approval, LocalDate on, String by) {
        EngineeringOrder answered = unit.orders().get(reference).approved(approval, on);
        unit.orders().save(answered);
        release(unit, answered, on, by, "approved on " + approval.sheet());
        unit.flush();
        return answered;
    }

    /** Record a rejection, which still releases the card because it is an answer. */
    public static EngineeringOrder reject(UnitOfWork unit, OrderId reference, LocalDate on, String by) {
        EngineeringOrder answered = unit.orders().get(reference).rejected(on);
        unit.orders().save(answered);
        release(unit, answered, on, by, "rejected, another way needed");
        unit.flush();
        return answered;
    }

    /** Take a question back, which releases the card the same way an answer does. */
    public static EngineeringOrder withdraw(UnitOfWork unit, OrderId reference, LocalDate on, String by) {
        EngineeringOrder pulled = unit.orders().get(reference).withdrawn(on);
        unit.orders().save(pulled);
        release(unit, pulled, on, by, "question withdrawn");
        unit.flush();
        return pulled;
    }

    /**
     * Put a held card back to assessed, and leave one that moved on alone.
     *
     * <p>A card can be superseded while its order is out, and superseding is terminal. Forcing it back
     * to assessed because a letter arrived would resurrect a card the desk deliberately closed.
     */
    private static void release(UnitOfWork unit, EngineeringOrder order, LocalDate on, String by, String note) {
        DispositionRecord held = unit.dispositions().get(order.against());
        if (held.state() != DispositionState.HELD) {
            return;
        }
        unit.dispositions().save(held.movedTo(DispositionState.ASSESSED, on, by, note));
    }

    /** The category the manufacturer allowed for this card, if any still stands. */
    public static Optional<RepairCategory> approvedCategory(UnitOfWork unit, FindingId against, LocalDate on) {
        for (EngineeringOrder order : unit.orders().against(against)) {
            Optional<RepairApproval> approval = order.approval();
            if (order.state() == OrderState.APPROVED
                    && approval.isPresent()
                    && approval.get().isValidOn(on)) {
                return Optional.of(approval.get().category());
            }
        }
        return Optional.empty();
    }
}
