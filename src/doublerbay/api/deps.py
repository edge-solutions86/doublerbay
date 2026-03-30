"""What every route needs from the request, in one place.

Eleven route modules were importing the dependency and the reference parser
out of the findings routes. That worked, and it meant the corrosion routes
imported the damage card routes to get at a two line function, so touching
the findings module could break a module that has nothing to do with findings.

Nothing here imports a route. Everything here is the small shared plumbing a
route reaches for before it does anything of its own, which is why it can sit
underneath all of them.
"""

from __future__ import annotations

from datetime import date

from fastapi import Request

from doublerbay.disposition.engineering import OrderId
from doublerbay.ids import FindingId, RepairId, Serial


def deps(request: Request):
    """The application's dependencies, off the app rather than out of a global."""
    return request.app.state.deps


def reference_from(text: str) -> FindingId:
    """A damage report number off the path, refused by name if it is not one."""
    return FindingId.parse(text)


def repair_from(text: str) -> RepairId:
    """A repair number off the path."""
    return RepairId.parse(text)


def order_from(text: str) -> OrderId:
    """An engineering order number off the path."""
    return OrderId.parse(text)


def serial_from(text: str) -> Serial:
    """A tail serial off the path."""
    return Serial(text)


def day_or(deps_in, on: date | None) -> date:
    """The day a request is answered as of, taking the service's own if none given."""
    return on if on is not None else deps_in.on()
