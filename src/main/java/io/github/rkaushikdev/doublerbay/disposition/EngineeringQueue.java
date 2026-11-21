package io.github.rkaushikdev.doublerbay.disposition;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Every question out to the manufacturer, and which of them need chasing.
 *
 * <p>The queue exists to make the list of phone calls somebody has to make rather than to store
 * anything clever. Orders come back in order number order.
 */
public final class EngineeringQueue implements Iterable<EngineeringOrder> {

    private final Map<OrderId, EngineeringOrder> orders = new HashMap<>();

    /** An empty queue. */
    public EngineeringQueue() {}

    /**
     * Put a new order on the queue.
     *
     * @throws BadReading when an order under that number is already on it
     */
    public void raiseOrder(EngineeringOrder order) {
        if (orders.containsKey(order.reference())) {
            throw new BadReading(order.reference() + " has already been raised");
        }
        orders.put(order.reference(), order);
    }

    /**
     * The order under a number.
     *
     * @throws NotOnFile when it is not on the queue
     */
    public EngineeringOrder order(OrderId reference) {
        EngineeringOrder found = orders.get(reference);
        if (found == null) {
            throw new NotOnFile(reference + " is not on the queue");
        }
        return found;
    }

    /**
     * Put an order's new state in place of its old one.
     *
     * @throws NotOnFile when it is not on the queue
     */
    public void replaceOrder(EngineeringOrder order) {
        if (!orders.containsKey(order.reference())) {
            throw new NotOnFile(order.reference() + " is not on the queue");
        }
        orders.put(order.reference(), order);
    }

    /** The orders still waiting on somebody. */
    public List<EngineeringOrder> openOrders() {
        return ordered().stream().filter(EngineeringOrder::isOpen).toList();
    }

    /** The orders sent, not acknowledged, and past the turnaround. */
    public List<EngineeringOrder> needingChase(LocalDate today) {
        return ordered().stream().filter(one -> one.needsChasing(today)).toList();
    }

    /** The orders raised against one card. */
    public List<EngineeringOrder> against(FindingId finding) {
        return ordered().stream().filter(one -> one.against().equals(finding)).toList();
    }

    /** The number after the highest used in a year, or the first of the year. */
    public OrderId nextReference(int year) {
        int highest = orders.keySet().stream()
                .filter(one -> one.year() == year)
                .mapToInt(OrderId::sequence)
                .max()
                .orElse(0);
        return new OrderId(year, highest + 1);
    }

    private List<EngineeringOrder> ordered() {
        return orders.values().stream()
                .sorted(Comparator.comparing(one -> one.reference().toString()))
                .toList();
    }

    /** Every order, in order number order. */
    @Override
    public Iterator<EngineeringOrder> iterator() {
        return ordered().iterator();
    }

    /** How many orders are on the queue. */
    public int size() {
        return orders.size();
    }

    /** {@code 2 orders, 2 open}. */
    @Override
    public String toString() {
        return orders.size() + " orders, " + openOrders().size() + " open";
    }
}
