"""What this package refuses, and what it refuses with.

Everything here also inherits from the builtin a caller would already be
catching, so a route that wraps ValueError keeps working and a route that wants
to tell a bad reading from a genuine lookup miss can. That was the whole point
of collecting them: a damage card with 0.0405 written on it and a panel number
that is not on this aircraft are both refusals, and they need different words
back to the person holding the card.
"""

from __future__ import annotations


class DoublerbayError(Exception):
    """Anything this package raises on purpose."""


class BadReading(DoublerbayError, ValueError):
    """A value on the card cannot be read, or is not one this aircraft can carry."""


class WrongMeasure(DoublerbayError, TypeError):
    """Two quantities that are not the same kind of thing were mixed."""


class NotOnFile(DoublerbayError, LookupError):
    """Something was looked up and the reference data does not hold it."""


class OutOfSequence(DoublerbayError, RuntimeError):
    """A record was asked to do something its current state does not allow."""


class Refused(DoublerbayError):
    """A disposition could not be reached, with the reason it could not."""

    def __init__(self, reason: str, detail: str | None = None) -> None:
        super().__init__(reason if detail is None else f"{reason}: {detail}")
        self.reason = reason
        self.detail = detail
