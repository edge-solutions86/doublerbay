package io.github.rkaushikdev.doublerbay.cli;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.disposition.EngineeringOrder;
import io.github.rkaushikdev.doublerbay.disposition.OrderId;
import io.github.rkaushikdev.doublerbay.disposition.RepairApproval;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.repair.RepairCategory;
import io.github.rkaushikdev.doublerbay.service.Orders;
import io.github.rkaushikdev.doublerbay.service.Raised;
import io.github.rkaushikdev.doublerbay.util.Literal;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Questions to the manufacturer, from the terminal.
 *
 * <p>The question itself comes off a file rather than an option. It runs to a paragraph, it contains
 * the measurements, and getting a paragraph through a shell without something eating a quote is a
 * losing game. So it is written in an editor and handed over as a path.
 *
 * <p>The chase list is what this exists for. An order sitting unanswered is a card the desk cannot
 * close, and unlike a deferral there is no limit counting down to make it visible. Somebody has to
 * go and look, so it is one command.
 */
public final class Ordering {

    /** The letters a repair approval sheet carries, in order. */
    public static final List<String> CATEGORIES =
            Arrays.stream(RepairCategory.values()).map(RepairCategory::name).toList();

    private Ordering() {}

    /**
     * Read a category off the letter the sheet carries rather than the sentence.
     *
     * @throws Misused for a letter that is not one of {@link #CATEGORIES}
     */
    public static RepairCategory categoryNamed(String text) {
        String name = Text.upper(Text.strip(text));
        for (RepairCategory one : RepairCategory.values()) {
            if (one.name().equals(name)) {
                return one;
            }
        }
        throw Misused.badValue(Literal.of(text) + " is not one of " + String.join(", ", CATEGORIES));
    }

    /**
     * Read the question off a file, refusing an empty one.
     *
     * <p>The file is read as UTF-8 wherever this runs. A question to the manufacturer quotes
     * measurements, and a degree sign that survives on one platform and not the other is a letter
     * that goes out wrong.
     */
    public static String readQuestion(Path path) {
        String text = Text.strip(Texts.read(path));
        if (text.isEmpty()) {
            throw new BadReading(path + " has no question in it");
        }
        return text;
    }

    /** One condition per line, blank lines dropped. */
    public static List<String> conditionsFrom(Path pathOrNull) {
        if (pathOrNull == null) {
            return List.of();
        }
        List<String> conditions = new ArrayList<>();
        for (String one : Text.lines(Texts.read(pathOrNull))) {
            if (!Text.strip(one).isEmpty()) {
                conditions.add(Text.strip(one));
            }
        }
        return List.copyOf(conditions);
    }

    private static String stateLine(EngineeringOrder answer) {
        return answer.reference() + " " + answer.state();
    }

    /** Engineering orders: raise one, chase it, record the answer. */
    @Command(
            name = "order",
            customSynopsis = "doublerbay order [OPTIONS] COMMAND [ARGS]...",
            description = "Engineering orders: raise one, chase it, record the answer.",
            subcommands = {
                OrderAcknowledged.class,
                OrderApproved.class,
                OrderChase.class,
                OrderList.class,
                OrderRaise.class,
                OrderRejected.class,
                OrderSent.class,
                OrderShow.class,
                OrderWithdraw.class,
            })
    static final class Group {}

    /** Send a card out to the manufacturer and hold it. */
    @Command(
            name = "raise",
            customSynopsis = "doublerbay order raise [OPTIONS] REFERENCE",
            description = "Send a card out to the manufacturer and hold it.")
    static final class OrderRaise extends Step {

        @Parameters(index = "0", paramLabel = "REFERENCE")
        String reference;

        @Option(names = "--question", required = true, converter = Arguments.ExistingPath.class)
        Path path;

        @Option(names = "--on", required = true, description = "yyyy-mm-dd")
        String onDay;

        @Option(names = "--by", required = true)
        String by;

