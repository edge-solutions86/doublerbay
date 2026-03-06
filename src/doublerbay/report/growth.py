"""How fast a damage is actually growing, measured rather than predicted.

The damage tolerance analysis says how fast a crack in this material at this
stress ought to grow. This says how fast this one did. The two are different
numbers and the second one is the one that decides whether the aeroplane keeps
flying, because it is the only one that knows about the particular corrosion
pit the crack started from.

Rate is per thousand flight cycles rather than per day. Growth is driven by
pressurisation, and an aeroplane doing four short sectors a day and one doing
a single long one are not accumulating damage at the same rate per day even
though the calendar says they are.

Two readings is the minimum and it is a weak answer. Three is a trend. The
report says how many it is working from rather than presenting a two point
line as though it were a curve.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import ROUND_HALF_UP, Decimal

from doublerbay.damage.crack import Crack
from doublerbay.damage.finding import Finding
from doublerbay.errors import BadReading
from doublerbay.ids import FindingId
from doublerbay.measure.length import Length

# What a rate is quoted against, because growth follows pressurisation.
PER_CYCLES = 1000
PLACES = Decimal("0.0001")
# Below this many cycles between readings the rate is noise, not a measurement.
SHORTEST_SPAN = 50


@dataclass(frozen=True, slots=True)
class Step:
    """The growth between two consecutive readings of one card."""

    was: Length
    now: Length
    cycles: int

    def __post_init__(self) -> None:
        if self.cycles < 0:
            raise BadReading("a growth step does not run backwards in cycles")

    @property
    def grew_by(self) -> Length:
        return Length(self.now.mils - self.was.mils)

    @property
    def is_measurable(self) -> bool:
        """Whether enough flying happened between the readings to mean anything."""
        return self.cycles >= SHORTEST_SPAN

    @property
    def rate(self) -> Decimal:
        """Inches per thousand cycles, or nothing measurable if the span is short."""
        if not self.is_measurable:
            raise BadReading(f"{self.cycles} cycles is too short a span to take a rate from")
        per = Decimal(self.grew_by.mils) * PER_CYCLES / Decimal(self.cycles)
        return (per / 1000).quantize(PLACES, rounding=ROUND_HALF_UP)

    def __str__(self) -> str:
        return f"{self.was.inches} to {self.now.inches} over {self.cycles} FC"


@dataclass(frozen=True, slots=True)
class Growth:
    """One card's measured growth across every revision on file."""

    reference: FindingId
    readings: int
    steps: tuple[Step, ...]

    @property
    def is_a_trend(self) -> bool:
        """Three readings is a trend. Two is a line through two points."""
        return self.readings >= 3

    @property
    def has_grown(self) -> bool:
        return any(one.grew_by.mils > 0 for one in self.steps)

    @property
    def total(self) -> Length:
        if not self.steps:
            return Length(0)
        return Length(self.steps[-1].now.mils - self.steps[0].was.mils)

    @property
    def measurable(self) -> tuple[Step, ...]:
        return tuple(one for one in self.steps if one.is_measurable)

    @property
    def worst_rate(self) -> Decimal | None:
        """The fastest measured stretch, which is the one to plan against."""
        rates = [one.rate for one in self.measurable]
        return max(rates) if rates else None

    def __str__(self) -> str:
        if not self.steps:
            return f"{self.reference}: one reading, no growth measured"
        rate = self.worst_rate
        pace = f", worst {rate} in per 1000 FC" if rate is not None else ", no measurable rate"
        return f"{self.reference}: {self.total.inches} over {self.readings} readings{pace}"


def measured_length(finding: Finding) -> Length:
    """The number that matters for growth, which depends on the kind.

    A crack grows along its length. Anything else is reported by whichever of
    its measurements the limit table is written against, and for now that is
    only the crack, because nothing else on this fleet is tracked for growth.
    """
    geometry = finding.geometry
    if not isinstance(geometry, Crack):
        raise BadReading(
            f"{finding.reference} is a {finding.kind}, which is not tracked for growth"
        )
    return geometry.length


def growth_of(revisions: tuple[Finding, ...]) -> Growth:
    """Work out one card's growth from its revisions, oldest first."""
    if not revisions:
        raise BadReading("a card with no revisions has no growth")
    ordered = sorted(revisions, key=lambda one: one.revision)
    steps = tuple(
        Step(
            was=measured_length(before),
            now=measured_length(after),
            cycles=after.at_usage.cycles.count - before.at_usage.cycles.count,
        )
        for before, after in zip(ordered, ordered[1:], strict=False)
    )
    return Growth(reference=ordered[0].reference, readings=len(ordered), steps=steps)
