package io.github.rkaushikdev.doublerbay.api;

import io.github.rkaushikdev.doublerbay.disposition.EngineeringOrder;
import io.github.rkaushikdev.doublerbay.disposition.OrderId;
import io.github.rkaushikdev.doublerbay.disposition.RepairApproval;
import io.github.rkaushikdev.doublerbay.service.Orders;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The routes for questions out to the manufacturer.
 *
 * <p>Every state move is its own route rather than one endpoint taking a target state. A caller that
 * can post any state can post the ones the machine would refuse, and the refusal then reads as a
 * validation error on a field instead of as what it is, which is that the order has not been sent
 * yet.
 *
 * <p>The chase list is the one anybody actually opens. An order sitting unanswered for four months is
 * a card the desk cannot close, and nothing else on this service will surface it.
 */
@RestController
@Tag(name = "orders")
final class OrderRoutes {

    private final Deps deps;

    OrderRoutes(Deps deps) {
        this.deps = deps;
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires(Action.DISPOSITION)
    @Operation(summary = "Send a card out and hold it until the answer lands.")
    OrderOut raiseOne(@Valid @RequestBody RaiseIn body) {
        return deps.apply(unit -> orderOut(Orders.raiseOrder(
                        unit, Dependencies.referenceFrom(body.against()), body.question(), body.raised(), body.by())
                .order()));
    }

    @GetMapping("/orders")
    @Operation(summary = "Everything still waiting on an answer, oldest first.")
    Page<OrderOut> openOrders(
            @RequestParam(name = "offset", required = false) Integer offset,
            @RequestParam(name = "limit", required = false) Integer limit) {
        Window asked = Paging.window(offset, limit);
        return deps.apply(unit -> Paging.paged(unit.orders().openOrders(), asked, OrderRoutes::orderOut));
    }

    @GetMapping("/orders/chase")
    @Operation(summary = "The ones that have been out too long, longest first.")
    List<ChasedOut> chase(@RequestParam(name = "on") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate on) {
        return deps.apply(unit -> unit.orders().needingChase(on).stream()
                .map(one -> new ChasedOut(orderOut(one), one.daysWaiting(on)))
                .toList());
    }

    @GetMapping("/orders/{reference}")
    @Operation(summary = "One order and whatever came back.")
    OrderOut readOrder(@PathVariable("reference") String reference) {
        return deps.apply(unit -> orderOut(unit.orders().get(Dependencies.orderFrom(reference))));
    }

    @PostMapping("/orders/{reference}/sent")
    @Requires(Action.DISPOSITION)
    @Operation(summary = "Record the day it actually went, which is not the day it was written.")
    OrderOut markSent(@PathVariable("reference") String reference, @Valid @RequestBody SentIn body) {
        return deps.apply(unit -> orderOut(Orders.send(unit, Dependencies.orderFrom(reference), body.on())));
    }

    @PostMapping("/orders/{reference}/acknowledged")
    @Requires(Action.DISPOSITION)
    @Operation(summary = "Record that the manufacturer has it, which stops the chase clock.")
    OrderOut markAcknowledged(@PathVariable("reference") String reference) {
        return deps.apply(unit -> orderOut(Orders.acknowledge(unit, Dependencies.orderFrom(reference))));
    }

    @PostMapping("/orders/{reference}/approved")
    @Requires(Action.DISPOSITION)
    @Operation(summary = "Record an approval and put the card back where a repair can be raised.")
    OrderOut markApproved(@PathVariable("reference") String reference, @Valid @RequestBody ApproveIn body) {
        return deps.apply(unit -> {
            OrderId held = Dependencies.orderFrom(reference);
            RepairApproval approval = body.toDomain();
            return orderOut(Orders.approve(unit, held, approval, body.on(), body.by()));
        });
    }

    @PostMapping("/orders/{reference}/rejected")
    @Requires(Action.DISPOSITION)
    @Operation(summary = "Record a rejection, which still releases the card.")
    OrderOut markRejected(@PathVariable("reference") String reference, @Valid @RequestBody AnsweredIn body) {
        return deps.apply(
                unit -> orderOut(Orders.reject(unit, Dependencies.orderFrom(reference), body.on(), body.by())));
    }

    @PostMapping("/orders/{reference}/withdrawn")
    @Requires(Action.DISPOSITION)
    @Operation(summary = "Take a question back, releasing the card the same way an answer does.")
    OrderOut markWithdrawn(@PathVariable("reference") String reference, @Valid @RequestBody AnsweredIn body) {
        return deps.apply(
                unit -> orderOut(Orders.withdraw(unit, Dependencies.orderFrom(reference), body.on(), body.by())));
    }

    /** One order as it goes back, with its sheet when it has one. */
    static OrderOut orderOut(EngineeringOrder order) {
        return new OrderOut(
                order.reference().toString(),
                order.against().toString(),
                order.question(),
                order.raised(),
                order.state().value(),
                order.sent(),
                order.answered(),
                order.isOpen(),
                order.approval()
                        .map(approval -> new ApprovalOut(
                                approval.sheet(),
                                approval.category().value(),
                                approval.conditions(),
                                approval.validUntil())));
    }
}