        @Override
        public Integer call() {
            Raised raised = work(unit -> {
                FindingId against = FindingId.parse(reference);
                String question = readQuestion(path);
                return Orders.raiseOrder(unit, against, question, Output.asDate(onDay), by);
            });
            say(raised.toString());
            return Main.OK;
        }
    }

    /** Record the day it actually went out. */
    @Command(
            name = "sent",
            customSynopsis = "doublerbay order sent [OPTIONS] REFERENCE",
            description = "Record the day it actually went out.")
    static final class OrderSent extends Step {

        @Parameters(index = "0", paramLabel = "REFERENCE")
        String reference;

        @Option(names = "--on", required = true)
        String onDay;

        @Override
        public Integer call() {
            EngineeringOrder answer = work(unit -> Orders.send(unit, OrderId.parse(reference), Output.asDate(onDay)));
            say(stateLine(answer));
            return Main.OK;
        }
    }

    /** Record that the manufacturer has it. */
    @Command(
            name = "acknowledged",
            customSynopsis = "doublerbay order acknowledged [OPTIONS] REFERENCE",
            description = "Record that the manufacturer has it.")
    static final class OrderAcknowledged extends Step {

        @Parameters(index = "0", paramLabel = "REFERENCE")
        String reference;

        @Override
        public Integer call() {
            EngineeringOrder answer = work(unit -> Orders.acknowledge(unit, OrderId.parse(reference)));
            say(stateLine(answer));
            return Main.OK;
        }
    }

    /** Record an approval and put the card back where a repair can be raised. */
    @Command(
            name = "approved",
            customSynopsis = "doublerbay order approved [OPTIONS] REFERENCE",
            description = "Record an approval and put the card back where a repair can be raised.")
    static final class OrderApproved extends Step {

        @Parameters(index = "0", paramLabel = "REFERENCE")
        String reference;

        @Option(names = "--sheet", required = true, description = "the repair approval sheet number")
        String sheet;

        @Option(names = "--category", required = true, description = "one of A, B, C")
        String category;

        @Option(names = "--on", required = true)
        String onDay;

        @Option(names = "--by", required = true)
        String by;

        @Option(names = "--valid-until", description = "when the approval runs out")
        String validUntil;

        @Option(
                names = "--conditions",
                converter = Arguments.ExistingPath.class,
                description = "one condition per line")
        Path path;

        @Override
        public Integer call() {
            RepairCategory read = categoryNamed(category);
            List<String> conditions = conditionsFrom(path);
            Optional<LocalDate> runsOut = given(validUntil) ? Optional.of(Output.asDate(validUntil)) : Optional.empty();
            RepairApproval granted = new RepairApproval(sheet, read, conditions, runsOut);
            EngineeringOrder answer =
                    work(unit -> Orders.approve(unit, OrderId.parse(reference), granted, Output.asDate(onDay), by));
            say(answer.reference() + " approved on " + granted.sheet());
            return Main.OK;
        }
    }

    /** Record a rejection, which still releases the card. */
    @Command(
            name = "rejected",
            customSynopsis = "doublerbay order rejected [OPTIONS] REFERENCE",
            description = "Record a rejection, which still releases the card.")
    static final class OrderRejected extends Step {

        @Parameters(index = "0", paramLabel = "REFERENCE")
        String reference;

        @Option(names = "--on", required = true)
        String onDay;

        @Option(names = "--by", required = true)
        String by;

        @Override
        public Integer call() {
            EngineeringOrder answer =
                    work(unit -> Orders.reject(unit, OrderId.parse(reference), Output.asDate(onDay), by));
            say(stateLine(answer));
            return Main.OK;
        }
    }

    /** Take a question back. */
    @Command(
            name = "withdraw",
            customSynopsis = "doublerbay order withdraw [OPTIONS] REFERENCE",
            description = "Take a question back.")
    static final class OrderWithdraw extends Step {

