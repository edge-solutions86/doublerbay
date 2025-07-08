"""What the desk decides, and every rule it walked to get there.

Four answers. Leave it and record it, grind it out and then leave it, repair it
from the book, or the book has run out and this goes to the manufacturer. They
are ordered, because a damage assessed against several rules takes the worst
answer any of them gave and not the last one that ran.

The reason trail is not decoration. Somebody signs the disposition, and in two
years an auditor asks why a 0.055 dent was left on a pressurised panel. The
answer has to be the page, the revision, the thickness that was read, and the
rules that passed, all of it recorded at the time. Recomputing it later reads
today's book and answers a different question.
"""

from __future__ import annotations

from collections.abc import Iterable
from dataclasses import dataclass
from enum import Enum

from doublerbay.errors import BadReading
from doublerbay.grid.ata import Reference


class Disposition(Enum):
    """What happens to the damage, worst last."""

    ALLOWABLE = "allowable as it stands"
    REWORKABLE = "blend out and re inspect"
    REPAIRABLE = "repair from the manual"
    OUT_OF_LIMITS = "outside the manual"

    @property
    def rank(self) -> int:
        return _RANKS[self]

    @property
    def needs_work(self) -> bool:
        """Anything that puts the aeroplane in a hangar."""
        return self is not Disposition.ALLOWABLE

    @property
    def needs_the_manufacturer(self) -> bool:
        return self is Disposition.OUT_OF_LIMITS

    def worse_than(self, other: Disposition) -> bool:
        return self.rank > other.rank

    def __str__(self) -> str:
        return self.value


_RANKS = {disposition: index for index, disposition in enumerate(Disposition)}


@dataclass(frozen=True, slots=True)
class Reason:
    """One rule, what it was checking, and whether the damage got past it."""

    rule: str
    detail: str
    passed: bool

    def __str__(self) -> str:
        mark = "ok" if self.passed else "no"
        return f"{mark}  {self.rule}: {self.detail}"


@dataclass(frozen=True, slots=True)
class Verdict:
    """One disposition, with the trail that produced it."""

    disposition: Disposition
    reasons: tuple[Reason, ...]
    reference: Reference | None = None
    revision: int | None = None

    def __post_init__(self) -> None:
        if not self.reasons:
            raise BadReading("a verdict carries the rules it walked")

    @property
    def is_allowable(self) -> bool:
        return self.disposition is Disposition.ALLOWABLE

    @property
    def needs_work(self) -> bool:
        return self.disposition.needs_work

    def failures(self) -> tuple[Reason, ...]:
        return tuple(reason for reason in self.reasons if not reason.passed)

    def passes(self) -> tuple[Reason, ...]:
        return tuple(reason for reason in self.reasons if reason.passed)

    def why(self) -> str:
        """One line naming what stopped it, or what let it through."""
        failed = self.failures()
        if failed:
            return "; ".join(reason.detail for reason in failed)
        return "every limit met"

    def with_reason(self, reason: Reason) -> Verdict:
        return Verdict(self.disposition, (*self.reasons, reason), self.reference, self.revision)

    def raised_to(self, disposition: Disposition) -> Verdict:
        """The same trail with a worse answer on it, which is how a cluster escalates."""
        if disposition.rank < self.disposition.rank:
            raise BadReading("a verdict is not talked down once a rule has failed")
        return Verdict(disposition, self.reasons, self.reference, self.revision)

    def __str__(self) -> str:
        page = f" per {self.reference} rev {self.revision}" if self.reference else ""
        return f"{self.disposition}{page}: {self.why()}"


def worst_of(verdicts: Iterable[Verdict]) -> Verdict:
    """The worst answer any rule gave, carrying every trail that led to it."""
    collected = list(verdicts)
    if not collected:
        raise BadReading("there is no worst of nothing")
    worst = max(collected, key=lambda verdict: verdict.disposition.rank)
    reasons = tuple(reason for verdict in collected for reason in verdict.reasons)
    return Verdict(worst.disposition, reasons, worst.reference, worst.revision)
