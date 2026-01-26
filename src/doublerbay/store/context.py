"""Building the tail context off the repositories, once.

Four modules were doing this: the assessment routes, the repair routes, the
command line and the intake service. Four copies of six lines is not a
disaster on the day it is written. It becomes one the day the in service check
is added to three of them.

That check is the reason this is not just tidying. A card raised against an
aeroplane that had not been delivered yet, or one already sold on, would be
assessed against a structure definition that was never on it. The register
knows the delivery date, so the answer is to refuse rather than to guess, and
that refusal has to live wherever the context is built.

The fleet register has its own version of this for the in memory Fleet. This
one is for the repositories, which is what everything above the domain uses.
"""

from __future__ import annotations

from datetime import date

from doublerbay.errors import NotOnFile
from doublerbay.fleet.catalogue import TailContext, TypeCatalogue
from doublerbay.ids import Serial
from doublerbay.store.unit import UnitOfWork


def context_for(
    unit: UnitOfWork, catalogue: TypeCatalogue, serial: Serial, on: date
) -> TailContext:
    """The aeroplane as it stood on a day, refusing one that was not flying.

    The counters come off the log at that day rather than the latest reading.
    Assessing a March card against September counters is how a crack growth
    threshold ends up computed against flying that had not happened yet.
    """
    aircraft = unit.aircraft.get(serial)
    if not aircraft.in_service_on(on):
        raise NotOnFile(f"{serial} was not in service on {on.isoformat()}")
    return TailContext(
        aircraft=aircraft,
        on=on,
        flown=unit.aircraft.log_for(serial).at(on),
        definition=catalogue.definition_for(aircraft.series),
    )
