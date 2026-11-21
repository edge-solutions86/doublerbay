package io.github.rkaushikdev.doublerbay.store;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.damage.Buckle;
import io.github.rkaushikdev.doublerbay.damage.Corrosion;
import io.github.rkaushikdev.doublerbay.damage.Crack;
import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.damage.DamageOrigin;
import io.github.rkaushikdev.doublerbay.damage.Dent;
import io.github.rkaushikdev.doublerbay.damage.Disbond;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.damage.Geometry;
import io.github.rkaushikdev.doublerbay.damage.LightningBurn;
import io.github.rkaushikdev.doublerbay.damage.Opening;
import io.github.rkaushikdev.doublerbay.damage.Scoring;
import io.github.rkaushikdev.doublerbay.disposition.DispositionRecord;
import io.github.rkaushikdev.doublerbay.disposition.DispositionState;
import io.github.rkaushikdev.doublerbay.disposition.Transition;
import io.github.rkaushikdev.doublerbay.fleet.Aircraft;
import io.github.rkaushikdev.doublerbay.fleet.Bulletin;
import io.github.rkaushikdev.doublerbay.fleet.DiscoverySource;
import io.github.rkaushikdev.doublerbay.fleet.Series;
import io.github.rkaushikdev.doublerbay.fleet.Snapshot;
import io.github.rkaushikdev.doublerbay.fleet.UtilisationLog;
import io.github.rkaushikdev.doublerbay.grid.Frame;
import io.github.rkaushikdev.doublerbay.grid.FuselagePoint;
import io.github.rkaushikdev.doublerbay.grid.Point;
import io.github.rkaushikdev.doublerbay.grid.Rib;
import io.github.rkaushikdev.doublerbay.grid.Side;
import io.github.rkaushikdev.doublerbay.grid.Spar;
import io.github.rkaushikdev.doublerbay.grid.Stringer;
import io.github.rkaushikdev.doublerbay.grid.Surface;
import io.github.rkaushikdev.doublerbay.grid.WingPoint;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Angle;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.measure.Usage;
import io.github.rkaushikdev.doublerbay.util.Literal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turning the register's domain objects into rows and back, in one place and both directions.
 *
 * <p>Mapping in one direction only is how a schema and a domain drift apart. A field gets added to
 * the domain object, the writer is updated, the reader is not, and the value comes back as a
 * default that looks plausible. So every mapper here has its opposite next to it.
 *
 * <p>The measurement sets are dispatched on the damage kind. Adding a kind of damage means adding a
 * case to both directions, and forgetting the second one fails loudly on the way back rather than
 * quietly on the way in.
 */
public final class Mapping {

    private Mapping() {}

    /** The numbers off one measurement set, as the document that gets stored. */
    public static Map<String, Object> measurementsOf(Geometry geometry) {
        Map<String, Object> held = new LinkedHashMap<>();
        switch (geometry) {
            case Dent dent -> {
                held.put("depth", dent.depth().mils());
                held.put("along", dent.along().mils());
                held.put("around", dent.around().mils());
                held.put("sharp_bottom", dent.sharpBottom());
                held.put("cracked", dent.cracked());
            }
            case Scoring scoring -> {
                held.put("depth", scoring.depth().mils());
                held.put("length", scoring.length().mils());
                held.put("width", scoring.width().mils());
                held.put("bearing", scoring.bearing().tenths());
                held.put("sharp_bottom", scoring.sharpBottom());
            }
            case Crack crack -> {
                held.put("length", crack.length().mils());
                held.put("bearing", crack.bearing().tenths());
                held.put("through_thickness", crack.throughThickness());
                held.put("stop_drilled", crack.stopDrilled());
                held.put("tips_found", crack.tipsFound());
            }
            case Corrosion corrosion -> {
                held.put("along", corrosion.along().mils());
                held.put("around", corrosion.around().mils());
                held.put("worst_depth", corrosion.worstDepth().mils());
                held.put("pitted", corrosion.pitted());
                held.put("exfoliating", corrosion.exfoliating());
            }
            case LightningBurn burn -> {
                held.put("along", burn.along().mils());
                held.put("around", burn.around().mils());
                held.put("pit_count", burn.pitCount());
                held.put("worst_pit", burn.worstPit().mils());
                held.put("on_a_fastener", burn.onAFastener());
                held.put("is_entry", burn.isEntry());
            }
            case Opening opening -> {
                held.put("along", opening.along().mils());
                held.put("around", opening.around().mils());
                held.put("torn_edges", opening.tornEdges());
                held.put("was_a_fastener_hole", opening.wasAFastenerHole());
            }
            case Buckle buckle -> {
                held.put("length", buckle.length().mils());
                held.put("height", buckle.height().mils());
                held.put("bearing", buckle.bearing().tenths());
                held.put("confirmed_permanent", buckle.confirmedPermanent());
            }
            case Disbond disbond -> {
                held.put("along", disbond.along().mils());
                held.put("around", disbond.around().mils());
                held.put("tap_tested", disbond.tapTested());
                held.put("reaches_an_edge", disbond.reachesAnEdge());
            }
        }
        return held;
    }

