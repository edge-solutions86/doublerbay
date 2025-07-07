"""What an audit says when something does not add up.

Two audits in this package now, one over the structure definition and one over
the manual, and they were about to grow a second copy of the same two types.
The shape is the same either way: how bad it is, what it is about, and what is
wrong with it, sorted so the things that stop work come first.

Blocking means the definition is not fit to disposition damage against.
Advisory means somebody should look at it and the desk can carry on meanwhile.
There is deliberately no third level, because a middle one turns into a place
to put anything nobody wants to argue about.
"""

from __future__ import annotations

from collections.abc import Iterable
from dataclasses import dataclass
from enum import Enum


class Severity(Enum):
    """Whether a complaint stops the thing being used."""

    BLOCKING = "blocking"
    ADVISORY = "advisory"

    @property
    def stops_work(self) -> bool:
        return self is Severity.BLOCKING

    @property
    def rank(self) -> int:
        """Blocking sorts first, which alphabetical order would not manage."""
        return 0 if self is Severity.BLOCKING else 1

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True, slots=True)
class Complaint:
    """One thing that does not add up."""

    severity: Severity
    about: str
    says: str

    def __str__(self) -> str:
        return f"[{self.severity}] {self.about}: {self.says}"


def sorted_complaints(complaints: Iterable[Complaint]) -> tuple[Complaint, ...]:
    """Blocking first, then by what they are about, so a report reads the same twice."""
    return tuple(
        sorted(complaints, key=lambda complaint: (complaint.severity.rank, complaint.about))
    )


def blocking(complaints: Iterable[Complaint]) -> tuple[Complaint, ...]:
    return tuple(complaint for complaint in complaints if complaint.severity.stops_work)


def is_clear(complaints: Iterable[Complaint]) -> bool:
    """Whether anything found stops the definition being used."""
    return not blocking(complaints)