        @Parameters(index = "0", paramLabel = "REFERENCE")
        String reference;

        @Option(names = "--on", required = true)
        String onDay;

        @Option(names = "--by", required = true)
        String by;

        @Override
        public Integer call() {
            EngineeringOrder answer =
                    work(unit -> Orders.withdraw(unit, OrderId.parse(reference), Output.asDate(onDay), by));
            say(stateLine(answer));
            return Main.OK;
        }
    }

    /** Everything still waiting on an answer, oldest first. */
    @Command(
            name = "list",
            customSynopsis = "doublerbay order list [OPTIONS]",
            description = "Everything still waiting on an answer, oldest first.")
    static final class OrderList extends Step {

        @Override
        public Integer call() {
            List<EngineeringOrder> out = work(unit -> unit.orders().openOrders());
            if (out.isEmpty()) {
                say(Output.sayNothingFound("out to the manufacturer"));
                return Main.OK;
            }
            List<List<String>> rows = new ArrayList<>();
            rows.add(List.of("reference", "card", "raised", "state", "sent"));
            for (EngineeringOrder one : out) {
                rows.add(List.of(
                        one.reference().toString(),
                        one.against().toString(),
                        one.raised().toString(),
                        one.state().value(),
                        one.sent().map(LocalDate::toString).orElse("")));
            }
            say(Output.columns(rows));
            return Main.OK;
        }
    }

    /** The orders that have been out too long, longest first. */
    @Command(
            name = "chase",
            customSynopsis = "doublerbay order chase [OPTIONS]",
            description = "The orders that have been out too long, longest first.")
    static final class OrderChase extends Step {

        @Option(names = "--on", required = true, description = "the day to measure the wait against")
        String onDay;

        @Override
        public Integer call() {
            LocalDate day = Output.asDate(onDay);
            List<EngineeringOrder> waiting = work(unit -> unit.orders().needingChase(day));
            if (waiting.isEmpty()) {
                say(Output.sayNothingFound("waiting too long"));
                return Main.OK;
            }
            List<List<String>> rows = new ArrayList<>();
            rows.add(List.of("reference", "card", "sent", "days waiting"));
            for (EngineeringOrder one : waiting) {
                rows.add(List.of(
                        one.reference().toString(),
                        one.against().toString(),
                        one.sent().map(LocalDate::toString).orElse("not sent"),
                        String.valueOf(one.daysWaiting(day))));
            }
            say(Output.columns(rows));
            return Main.OK;
        }
    }

    /** One order and whatever came back. */
    @Command(
            name = "show",
            customSynopsis = "doublerbay order show [OPTIONS] REFERENCE",
            description = "One order and whatever came back.")
    static final class OrderShow extends Step {

        @Parameters(index = "0", paramLabel = "REFERENCE")
        String reference;

        @Override
        public Integer call() {
            EngineeringOrder one = work(unit -> unit.orders().get(OrderId.parse(reference)));
            List<List<String>> rows = new ArrayList<>();
            rows.add(List.of("reference", one.reference().toString()));
            rows.add(List.of("card", one.against().toString()));
            rows.add(List.of("raised", one.raised().toString()));
            rows.add(List.of("state", one.state().value()));
            rows.add(List.of("sent", one.sent().map(LocalDate::toString).orElse("not yet")));
            rows.add(List.of("answered", one.answered().map(LocalDate::toString).orElse("not yet")));
            rows.add(List.of("question", one.question()));
            one.approval().ifPresent(approval -> {
                rows.add(List.of("sheet", approval.sheet()));
                rows.add(List.of("category", approval.category().value()));
                rows.add(List.of(
                        "valid until",
                        approval.validUntil().map(LocalDate::toString).orElse("no end")));
                for (String text : approval.conditions()) {
                    rows.add(List.of("  condition", text));
                }
            });
            say(Output.columns(rows));
            return Main.OK;
        }
    }
}