    /** Rebuild a measurement set from what was stored, for the kind on the row. */
    public static Geometry geometryFrom(DamageKind kind, Map<String, Object> stored) {
        Stored read = new Stored(kind, stored);
        return switch (kind) {
            case DENT ->
                new Dent(
                        read.mils("depth"),
                        read.mils("along"),
                        read.mils("around"),
                        read.flag("sharp_bottom"),
                        read.flag("cracked"));
            case SCRATCH, GOUGE, NICK ->
                new Scoring(
                        read.mils("depth"),
                        read.mils("length"),
                        read.mils("width"),
                        read.tenths("bearing"),
                        read.flag("sharp_bottom"));
            case CRACK ->
                new Crack(
                        read.mils("length"),
                        read.tenths("bearing"),
                        read.flag("through_thickness"),
                        read.flag("stop_drilled"),
                        read.count("tips_found"));
            case CORROSION ->
                new Corrosion(
                        read.mils("along"),
                        read.mils("around"),
                        read.mils("worst_depth"),
                        read.flag("pitted"),
                        read.flag("exfoliating"));
            case LIGHTNING_BURN ->
                new LightningBurn(
                        read.mils("along"),
                        read.mils("around"),
                        read.count("pit_count"),
                        read.mils("worst_pit"),
                        read.flag("on_a_fastener"),
                        read.flag("is_entry"));
            case HOLE, PUNCTURE ->
                new Opening(
                        read.mils("along"),
                        read.mils("around"),
                        read.flag("torn_edges"),
                        read.flag("was_a_fastener_hole"));
            case BUCKLE ->
                new Buckle(
                        read.mils("length"),
                        read.mils("height"),
                        read.tenths("bearing"),
                        read.flag("confirmed_permanent"));
            case DISBOND ->
                new Disbond(
                        read.mils("along"), read.mils("around"), read.flag("tap_tested"), read.flag("reaches_an_edge"));
        };
    }

    /** A tail as its row, with its bulletins in printed order. */
    public static AircraftRow aircraftToRow(Aircraft aircraft) {
        AircraftRow row = new AircraftRow(
                aircraft.serial(),
                aircraft.registration(),
                aircraft.series().value(),
                aircraft.lineNumber(),
                aircraft.delivered());
        row.setWithdrawn(aircraft.withdrawn().orElse(null));
        List<Bulletin> bulletins = new ArrayList<>(aircraft.embodied());
        bulletins.sort(Comparator.comparing(Bulletin::toString));
        row.setEmbodiments(bulletins.stream()
                .map(one -> new EmbodimentRow(aircraft.serial(), one.chapter(), one.number()))
                .toList());
        return row;
    }

    /** A tail back off its row. */
    public static Aircraft rowToAircraft(AircraftRow row) {
        Set<Bulletin> embodied = new HashSet<>();
        for (EmbodimentRow one : row.getEmbodiments()) {
            embodied.add(new Bulletin(one.getChapter(), one.getNumber()));
        }
        return Aircraft.of(
                row.getSerial(),
                row.getRegistration(),
                Series.fromValue(row.getSeries()),
                row.getLineNumber(),
                row.getDelivered(),
                embodied,
                row.getWithdrawn().orElse(null));
    }

    /** A log as one row per reading. */
    public static List<ReadingRow> logToRows(UtilisationLog log) {
        return log.snapshots().stream()
                .map(snapshot -> new ReadingRow(
                        log.serial(),
                        snapshot.taken(),
                        snapshot.usage().cycles(),
                        snapshot.usage().hours()))
                .toList();
    }

    /** A log back off its readings, in the order they were taken. */
    public static UtilisationLog rowsToLog(Serial serial, List<ReadingRow> rows) {
        List<ReadingRow> ordered = new ArrayList<>(rows);
        ordered.sort(Comparator.comparing(ReadingRow::getTaken));
        return new UtilisationLog(
                serial,
                ordered.stream()
                        .map(row -> new Snapshot(row.getTaken(), new Usage(row.getCycles(), row.getHours())))
                        .toList());
    }

    /** A card at one revision as its row, its location written the way the mechanic wrote it. */
    public static FindingRow findingToRow(Finding finding) {
        Point point = finding.point();
        FindingRow row = new FindingRow(
                finding.reference(),
                finding.revision(),
                finding.serial(),
                finding.kind().value(),
                finding.foundOn(),
                finding.source().value(),
                finding.origin().value(),
                finding.atUsage().cycles(),
                finding.atUsage().hours(),
                point instanceof FuselagePoint ? "fuselage" : "wing",
                point.side().value(),
                measurementsOf(finding.geometry()));
        row.setSupersededBy(finding.supersededBy().orElse(null));
        row.setNote(finding.note());
        switch (point) {
            case FuselagePoint at ->
                row.onFuselage(
                        at.frame().number(), at.aftOfFrame(), at.stringer().number(), at.belowStringer());
            case WingPoint at ->
                row.onWing(
                        at.rib().number(),
                        at.outboardOfRib(),
                        at.spar().value(),
                        at.aftOfSpar(),
                        at.surface().value());
        }
        return row;
    }

