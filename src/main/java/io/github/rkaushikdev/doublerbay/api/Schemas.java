package io.github.rkaushikdev.doublerbay.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.WrongMeasure;
import io.github.rkaushikdev.doublerbay.assess.Assessment;
import io.github.rkaushikdev.doublerbay.assess.Verdict;
import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.damage.Geometry;
import io.github.rkaushikdev.doublerbay.fleet.Aircraft;
import io.github.rkaushikdev.doublerbay.grid.FuselagePoint;
import io.github.rkaushikdev.doublerbay.grid.Point;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.store.Mapping;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import io.github.rkaushikdev.doublerbay.util.Literal;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * What goes over the wire, with every measurement carried as a string.
 *
 * <p>A depth of 0.040 sent as a JSON number is a double by the time it reaches the other end, and
 * 0.040 is not representable as one. It arrives as 0.04000000000000000083, gets rounded somewhere,
 * and compares unequal to the limit it is being tested against roughly one time in a thousand. So a
 * length crosses the wire as the text a person would write on the card and is parsed into whole mils
 * on arrival, exactly as it is off a paper form.
 *
 * <p>Angles go the same way, in degrees to a tenth. Counts and flags are the only things that cross
 * as JSON numbers and booleans, because those are exact.
 */
public final class Schemas {

    /** Which fields of a measurement document are lengths, and so cross as strings. */
    public static final Set<String> LENGTH_FIELDS =
            Set.of("depth", "along", "around", "length", "width", "worst_depth", "worst_pit", "height");

    /** Which fields of a measurement document are angles, and so cross as degrees in text. */
    public static final Set<String> ANGLE_FIELDS = Set.of("bearing");

    private static final ObjectMapper JSON = Wire.mapper();

    private Schemas() {}

    /** Parse a length off the wire the same way it is parsed off a card. */
    public static Length inches(String text) {
        return inches(text, "length");
    }

    /** Parse a measurement off the wire, naming what it was meant to be if it is not one. */
    public static Length inches(String text, String what) {
        try {
            return Length.ofInches(Decimals.parse(text));
        } catch (BadReading | WrongMeasure | IllegalArgumentException | ArithmeticException refused) {
            throw new BadReading(Literal.of(text) + " is not a " + what + " in inches");
        }
    }

    /**
     * Read one of a fixed set of values off the wire, refusing an unknown one the way the domain does.
     *
     * <p>A value the set does not hold is a bad request, not a broken server, so it is refused as a
     * bad reading that lists what the value could have been.
     */
    public static <E> E readEnum(E[] every, Function<E, String> valueOf, String value, String what) {
        List<String> values = new ArrayList<>();
        for (E one : every) {
            String written = valueOf.apply(one);
            if (written.equals(value)) {
                return one;
            }
            values.add(written);
        }
        throw new BadReading(Literal.of(value) + " is not a " + what + ", it is one of " + String.join(", ", values));
    }

    /** One measurement set, with its lengths as the text a person would write. */
    public static Map<String, Object> measurementsOut(Geometry geometry) {
        Map<String, Object> sent = new LinkedHashMap<>();
        Mapping.measurementsOf(geometry).forEach((name, value) -> {
            if (LENGTH_FIELDS.contains(name)) {
                sent.put(name, Decimals.text(new Length(((Number) value).intValue()).inches()));
            } else if (ANGLE_FIELDS.contains(name)) {
                sent.put(name, Decimals.text(Decimals.over(BigDecimal.valueOf(((Number) value).longValue()), 10)));
            } else {
                sent.put(name, value);
            }
        });
        return sent;
    }

    /** Rebuild a measurement set from what arrived, refusing anything unreadable. */
    public static Geometry measurementsIn(DamageKind kind, Map<String, Object> sent) {
        Map<String, Object> stored = new LinkedHashMap<>();
        sent.forEach((name, value) -> {
            if (LENGTH_FIELDS.contains(name)) {
                stored.put(name, inches(text(value), name).mils());
            } else if (ANGLE_FIELDS.contains(name)) {
                try {
                    stored.put(name, Decimals.toInt(Decimals.times(Decimals.parse(text(value)), 10)));
                } catch (NumberFormatException refused) {
                    throw new BadReading(written(value) + " is not a bearing in degrees");
                }
            } else {
                stored.put(name, value);
            }
        });
        return Mapping.geometryFrom(kind, stored);
    }

    /** Where a card is, as it goes back. */
    public static LocationOut locationOut(Finding finding) {
        Point point = finding.point();
        String structure = point instanceof FuselagePoint ? "fuselage" : "wing";
        return new LocationOut(structure, point.toString(), point.side().value());
    }

    /** A damage card as it goes back. */
    public static FindingOut findingOut(Finding finding) {
        return new FindingOut(
                finding.reference().toString(),
                finding.serial().number(),
                finding.kind().value(),
                finding.origin().value(),
                finding.source().value(),
                finding.foundOn(),
                finding.revision(),
                new UsageOut(
                        finding.atUsage().cycles().count(),
                        Decimals.text(finding.atUsage().hours().hours())),
                locationOut(finding),
                measurementsOut(finding.geometry()),
                finding.supersededBy().map(FindingId::toString),
                finding.note());
    }

    /** One assessment as it goes back, with the whole trail it took. */
    public static AssessmentOut assessmentOut(Assessment assessment) {
        Verdict verdict = assessment.verdict();
        return new AssessmentOut(
                assessment.finding().reference().toString(),
                assessment.placement().part().toString(),
                assessment.placement().panelKind().value(),
                assessment.placement().region().value(),
                Decimals.text(assessment.placement().thickness().inches()),
                Decimals.text(assessment.placement().edgeMargin().inches()),
                new VerdictOut(
                        verdict.disposition().value(),
                        verdict.reference().map(Object::toString),
                        verdict.revision(),
                        verdict.why(),
                        verdict.reasons().stream()
                                .map(one -> new ReasonOut(one.rule(), one.detail(), one.passed()))
                                .toList()));
    }

    /** A tail as it goes back. */
    public static AircraftOut aircraftOut(Aircraft aircraft) {
        return new AircraftOut(
                aircraft.serial().number(),
                aircraft.registration().toString(),
                aircraft.series().value(),
                aircraft.lineNumber(),
                aircraft.delivered(),
                aircraft.withdrawn());
    }

    /** A value off the wire as text: text as it stands, anything else in its written form. */
    private static String text(Object value) {
        return value == null ? "None" : Printed.of(tree(value));
    }

    /** A value off the wire in its written form, text quoted. */
    private static String written(Object value) {
        return value == null ? "None" : Printed.written(tree(value));
    }

    private static JsonNode tree(Object value) {
        return JSON.valueToTree(value);
    }
}
