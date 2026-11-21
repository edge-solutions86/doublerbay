package io.github.rkaushikdev.doublerbay.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.api.FindingIn;
import io.github.rkaushikdev.doublerbay.api.Invalid;
import io.github.rkaushikdev.doublerbay.api.Schemas;
import io.github.rkaushikdev.doublerbay.api.Wire;
import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.damage.Geometry;
import io.github.rkaushikdev.doublerbay.fleet.TypeCatalogue;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.manual.Book;
import io.github.rkaushikdev.doublerbay.report.Entry;
import io.github.rkaushikdev.doublerbay.report.History;
import io.github.rkaushikdev.doublerbay.report.Story;
import io.github.rkaushikdev.doublerbay.service.Intake;
import io.github.rkaushikdev.doublerbay.service.Measuring;
import io.github.rkaushikdev.doublerbay.service.ReMeasured;
import io.github.rkaushikdev.doublerbay.util.Literal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * The card commands, which take a document rather than fifteen options.
 *
 * <p>A damage card carries a location on one of two structures and a measurement set whose fields
 * depend on the kind. Expressed as command line options that is somewhere north of twenty flags,
 * most of them meaningless for any given kind, and a mechanic gets three of them wrong at three in
 * the morning.
 *
 * <p>So a card is raised from a small JSON document. That is also what the maintenance system
 * already emits, and what the desk can keep a copy of. The template command prints a blank one for
 * a kind, with every field the kind needs and nothing it does not, which is the part people actually
 * get wrong.
 */
public final class Cards {

    /**
     * What each kind measures. The values are the shape of the field, not a default, because a blank
     * a person forgets to fill in should not parse.
     */
    public static final Map<DamageKind, Map<String, Object>> MEASUREMENTS = measurements();

    /** Where a card on the fuselage is, blank. */
    public static final Map<String, Object> FUSELAGE_BLANK = blank(
            "structure", "fuselage",
            "frame", 0,
            "aft_of_frame", "0.000",
            "stringer", 0,
            "below_stringer", "0.000",
            "side", "L");

    /** Where a card on a wing is, blank. */
    public static final Map<String, Object> WING_BLANK = blank(
            "structure", "wing",
            "rib", 0,
            "outboard_of_rib", "0.000",
            "spar", "front",
            "aft_of_spar", "0.000",
            "surface", "lower",
            "side", "L");