    /** A card back off its row, refusing a row whose location or measurements do not read. */
    public static Finding rowToFinding(FindingRow row) {
        Side side = Side.fromValue(row.getSide());
        Point point;
        if (row.getStructure().equals("fuselage")) {
            int stringerNumber = count(row.getStringer().orElse(null));
            point = new FuselagePoint(
                    new Frame(count(row.getFrame().orElse(null))),
                    new Length(row.getAftOfFrame().map(Length::mils).orElse(0)),
                    stringerNumber != 0 ? new Stringer(stringerNumber, side) : new Stringer(0),
                    new Length(row.getBelowStringer().map(Length::mils).orElse(0)));
        } else if (row.getStructure().equals("wing")) {
            point = new WingPoint(
                    new Rib(count(row.getRib().orElse(null)), side),
                    new Length(row.getOutboardOfRib().map(Length::mils).orElse(0)),
                    Spar.fromValue(row.getSpar().orElse(null)),
                    new Length(row.getAftOfSpar().map(Length::mils).orElse(0)),
                    Surface.fromValue(row.getSurface().orElse(null)));
        } else {
            throw new BadReading(
                    row.getReference() + ": " + Literal.of(row.getStructure()) + " is not a structure on file");
        }
        DamageKind kind = DamageKind.fromValue(row.getKind());
        return Finding.of(
                row.getReference(),
                row.getSerial(),
                kind,
                geometryFrom(kind, new LinkedHashMap<>(row.getMeasurements())),
                point,
                row.getFoundOn(),
                DiscoverySource.fromValue(row.getSource()),
                DamageOrigin.fromValue(row.getOrigin()),
                new Usage(row.getAtCycles(), row.getAtHours()),
                row.getRevision(),
                row.getSupersededBy().orElse(null),
                row.getNote());
    }

    /** Where a card is and how it got there, as its row and one row per move, numbered from one. */
    public static DispositionRow dispositionToRow(DispositionRecord record) {
        DispositionRow row =
                new DispositionRow(record.reference(), record.state().value());
        List<MoveRow> moves = new ArrayList<>();
        int sequence = 1;
        for (Transition move : record.history()) {
            moves.add(new MoveRow(
                            record.reference(),
                            sequence++,
                            move.was().value(),
                            move.now().value(),
                            move.on(),
                            move.by())
                    .withNote(move.note()));
        }
        row.setMoves(moves);
        return row;
    }

    /** A card's disposition back off its rows, the moves in the order they were made. */
    public static DispositionRecord rowToDisposition(DispositionRow row) {
        List<MoveRow> moves = new ArrayList<>(row.getMoves());
        moves.sort(Comparator.comparingInt(MoveRow::getSequence));
        return new DispositionRecord(
                row.getReference(),
                DispositionState.fromValue(row.getState()),
                moves.stream()
                        .map(move -> new Transition(
                                DispositionState.fromValue(move.getWas()),
                                DispositionState.fromValue(move.getNow()),
                                move.getOn(),
                                move.getBy(),
                                move.getNote()))
                        .toList());
    }

    private static int count(Integer value) {
        if (value == null) {
            throw new BadReading("an empty column is not a stored count");
        }
        return value;
    }

    /** A stored measurement set being read back, refusing a value of the wrong shape by name. */
    private record Stored(DamageKind kind, Map<String, Object> values) {

        private Object need(String name) {
            if (!values.containsKey(name)) {
                throw new BadReading("a stored " + kind + " is missing its " + name);
            }
            return values.get(name);
        }

        Length mils(String name) {
            return new Length(whole(need(name), "length"));
        }

        Angle tenths(String name) {
            return new Angle(whole(need(name), "angle"));
        }

        int count(String name) {
            return whole(need(name), "count");
        }

        boolean flag(String name) {
            Object value = need(name);
            return switch (value) {
                case null -> false;
                case Boolean yes -> yes;
                case Number number -> number.doubleValue() != 0;
                case String text -> !text.isEmpty();
                case Map<?, ?> nested -> !nested.isEmpty();
                case List<?> items -> !items.isEmpty();
                default -> true;
            };
        }

        private static int whole(Object value, String what) {
            if (value instanceof Integer number) {
                return number;
            }
            if (value instanceof Long number && number == number.intValue()) {
                return number.intValue();
            }
            String written = value instanceof String text ? Literal.of(text) : String.valueOf(value);
            throw new BadReading(written + " is not a stored " + what);
        }
    }
}
