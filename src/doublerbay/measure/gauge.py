"""Sheet stock, and what the mill is allowed to send you.

A repair doubler is cut from stock, and stock comes in a fixed ladder of
nominal gauges. When the assessment says a damage needs a 0.047 doubler the
shop does not have 0.047, it has 0.050, so anything that sizes a part has to
land on the ladder rather than on the arithmetic.

The mill tolerance matters for a different reason. A skin called out at 0.040
can leave the mill at 0.0365 and still be in spec, so a thickness measured off
the aircraft is compared against the low side of the callout and never against
the callout itself. Getting that backwards condemns good skin.
"""

from __future__ import annotations

from dataclasses import dataclass

from doublerbay.measure.length import Length


@dataclass(frozen=True, slots=True)
class SheetGauge:
    """One rung of the stock ladder, with the spread the mill is allowed."""

    nominal: Length
    under: Length
    over: Length

    @property
    def designation(self) -> str:
        return f"{self.nominal.inches}"

    @property
    def floor(self) -> Length:
        return self.nominal - self.under

    @property
    def ceiling(self) -> Length:
        return self.nominal + self.over

    def admits(self, measured: Length) -> bool:
        return self.floor <= measured <= self.ceiling

    def __str__(self) -> str:
        return self.designation


def _gauge(nominal: str, under: str, over: str) -> SheetGauge:
    return SheetGauge(
        nominal=Length.of_inches(nominal),
        under=Length.of_inches(under),
        over=Length.of_inches(over),
    )


# The clad aluminium sheet the V320 skin and its repair parts are cut from.
# Tolerance widens with the gauge, which is why it is carried per rung and not
# as one percentage.
STOCK_LADDER: tuple[SheetGauge, ...] = (
    _gauge("0.016", "0.002", "0.002"),
    _gauge("0.020", "0.002", "0.002"),
    _gauge("0.025", "0.002", "0.002"),
    _gauge("0.032", "0.003", "0.003"),
    _gauge("0.040", "0.004", "0.004"),
    _gauge("0.050", "0.004", "0.004"),
    _gauge("0.063", "0.005", "0.005"),
    _gauge("0.071", "0.005", "0.005"),
    _gauge("0.080", "0.006", "0.006"),
    _gauge("0.090", "0.006", "0.006"),
    _gauge("0.100", "0.007", "0.007"),
    _gauge("0.125", "0.008", "0.008"),
    _gauge("0.160", "0.010", "0.010"),
    _gauge("0.190", "0.012", "0.012"),
    _gauge("0.250", "0.015", "0.015"),
)

_BY_NOMINAL = {rung.nominal: rung for rung in STOCK_LADDER}


def gauge_for(nominal: Length) -> SheetGauge:
    """The rung called out by this exact nominal thickness."""
    try:
        return _BY_NOMINAL[nominal]
    except KeyError:
        raise ValueError(f"{nominal} is not a stocked gauge") from None


def is_stocked(nominal: Length) -> bool:
    return nominal in _BY_NOMINAL


def next_heavier(wanted: Length) -> SheetGauge:
    """The thinnest rung that is at least as thick as what was asked for.

    Sizing rounds up. A doubler thinner than the calculation is not a doubler.
    """
    for rung in STOCK_LADDER:
        if rung.nominal >= wanted:
            return rung
    raise ValueError(f"{wanted} is heavier than any stocked sheet")


def nearest(wanted: Length) -> SheetGauge:
    """The rung closest to what was asked for, breaking a tie upwards."""
    best = STOCK_LADDER[0]
    best_gap = abs(best.nominal - wanted)
    for rung in STOCK_LADDER[1:]:
        gap = abs(rung.nominal - wanted)
        if gap < best_gap or (gap == best_gap and rung.nominal > best.nominal):
            best, best_gap = rung, gap
    return best


def identify(measured: Length) -> SheetGauge | None:
    """Which callout a measured thickness could have come off.

    Ambiguous by construction once the tolerance bands touch, so this answers
    only when exactly one rung admits the reading.
    """
    admitting = [rung for rung in STOCK_LADDER if rung.admits(measured)]
    if len(admitting) == 1:
        return admitting[0]
    return None
