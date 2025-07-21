"""Chapter 57 of the V320 manual, allowable damage to wing skin.

The chapter is laid out like chapter 53 and the numbers are not close to it,
for one reason: the lower surface spends the flight in tension. A scratch on
the underside of a wing is sitting in the stress field that grows cracks, and
the book gives it roughly half of what the same scratch gets on top and a
quarter of what it gets on a fuselage panel.

The other difference is thickness. Wing plate runs from a quarter of an inch at
the root down to a tenth at the tip, so a fractional limit written against the
thickness at the damage moves a long way across one panel. The absolute caps
are there to stop the fractional ones from becoming generous on the heavy
plate inboard.
"""

from __future__ import annotations

from datetime import date
from decimal import Decimal
from functools import lru_cache

from doublerbay.damage.kind import DamageKind
from doublerbay.grid.ata import Chapter, Reference
from doublerbay.manual.document import Manual, Page, Revision
from doublerbay.manual.limits import LimitSet
from doublerbay.manual.table import LimitKey, LimitRow, LimitTable
from doublerbay.measure.length import Length
from doublerbay.measure.ratio import Ratio, Taper
from doublerbay.structure.build import Region
from doublerbay.structure.panel import PanelKind

CHAPTER = Chapter.WINGS
ADL = Reference.parse("57-10")

DEFORMATION = Reference.parse("57-10-01")
MATERIAL_LOSS = Reference.parse("57-20-01")
CORROSION_PAGE = Reference.parse("57-30-01")
PENETRATION = Reference.parse("57-40-01")

REVISIONS = (
    Revision(7, date(2021, 6, 1), date(2021, 9, 1)),
    Revision(8, date(2022, 10, 1), date(2023, 1, 1)),
    Revision(9, date(2024, 4, 1), date(2024, 7, 1)),
    Revision(10, date(2025, 5, 1), date(2025, 8, 1)),
)

PAGES = (
    Page(DEFORMATION, "allowable damage, wing skin dents", 7),
    Page(MATERIAL_LOSS, "allowable damage, wing skin material loss", 7),
    Page(CORROSION_PAGE, "allowable damage, wing skin corrosion", 7),
    Page(PENETRATION, "allowable damage, wing skin cracks and penetration", 7),
)

TWO_AND_A_HALF_DIAMETERS = Decimal("2.5")
BLEND = Taper(30)

NOTHING_ALLOWED_IN = (Region.FASTENER_ROW, Region.CUTOUT_CORNER, Region.CHEM_MILL_STEP)

# Depth caps and residual floors, by surface. The lower surface is the tension
# one and it gets half of what the upper gets.
SURFACE_LIMITS: dict[PanelKind, tuple[str, str, str]] = {
    PanelKind.WING_UPPER: ("0.05", "0.88", "0.010"),
    PanelKind.WING_LOWER: ("0.025", "0.94", "0.005"),
}


def manual() -> Manual:
    return Manual(CHAPTER, REVISIONS)


def _dent_rows() -> list[LimitRow]:
    rows: list[LimitRow] = [
        LimitRow(
            LimitKey(DamageKind.DENT),
            LimitSet(
                max_depth=Length.of_inches("0.040"),
                max_sharpness=Ratio("0.04"),
                min_edge_margin_diameters=TWO_AND_A_HALF_DIAMETERS,
            ),
            DEFORMATION,
        ),
        LimitRow(
            LimitKey(DamageKind.DENT, PanelKind.WING_LOWER),
            LimitSet.nothing_allowable(),
            DEFORMATION,
        ),
    ]
    rows.extend(
        LimitRow(LimitKey(DamageKind.DENT, None, region), LimitSet.nothing_allowable(), DEFORMATION)
        for region in NOTHING_ALLOWED_IN
    )
    return rows


def _score_rows() -> list[LimitRow]:
    rows: list[LimitRow] = []
    for kind in (DamageKind.SCRATCH, DamageKind.GOUGE, DamageKind.NICK):
        for surface, (share, residual, cap) in SURFACE_LIMITS.items():
            rows.append(
                LimitRow(
                    LimitKey(kind, surface),
                    LimitSet(
                        max_depth=Length.of_inches(cap),
                        max_depth_fraction=Ratio(share),
                        min_residual_fraction=Ratio(residual),
                        min_edge_margin_diameters=TWO_AND_A_HALF_DIAMETERS,
                        blend_taper=BLEND,
                    ),
                    MATERIAL_LOSS,
                )
            )
        rows.append(
            LimitRow(
                LimitKey(kind),
                LimitSet(
                    max_depth=Length.of_inches("0.005"),
                    max_depth_fraction=Ratio("0.025"),
                    min_residual_fraction=Ratio("0.94"),
                    min_edge_margin_diameters=TWO_AND_A_HALF_DIAMETERS,
                    blend_taper=BLEND,
                ),
                MATERIAL_LOSS,
            )
        )
        rows.extend(
            LimitRow(LimitKey(kind, None, region), LimitSet.nothing_allowable(), MATERIAL_LOSS)
            for region in NOTHING_ALLOWED_IN
        )
    return rows


def _corrosion_rows() -> list[LimitRow]:
    rows = [
        LimitRow(
            LimitKey(DamageKind.CORROSION, PanelKind.WING_UPPER),
            LimitSet(
                max_depth_fraction=Ratio("0.05"),
                min_residual_fraction=Ratio("0.88"),
                max_area=Decimal("3.00"),
                blend_taper=BLEND,
            ),
            CORROSION_PAGE,
        ),
        LimitRow(
            LimitKey(DamageKind.CORROSION, PanelKind.WING_LOWER),
            LimitSet(
                max_depth_fraction=Ratio("0.025"),
                min_residual_fraction=Ratio("0.94"),
                max_area=Decimal("1.50"),
                blend_taper=BLEND,
            ),
            CORROSION_PAGE,
        ),
        LimitRow(
            LimitKey(DamageKind.CORROSION),
            LimitSet(
                max_depth_fraction=Ratio("0.025"),
                min_residual_fraction=Ratio("0.94"),
                max_area=Decimal("1.50"),
                blend_taper=BLEND,
            ),
            CORROSION_PAGE,
        ),
        LimitRow(
            LimitKey(DamageKind.LIGHTNING_BURN),
            LimitSet(
                max_depth=Length.of_inches("0.006"),
                max_area=Decimal("1.00"),
                blend_taper=BLEND,
            ),
            CORROSION_PAGE,
        ),
    ]
    rows.extend(
        LimitRow(LimitKey(kind, None, region), LimitSet.nothing_allowable(), CORROSION_PAGE)
        for kind in (DamageKind.CORROSION, DamageKind.LIGHTNING_BURN)
        for region in NOTHING_ALLOWED_IN
    )
    return rows


def _penetration_rows() -> list[LimitRow]:
    return [
        LimitRow(LimitKey(kind), LimitSet.nothing_allowable(), PENETRATION)
        for kind in (
            DamageKind.CRACK,
            DamageKind.HOLE,
            DamageKind.PUNCTURE,
            DamageKind.BUCKLE,
            DamageKind.DISBOND,
        )
    ]


@lru_cache(maxsize=8)
def allowable_damage(revision: int) -> LimitTable:
    """The whole allowable damage table for the wing, at one revision."""
    rows = _dent_rows() + _score_rows() + _corrosion_rows() + _penetration_rows()
    return LimitTable(ADL, revision, tuple(rows))
