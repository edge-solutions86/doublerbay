"""What goes over the wire, with every measurement carried as a string.

A depth of 0.040 sent as a JSON number is a double by the time it reaches the
other end, and 0.040 is not representable as one. It arrives as
0.04000000000000000083, gets rounded somewhere, and compares unequal to the
limit it is being tested against roughly one time in a thousand. So a length
crosses the wire as the text a person would write on the card and is parsed
into whole mils on arrival, exactly as it is off a paper form.

Angles go the same way, in degrees to a tenth. Counts and flags are the only
things that cross as JSON numbers and booleans, because those are exact.
"""

from __future__ import annotations

from datetime import date
from decimal import Decimal, InvalidOperation
from enum import Enum
from typing import Any, TypeVar

from pydantic import BaseModel, Field

from doublerbay.assess.assessor import Assessment
from doublerbay.damage.finding import Finding, Geometry
from doublerbay.damage.kind import DamageKind, DamageOrigin
from doublerbay.errors import BadReading
from doublerbay.fleet.aircraft import Aircraft
from doublerbay.fleet.check import DiscoverySource
from doublerbay.fleet.model import Series
from doublerbay.grid.lattice import Frame, Stringer
from doublerbay.grid.location import FuselagePoint, WingPoint
from doublerbay.grid.wing import Rib, Spar, Surface
from doublerbay.grid.zone import Side
from doublerbay.ids import FindingId, Registration, Serial
from doublerbay.measure.length import Length
from doublerbay.measure.usage import Cycles, Hours, Usage
from doublerbay.store.mapping import geometry_from, measurements_of

# Which fields of a measurement document are lengths, and so cross as strings.
EnumT = TypeVar("EnumT", bound=Enum)

LENGTH_FIELDS = frozenset(
    {"depth", "along", "around", "length", "width", "worst_depth", "worst_pit", "height"}
)
ANGLE_FIELDS = frozenset({"bearing"})


def inches(text: str, what: str = "length") -> Length:
    """Parse a measurement off the wire the same way it is parsed off a card."""
    try:
        return Length.of_inches(Decimal(text))
    except (InvalidOperation, ValueError, TypeError):
        raise BadReading(f"{text!r} is not a {what} in inches") from None


def read_enum(kind: type[EnumT], value: str, what: str) -> EnumT:
    """Read an enum off the wire, refusing an unknown value the way the domain does.

    Calling the enum directly raises ValueError, which is not one of the
    refusals this service knows how to turn into a status code, so it would
    leave as a five hundred. A caller sending a method this desk does not have
    has made a bad request, not broken the server.
    """
    try:
        return kind(value)
    except ValueError:
        every = ", ".join(str(one.value) for one in kind)
        raise BadReading(f"{value!r} is not a {what}, it is one of {every}") from None


def measurements_out(geometry: Geometry) -> dict[str, Any]:
    """One measurement set, with its lengths as the text a person would write."""
    stored = measurements_of(geometry)
    sent: dict[str, Any] = {}
    for name, value in stored.items():
        if name in LENGTH_FIELDS:
            sent[name] = str(Length(int(value)).inches)
        elif name in ANGLE_FIELDS:
            sent[name] = str(Decimal(int(value)) / 10)
        else:
            sent[name] = value
    return sent


def measurements_in(kind: DamageKind, sent: dict[str, Any]) -> Geometry:
    """Rebuild a measurement set from what arrived, refusing anything unreadable."""
    stored: dict[str, Any] = {}
    for name, value in sent.items():
        if name in LENGTH_FIELDS:
            stored[name] = inches(str(value), name).mils
        elif name in ANGLE_FIELDS:
            try:
                stored[name] = int((Decimal(str(value)) * 10).to_integral_value())
            except InvalidOperation:
                raise BadReading(f"{value!r} is not a bearing in degrees") from None
        else:
            stored[name] = value
    return geometry_from(kind, stored)


class UsageOut(BaseModel):
    cycles: int
    hours: str


class UsageIn(BaseModel):
    cycles: int = Field(ge=0)
    hours: str

    def to_domain(self) -> Usage:
        try:
            return Usage(Cycles(self.cycles), Hours.of_hours(Decimal(self.hours)))
        except InvalidOperation:
            raise BadReading(f"{self.hours!r} is not a count of hours") from None


class FuselageLocationIn(BaseModel):
    structure: str = Field(default="fuselage", pattern="^fuselage$")
    frame: int
    aft_of_frame: str
    stringer: int
    below_stringer: str
    side: str = Field(pattern="^[LRC]$")

    def to_domain(self) -> FuselagePoint:
        side = Side(self.side)
        return FuselagePoint(
            Frame(self.frame),
            inches(self.aft_of_frame, "offset"),
            Stringer(self.stringer, side) if self.stringer else Stringer(0),
            inches(self.below_stringer, "offset"),
        )


