package io.github.rkaushikdev.doublerbay.store;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.disposition.EngineeringOrder;
import io.github.rkaushikdev.doublerbay.disposition.OrderId;
import io.github.rkaushikdev.doublerbay.disposition.OrderState;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Questions out to the manufacturer, and whatever came back.
 *
 * <p>An order is replaced rather than mutated. The domain object walks its own state machine and
 * hands back a new one, so the repository's job is to put the whole thing down again, and a move the
 * machine refused never reaches here at all.
 */
public final class OrderRepository {

    private final EntityManager session;

    OrderRepository(EntityManager session) {
        this.session = session;
    }

    /** Put a new order on file, refusing a number already used. */
    public void raiseOrder(EngineeringOrder order) {
        if (row(order.reference()) != null) {
            throw new BadReading(order.reference() + " has already been raised");
        }
        session.persist(Decisions.orderToRow(order));
    }

    /** Put a moved order down again, replacing the row it came off. */
    public void save(EngineeringOrder order) {
        OrderRow held = row(order.reference());
        if (held == null) {
            throw new NotOnFile(order.reference() + " is not an order on file");
        }
        session.remove(held);
        session.flush();
        session.persist(Decisions.orderToRow(order));
    }

    /** One order by its number. */
    public EngineeringOrder get(OrderId reference) {
        OrderRow held = row(reference);
        if (held == null) {
            throw new NotOnFile(reference + " is not an order on file");
        }
        return Decisions.rowToOrder(held);
    }

    /** Everything still waiting on an answer, oldest first. */
    public List<EngineeringOrder> openOrders() {
        List<String> open = Arrays.stream(OrderState.values())
                .filter(OrderState::isOpen)
                .map(OrderState::value)
                .toList();
        return session
                .createQuery("select o from OrderRow o where o.state in :open order by o.raised", OrderRow.class)
                .setParameter("open", open)
                .getResultList()
                .stream()
                .map(Decisions::rowToOrder)
                .toList();
    }

    /** Every order raised against one card, oldest first. */
    public List<EngineeringOrder> against(FindingId reference) {
        return session
                .createQuery("select o from OrderRow o where o.against = :against order by o.raised", OrderRow.class)
                .setParameter("against", reference)
                .getResultList()
                .stream()
                .map(Decisions::rowToOrder)
                .toList();
    }

    /** The ones that have been out too long, longest first, then in number order. */
    public List<EngineeringOrder> needingChase(LocalDate today) {
        List<EngineeringOrder> waiting = new ArrayList<>(
                openOrders().stream().filter(one -> one.needsChasing(today)).toList());
        waiting.sort(Comparator.comparingInt((EngineeringOrder one) -> -one.daysWaiting(today))
                .thenComparing(one -> one.reference().toString()));
        return List.copyOf(waiting);
    }

    /** The next order number in a year, which this side allocates. */
    public OrderId nextReference(int year) {
        int highest = 0;
        for (OrderRow row :
                session.createQuery("select o from OrderRow o", OrderRow.class).getResultList()) {
            OrderId used = OrderId.parse(row.getReference());
            if (used.year() == year) {
                highest = Math.max(highest, used.sequence());
            }
        }
        return new OrderId(year, highest + 1);
    }

    private OrderRow row(OrderId reference) {
        return session.find(OrderRow.class, reference.toString());
    }
}