    /** Reads a document the way it was written: one value, nothing after it, NaN allowed. */
    private static final JsonMapper DOCUMENTS = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)
            .build();

    private static final TypeReference<Map<String, Object>> NAMED = new TypeReference<>() {};

    private Cards() {}

    private static Map<DamageKind, Map<String, Object>> measurements() {
        Map<DamageKind, Map<String, Object>> every = new EnumMap<>(DamageKind.class);
        every.put(
                DamageKind.DENT,
                blank("depth", "0.000", "along", "0.000", "around", "0.000", "sharp_bottom", false, "cracked", false));
        Map<String, Object> scratch =
                blank("depth", "0.000", "length", "0.000", "width", "0.000", "bearing", "0.0", "sharp_bottom", false);
        every.put(DamageKind.SCRATCH, scratch);
        every.put(
                DamageKind.CRACK,
                blank(
                        "length",
                        "0.000",
                        "bearing",
                        "0.0",
                        "through_thickness",
                        false,
                        "stop_drilled",
                        false,
                        "tips_found",
                        2));
        every.put(
                DamageKind.CORROSION,
                blank(
                        "along", "0.000",
                        "around", "0.000",
                        "worst_depth", "0.000",
                        "pitted", false,
                        "exfoliating", false));
        every.put(DamageKind.GOUGE, scratch);
        every.put(DamageKind.NICK, scratch);
        return Collections.unmodifiableMap(every);
    }

    private static Map<String, Object> blank(Object... namesAndValues) {
        Map<String, Object> laid = new LinkedHashMap<>();
        for (int index = 0; index < namesAndValues.length; index += 2) {
            laid.put((String) namesAndValues[index], namesAndValues[index + 1]);
        }
        return Collections.unmodifiableMap(laid);
    }

    /**
     * A blank card for one kind on one structure, as a document the caller may fill in.
     *
     * @throws BadReading for a kind with no template or a structure the desk does not hold
     */
    public static Map<String, Object> templateFor(DamageKind kind, String structure) {
        if (!MEASUREMENTS.containsKey(kind)) {
            throw new BadReading("there is no card template for a " + kind + " yet");
        }
        if (!structure.equals("fuselage") && !structure.equals("wing")) {
            throw new BadReading(Literal.of(structure) + " is not a structure this desk holds");
        }
        Map<String, Object> blank = new LinkedHashMap<>();
        blank.put("serial", "");
        blank.put("kind", kind.value());
        blank.put("origin", "unknown");
        blank.put("source", "walkaround");
        blank.put("found_on", "");
        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("cycles", 0);
        usage.put("hours", "0.0");
        blank.put("at_usage", usage);
        blank.put(structure, new LinkedHashMap<>(structure.equals("fuselage") ? FUSELAGE_BLANK : WING_BLANK));
        blank.put("measurements", new LinkedHashMap<>(MEASUREMENTS.get(kind)));
        blank.put("note", "");
        return blank;
    }

    /**
     * Read a card document off disk, refusing an unreadable one by name.
     *
     * <p>The file is read as UTF-8 wherever this runs. A card carrying a degree sign or an
     * engineer's accented name reads the same on the desk as on the server.
     *
     * @throws BadReading for a file that is not JSON, or JSON that is not a card
     * @throws Invalid for a card that leaves out a field or carries one of the wrong shape
     */
    public static FindingIn readDocument(Path path) {
        JsonNode loaded = parsed(path, Texts.read(path));
        if (!loaded.isObject()) {
            throw new BadReading(path + " is not a card, it is a " + kindOf(loaded));
        }
        FindingIn body;
        try {
            body = Wire.mapper().treeToValue(loaded, FindingIn.class);
        } catch (JsonProcessingException unshaped) {
            throw new IllegalArgumentException(unshaped.getOriginalMessage(), unshaped);
        }
        return Invalid.validated(body);
    }

    private static JsonNode parsed(Path path, String text) {
        JsonNode loaded;
        try {
            loaded = DOCUMENTS.readTree(text);
        } catch (JsonProcessingException broken) {
            throw new BadReading(path + " is not readable json: " + broken.getOriginalMessage());
        }
        if (loaded == null || loaded.isMissingNode()) {
            throw new BadReading(path + " is not readable json: there is no value in it");
        }
        return loaded;
    }

    /** The name a refusal gives the kind of value a document holds. */
    static String kindOf(JsonNode value) {
        if (value.isObject()) {
            return "dict";
        }
        if (value.isArray()) {
            return "list";
        }
        if (value.isTextual()) {
            return "str";
        }
        if (value.isBoolean()) {
            return "bool";
        }
        if (value.isIntegralNumber()) {
            return "int";
        }
        if (value.isNumber()) {
            return "float";
        }
        return "null";
    }

    /** A measurement as the card prints it: a length as written, a yes or no as {@code True} or {@code False}. */
    static String printed(Object value) {
        if (value instanceof Boolean yes) {
            return yes ? "True" : "False";
        }
        return String.valueOf(value);
    }

    /** Damage cards: raise them, read them, measure them again. */
    @Command(
            name = "cards",
            customSynopsis = "doublerbay cards [OPTIONS] COMMAND [ARGS]...",
            description = "Damage cards: raise them, read them, measure them again.",
            subcommands = {
                CardsHistory.class,
                CardsList.class,
                CardsMeasure.class,
                CardsRaise.class,
                CardsShow.class,
                CardsStory.class,
                CardsTemplate.class,
            })
    static final class Group {}

    /** Print a blank card document for a kind. */
    @Command(
            name = "template",
            customSynopsis = "doublerbay cards template [OPTIONS] KIND",
            description = "Print a blank card document for a kind.")
    static final class CardsTemplate extends Step {

        @Parameters(index = "0", paramLabel = "KIND")
        String kind;

        @Option(names = "--structure", defaultValue = "fuselage", showDefaultValue = Visibility.ALWAYS)
        String structure;

        @Override
        public Integer call() {
            say(Indented.json(templateFor(DamageKind.fromValue(kind), structure)));
            return Main.OK;
        }
    }

    /** Raise a card from a document, allocating its reference here. */
    @Command(
            name = "raise",
            customSynopsis = "doublerbay cards raise [OPTIONS] DOCUMENT",
            description = "Raise a card from a document, allocating its reference here.")
    static final class CardsRaise extends Step {

        @Parameters(index = "0", paramLabel = "DOCUMENT", converter = Arguments.ExistingFile.class)
        Path document;

        @Override
        public Integer call() {
            FindingIn body = readDocument(document);
            Intake taken = work(unit -> {
                FindingId reference = Intake.nextReference(unit, body.foundOn());
                return Intake.takeIn(unit, Book.v320(), new TypeCatalogue(), body.toDomain(reference), body.urgent());
            });
            say(taken.toString());
            taken.reportBy().ifPresent(by -> say("report it by " + by));
            return Main.OK;
        }
    }

    /** One card, with its measurements as they were written. */
    @Command(
            name = "show",
            customSynopsis = "doublerbay cards show [OPTIONS] REFERENCE",
            description = "One card, with its measurements as they were written.")
    static final class CardsShow extends Step {

        @Parameters(index = "0", paramLabel = "REFERENCE")
        String reference;

        @Override
        public Integer call() {
            List<List<String>> rows = work(unit -> {
                Finding card = unit.findings().latest(FindingId.parse(reference));
                List<List<String>> laid = new ArrayList<>();
                laid.add(List.of("reference", card.reference().toString()));
                laid.add(List.of("tail", card.serial().number()));
                laid.add(List.of("kind", card.kind().toString()));
                laid.add(List.of("origin", card.origin().value()));
                laid.add(List.of("found by", card.source().value()));
                laid.add(List.of("found on", card.foundOn().toString()));
                laid.add(List.of("revision", String.valueOf(card.revision())));
                laid.add(List.of("at", card.atUsage().toString()));
                laid.add(List.of("where", card.point().toString()));
                new TreeMap<>(Schemas.measurementsOut(card.geometry()))
                        .forEach((name, value) -> laid.add(List.of(name, printed(value))));
                card.supersededBy().ifPresent(by -> laid.add(List.of("superseded by", by.toString())));
                if (!card.note().isEmpty()) {
                    laid.add(List.of("note", card.note()));
                }
                return laid;
            });
            say(Output.columns(rows));
            return Main.OK;
        }
    }

    /** Every revision of one card, oldest first. */
    @Command(
            name = "history",
            customSynopsis = "doublerbay cards history [OPTIONS] REFERENCE",
            description = "Every revision of one card, oldest first.")
    static final class CardsHistory extends Step {

        @Parameters(index = "0", paramLabel = "REFERENCE")
        String reference;

        @Override
        public Integer call() {
            List<List<String>> rows = work(unit -> {
                List<Finding> revisions = unit.findings().history(FindingId.parse(reference));
                List<List<String>> laid = new ArrayList<>();
                laid.add(List.of("revision", "found on", "at", "measurements"));
                for (Finding one : revisions) {
                    laid.add(List.of(
                            String.valueOf(one.revision()),
                            one.foundOn().toString(),
                            one.atUsage().cycles().toString(),
                            one.geometry().toString()));
                }
                return laid;
            });
            say(Output.columns(rows));
            return Main.OK;
        }
    }

    /** Every card on one tail. */
    @Command(name = "list", customSynopsis = "doublerbay cards list [OPTIONS]", description = "Every card on one tail.")
    static final class CardsList extends Step {

        @Option(names = "--tail", required = true)
        String serial;

        @Option(names = "--open", description = "only the ones the desk still owns")
        boolean openOnly;

        @Override
        public Integer call() {
            List<List<String>> rows = work(unit -> {
                Serial held = new Serial(serial);
                List<Finding> found = openOnly
                        ? unit.findings().openOn(held)
                        : unit.findings().onTail(held);
                List<List<String>> laid = new ArrayList<>();
                laid.add(List.of("reference", "kind", "found on", "where"));
                for (Finding one : found) {
                    laid.add(List.of(
                            one.reference().toString(),
                            one.kind().toString(),
                            one.foundOn().toString(),
                            one.point().toString()));
                }
                return laid;
            });
            say(Output.columns(rows));
            return Main.OK;
        }
    }

    /**
     * Record a re-measurement from a document of the new numbers.
     *
     * <p>Only the measurements block is read. The rest of a card document is where the damage is and
     * what caused it, and neither of those changes because somebody put a depth gauge on it again.
     */
    @Command(
            name = "measure",
            customSynopsis = "doublerbay cards measure [OPTIONS] REFERENCE DOCUMENT",
            description = {
                "Record a re measurement from a document of the new numbers.",
                "",
                "Only the measurements block is read. The rest of a card document is where the damage is and what"
                        + " caused it, and neither of those changes because somebody put a depth gauge on it again."
            })
    static final class CardsMeasure extends Step {

        @Parameters(index = "0", paramLabel = "REFERENCE")
        String reference;

        @Parameters(index = "1", paramLabel = "DOCUMENT", converter = Arguments.ExistingFile.class)
        Path document;

        @Option(names = "--on", required = true, description = "the day it was looked at")
        String onDay;

        @Override
        public Integer call() {
            FindingId held = FindingId.parse(reference);
            JsonNode sent;
            try {
                sent = DOCUMENTS.readTree(Texts.read(document));
            } catch (JsonProcessingException broken) {
                throw new IllegalArgumentException(broken.getOriginalMessage(), broken);
            }
            if (sent == null || !sent.isObject() || !sent.has("measurements")) {
                throw new BadReading(document + " carries no measurements block");
            }
            JsonNode numbers = sent.get("measurements");
            ReMeasured answer = work(unit -> {
                Finding current = unit.findings().latest(held);
                Geometry measured = Schemas.measurementsIn(current.kind(), named(numbers));
                LocalDate day = Output.asDate(onDay);
                return Measuring.reMeasure(unit, Book.v320(), new TypeCatalogue(), held, measured, day);
            });
            say(answer.toString());
            if (answer.outgrewItsDeferral()) {
                say("this is still deferred and the answer got worse, tell an engineer");
            }
            return Main.OK;
        }

        private static Map<String, Object> named(JsonNode numbers) {
            if (!numbers.isObject()) {
                throw new IllegalArgumentException(
                        "the measurements block is a " + kindOf(numbers) + ", not a set of named values");
            }
            return Wire.mapper().convertValue(numbers, NAMED);
        }
    }

    /** Everything that happened to one card, oldest first. */
    @Command(
            name = "story",
            customSynopsis = "doublerbay cards story [OPTIONS] REFERENCE",
            description = "Everything that happened to one card, oldest first.")
    static final class CardsStory extends Step {

        @Parameters(index = "0", paramLabel = "REFERENCE")
        String reference;

        @Override
        public Integer call() {
            Story built = work(unit -> History.storyOf(unit, FindingId.parse(reference)));
            say(built.toString());
            say("");
            List<List<String>> rows = new ArrayList<>();
            for (Entry one : built.entries()) {
                rows.add(List.of(one.on().toString(), one.register(), one.what()));
            }
            say(Output.columns(rows));
            return Main.OK;
        }
    }
}