class WingLocationIn(BaseModel):
    structure: str = Field(default="wing", pattern="^wing$")
    rib: int
    outboard_of_rib: str
    spar: str
    aft_of_spar: str
    surface: str
    side: str = Field(pattern="^[LR]$")

    def to_domain(self) -> WingPoint:
        return WingPoint(
            Rib(self.rib, Side(self.side)),
            inches(self.outboard_of_rib, "offset"),
            Spar(self.spar),
            inches(self.aft_of_spar, "offset"),
            Surface(self.surface),
        )


class LocationOut(BaseModel):
    structure: str
    written: str
    side: str


def location_out(finding: Finding) -> LocationOut:
    point = finding.point
    structure = "fuselage" if isinstance(point, FuselagePoint) else "wing"
    return LocationOut(structure=structure, written=str(point), side=point.side.value)


class FindingIn(BaseModel):
    """A damage card as it arrives."""

    serial: str
    kind: str
    origin: str
    source: str
    found_on: date
    at_usage: UsageIn
    fuselage: FuselageLocationIn | None = None
    wing: WingLocationIn | None = None
    measurements: dict[str, Any]
    note: str = ""

    def to_domain(self, reference: FindingId) -> Finding:
        if (self.fuselage is None) == (self.wing is None):
            raise BadReading("a card says where it is, on one structure or the other")
        if self.fuselage is not None:
            point: FuselagePoint | WingPoint = self.fuselage.to_domain()
        else:
            assert self.wing is not None
            point = self.wing.to_domain()
        kind = DamageKind(self.kind)
        return Finding(
            reference=reference,
            serial=Serial(self.serial),
            kind=kind,
            geometry=measurements_in(kind, self.measurements),
            point=point,
            found_on=self.found_on,
            source=DiscoverySource(self.source),
            origin=DamageOrigin(self.origin),
            at_usage=self.at_usage.to_domain(),
            note=self.note,
        )


class FindingOut(BaseModel):
    """A damage card as it goes back."""

    reference: str
    serial: str
    kind: str
    origin: str
    source: str
    found_on: date
    revision: int
    at_usage: UsageOut
    location: LocationOut
    measurements: dict[str, Any]
    superseded_by: str | None
    note: str


def finding_out(finding: Finding) -> FindingOut:
    return FindingOut(
        reference=str(finding.reference),
        serial=finding.serial.number,
        kind=finding.kind.value,
        origin=finding.origin.value,
        source=finding.source.value,
        found_on=finding.found_on,
        revision=finding.revision,
        at_usage=UsageOut(
            cycles=finding.at_usage.cycles.count, hours=str(finding.at_usage.hours.hours)
        ),
        location=location_out(finding),
        measurements=measurements_out(finding.geometry),
        superseded_by=str(finding.superseded_by) if finding.superseded_by else None,
        note=finding.note,
    )


class ReasonOut(BaseModel):
    rule: str
    detail: str
    passed: bool


class VerdictOut(BaseModel):
    disposition: str
    page: str | None
    revision: int | None
    why: str
    reasons: list[ReasonOut]


class AssessmentOut(BaseModel):
    reference: str
    part: str
    panel_kind: str
    region: str
    thickness: str
    edge_margin: str
    verdict: VerdictOut


def assessment_out(assessment: Assessment) -> AssessmentOut:
    verdict = assessment.verdict
    return AssessmentOut(
        reference=str(assessment.finding.reference),
        part=str(assessment.placement.part),
        panel_kind=assessment.placement.panel_kind.value,
        region=assessment.placement.region.value,
        thickness=str(assessment.placement.thickness.inches),
        edge_margin=str(assessment.placement.edge_margin.inches),
        verdict=VerdictOut(
            disposition=verdict.disposition.value,
            page=str(verdict.reference) if verdict.reference else None,
            revision=verdict.revision,
            why=verdict.why(),
            reasons=[
                ReasonOut(rule=one.rule, detail=one.detail, passed=one.passed)
                for one in verdict.reasons
            ],
        ),
    )


class AircraftOut(BaseModel):
    serial: str
    registration: str
    series: str
    line_number: int
    delivered: date
    withdrawn: date | None


def aircraft_out(aircraft: Aircraft) -> AircraftOut:
    return AircraftOut(
        serial=aircraft.serial.number,
        registration=str(aircraft.registration),
        series=aircraft.series.value,
        line_number=aircraft.line_number,
        delivered=aircraft.delivered,
        withdrawn=aircraft.withdrawn,
    )


class AircraftIn(BaseModel):
    serial: str
    registration: str
    series: str
    line_number: int = Field(ge=1)
    delivered: date

    def to_domain(self) -> Aircraft:
        return Aircraft(
            serial=Serial(self.serial),
            registration=Registration.parse(self.registration),
            series=Series(self.series),
            line_number=self.line_number,
            delivered=self.delivered,
        )
