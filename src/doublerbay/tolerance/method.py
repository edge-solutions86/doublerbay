"""How a repeat inspection is actually done, and what each way of doing it can find.

Setting a repeat inspection is not much use without saying how. A man walking
round the aeroplane will see a two inch crack and will not see a quarter inch
one, and a quarter inch crack under a doubler he will not see at all because it
is on the other side of the skin. So the method is chosen from what has to be
found rather than from what is convenient, and the cheapest method that can
find it is the one that goes on the card.

The order they are listed in is the order of cost, not of sensitivity. Eddy
current finds far smaller cracks than a detailed visual and it needs the paint
off, a trained operator and an aeroplane that is not going anywhere, so it is
the last thing chosen and not the first.
"""

from __future__ import annotations

from enum import Enum

from doublerbay.errors import BadReading, NotOnFile
from doublerbay.measure.length import Length


class InspectionMethod(Enum):
    """The ways the shop can be asked to look at something."""

    GVI = "general visual"
    DVI = "detailed visual"
    XRAY = "radiographic"
    UT = "ultrasonic"
    LFEC = "low frequency eddy current"
    HFEC = "high frequency eddy current"

    @property
    def finds_down_to(self) -> Length:
        """The smallest crack this method can be relied on to find."""
        return _DETECTABLE[self]

    @property
    def cost_rank(self) -> int:
        """How expensive it is to run, cheapest first."""
        return _COST[self]

    @property
    def is_ndt(self) -> bool:
        """Anything that needs an instrument and an operator rather than a light."""
        return self not in (InspectionMethod.GVI, InspectionMethod.DVI)

    @property
    def needs_paint_off(self) -> bool:
        return self in (InspectionMethod.HFEC, InspectionMethod.LFEC)

    @property
    def reaches_the_far_side(self) -> bool:
        """Whether it finds damage on the side of the skin nobody can see."""
        return self in (InspectionMethod.LFEC, InspectionMethod.UT, InspectionMethod.XRAY)

    def can_find(self, size: Length) -> bool:
        if size.mils <= 0:
            raise BadReading("nothing has no size to find")
        return size >= self.finds_down_to

    def __str__(self) -> str:
        return self.value


_DETECTABLE = {
    InspectionMethod.GVI: Length.of_inches("2.000"),
    InspectionMethod.DVI: Length.of_inches("0.500"),
    InspectionMethod.XRAY: Length.of_inches("0.250"),
    InspectionMethod.UT: Length.of_inches("0.100"),
    InspectionMethod.LFEC: Length.of_inches("0.100"),
    InspectionMethod.HFEC: Length.of_inches("0.050"),
}

_COST = {
    InspectionMethod.GVI: 0,
    InspectionMethod.DVI: 1,
    InspectionMethod.XRAY: 2,
    InspectionMethod.UT: 3,
    InspectionMethod.LFEC: 4,
    InspectionMethod.HFEC: 5,
}


def by_cost() -> tuple[InspectionMethod, ...]:
    return tuple(sorted(InspectionMethod, key=lambda method: method.cost_rank))


def cheapest_that_finds(size: Length, far_side: bool = False) -> InspectionMethod:
    """The cheapest method that can be relied on to find something of this size."""
    for method in by_cost():
        if far_side and not method.reaches_the_far_side:
            continue
        if method.can_find(size):
            return method
    raise NotOnFile(f"nothing on file finds {size.inches} reliably")


def is_credible(method: InspectionMethod, size: Length, far_side: bool = False) -> bool:
    """Whether asking for this method would actually find what is being looked for."""
    if far_side and not method.reaches_the_far_side:
        return False
    return method.can_find(size)
