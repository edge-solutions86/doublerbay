package io.github.rkaushikdev.doublerbay.api;

import static io.github.rkaushikdev.doublerbay.support.Api.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.assess.Assessment;
import io.github.rkaushikdev.doublerbay.assess.Assessor;
import io.github.rkaushikdev.doublerbay.damage.Crack;
import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.damage.Dent;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.grid.FuselagePoint;
import io.github.rkaushikdev.doublerbay.grid.Point;
import io.github.rkaushikdev.doublerbay.grid.WingPoint;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.manual.Book;
import io.github.rkaushikdev.doublerbay.measure.Angle;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.measure.Usage;
import io.github.rkaushikdev.doublerbay.support.Sample;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SchemasTest {

    private static final FindingId DR = new FindingId(2025, 4182);

    private static Map<String, Object> cardBody() {
        return json(
                "serial", "5127",
                "kind", "dent",
                "origin", "impact",
                "source", "scheduled check",
                "found_on", "2025-03-14",
                "at_usage", json("cycles", 13900, "hours", "24400.0"),
                "fuselage",
                        json(
                                "structure", "fuselage",
                                "frame", 15,
                                "aft_of_frame", "10.000",
                                "stringer", 10,
                                "below_stringer", "0.000",
                                "side", "L"),
                "measurements",
                        json(
                                "depth", "0.020",
                                "along", "3.000",
                                "around", "2.000",
                                "sharp_bottom", false,
                                "cracked", false));
    }

    /** A card read the way it is read off the wire, held to the same rules. */
    private static FindingIn card(Map<String, Object> body) {
        return Invalid.validated(Wire.mapper().convertValue(body, FindingIn.class));
    }

    @Test
    void aLengthIsParsedOffTheWireTheWayItIsOffACard() {
        assertThat(Schemas.inches("0.040")).isEqualTo(Length.ofInches("0.040"));
        assertThat(Schemas.inches("727.5")).isEqualTo(Length.ofInches("727.5"));
    }

    @Test
    void aLengthThatIsNotANumberIsRefused() {
        assertThatThrownBy(() -> Schemas.inches("deep"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is not a length in inches");
    }

    @Test
    void aMeasurementSetGoesOutWithItsLengthsAsText() {
        Map<String, Object> sent =
                Schemas.measurementsOut(new Dent(Length.ofInches("0.020"), new Length(3000), new Length(2000)));
        assertThat(sent.get("depth")).isEqualTo("0.020");
        assertThat(sent.get("along")).isEqualTo("3.000");
        assertThat(sent.get("sharp_bottom")).isEqualTo(false);
    }

    @Test
    void aBearingGoesOutInDegreesToATenth() {
        Map<String, Object> sent = Schemas.measurementsOut(new Crack(new Length(750), Angle.ofDegrees("12.5")));
        assertThat(sent.get("bearing")).isEqualTo("12.5");
        assertThat(sent.get("tips_found")).isEqualTo(2);
    }

    @Test
    void aMeasurementSetRoundTripsThroughTheWireForm() {
        Dent before = new Dent(Length.ofInches("0.020"), new Length(3000), new Length(2000), true, false);
        assertThat(Schemas.measurementsIn(DamageKind.DENT, Schemas.measurementsOut(before)))
                .isEqualTo(before);
    }

    @Test
    void aBearingRoundTripsThroughTheWireForm() {
        Crack before = new Crack(new Length(750), Angle.ofDegrees("12.5"), false, true, 1);
        assertThat(Schemas.measurementsIn(DamageKind.CRACK, Schemas.measurementsOut(before)))
                .isEqualTo(before);
    }

    @Test
    void aMeasurementThatIsNotANumberIsRefused() {
        assertThatThrownBy(() -> Schemas.measurementsIn(
                        DamageKind.DENT,
                        json(
                                "depth", "deep",
                                "along", "3.000",
                                "around", "2.000",
                                "sharp_bottom", false,
                                "cracked", false)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is not a depth in inches");
    }

    @Test
    void aBearingThatIsNotANumberIsRefused() {
        assertThatThrownBy(() -> Schemas.measurementsIn(
                        DamageKind.CRACK,
                        json(
                                "length",
                                "0.750",
                                "bearing",
                                "sideways",
                                "through_thickness",
                                true,
                                "stop_drilled",
                                false,
                                "tips_found",
                                2)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is not a bearing in degrees");
    }

    @Test
    void aCounterReadingCrossesWithItsHoursAsText() {
        Usage reading = new UsageIn(13900, "24400.5").toDomain();
        assertThat(reading.cycles().count()).isEqualTo(13900);
        assertThat(reading.hours()).hasToString("24400.5 FH");
    }

    @Test
    void aCounterReadingThatIsNotANumberIsRefused() {
        assertThatThrownBy(() -> new UsageIn(1, "lots").toDomain())
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is not a count of hours");
    }

    @Test
    void aFuselageLocationArrivesAsAWrittenPoint() {
        Point location = new FuselageLocationIn(15, "10.000", 10, "0.000", "L").toDomain();
        assertThat(location).isInstanceOf(FuselagePoint.class);
        assertThat(location).hasToString("FR15 10.000 aft, S-10L on");
    }

    @Test
    void aWingLocationArrivesAsAWrittenPoint() {
        WingPoint location = new WingLocationIn(2, "6.000", "front", "20.000", "lower", "L").toDomain();
        assertThat(location).isInstanceOf(WingPoint.class);
        assertThat(location.surface().value()).isEqualTo("lower");
    }

    @Test
    void aCardArrivesAndBecomesAFinding() {
        Finding finding = card(cardBody()).toDomain(DR);
        assertThat(finding.reference()).isEqualTo(DR);
        assertThat(finding.kind()).isSameAs(DamageKind.DENT);
        assertThat(((Dent) finding.geometry()).depth()).isEqualTo(Length.ofInches("0.020"));
    }

    @Test
    void aCardWithNoLocationIsRefused() {
        Map<String, Object> body = cardBody();
        body.remove("fuselage");
        assertThatThrownBy(() -> card(body).toDomain(DR))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("says where it is");
    }

    @Test
    void aCardOnTwoStructuresAtOnceIsRefused() {
        Map<String, Object> body = cardBody();
        body.put(
                "wing",
                json(
                        "structure", "wing",
                        "rib", 2,
                        "outboard_of_rib", "6.000",
                        "spar", "front",
                        "aft_of_spar", "20.000",
                        "surface", "lower",
                        "side", "L"));
        assertThatThrownBy(() -> card(body).toDomain(DR))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("says where it is");
    }

    @Test
    void aCardGoesBackWithEverythingNeededToReadIt() {
        FindingOut sent = Schemas.findingOut(Sample.card());
        assertThat(sent.reference()).isEqualTo("DR-2025-000001");
        assertThat(sent.serial()).isEqualTo("5127");
        assertThat(sent.location().structure()).isEqualTo("fuselage");
        assertThat(sent.measurements().get("depth")).isEqualTo("0.020");
        assertThat(sent.supersededBy()).isEmpty();
    }

    @Test
    void aSupersededCardSaysWhatReplacedIt() {
        FindingOut sent = Schemas.findingOut(Sample.card().superseded(new FindingId(2025, 4200)));
        assertThat(sent.supersededBy()).contains("DR-2025-004200");
    }

    @Test
    void anAssessmentGoesBackWithItsWholeTrail() {
        Assessment answer = Assessor.assess(Book.v320(), Sample.tailContext(), Sample.card());
        AssessmentOut sent = Schemas.assessmentOut(answer);
        assertThat(sent.reference()).isEqualTo("DR-2025-000001");
        assertThat(sent.part()).startsWith("5310-");
        assertThat(sent.thickness()).startsWith("0.0");
        assertThat(sent.verdict().reasons()).isNotEmpty();
        assertThat(sent.verdict().disposition()).isNotEmpty();
    }

    @Test
    void aTailGoesBackAndComesInAgainUnchanged() {
        AircraftOut sent = Schemas.aircraftOut(Sample.aircraft());
        AircraftIn back =
                new AircraftIn(sent.serial(), sent.registration(), sent.series(), sent.lineNumber(), sent.delivered());
        assertThat(back.toDomain()).isEqualTo(Sample.aircraft());
    }

    @Test
    void aTailWithALineNumberOfNothingIsRefusedByTheSchema() {
        assertThatThrownBy(
                        () -> Invalid.validated(new AircraftIn("5127", "N412VD", "200", 0, LocalDate.of(2016, 4, 12))))
                .isInstanceOf(Invalid.class);
    }
}
