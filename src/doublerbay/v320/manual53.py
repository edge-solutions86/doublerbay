"""Chapter 53 of the V320 manual, allowable damage to fuselage skin.

Four subjects. Deformation at 53-10, material loss at 53-20, corrosion and
lightning at 53-30, and cracking and penetration at 53-40, which is the short
one because the answer there is always a repair.

The numbers tighten as you move inboard on the structure. Unpressurised tail
cone skin gets the loosest limits, ordinary pressurised barrel skin is half
that, and anything sitting on a fastener row or in a cutout corner has no
allowable limit at all whatever it measures.

Revision 12 tightened the pressurised skin dent limits after a fleet campaign,
which is why the table is built per revision rather than once. Anything
dispositioned before that revision came into force was assessed against the
looser numbers and stays that way.
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

CHAPTER = Chapter.FUSELAGE
ADL = Reference.parse("53-10")

DEFORMATION = Reference.parse("53-10-01")
MATERIAL_LOSS = Reference.parse("53-20-01")
CORROSION_PAGE = Reference.parse("53-30-01")
LIGHTNING_PAGE = Reference.parse("53-30-02")
PENETRATION = Reference.parse("53-40-01")

# The revision that tightened pressurised skin dents.
DENT_CAMPAIGN_REVISION = 12

REVISIONS = (
    Revision(9, date(2021, 11, 1), date(2022, 2, 1)),
    Revision(10, date(2023, 3, 1), date(2023, 6, 1)),
    Revision(11, date(2023, 12, 1), date(2024, 3, 1)),
    Revision(12, date(2024, 9, 1), date(2024, 12, 1)),
    Revision(13, date(2025, 8, 1), date(2025, 11, 1)),
)

PAGES = (
    Page(DEFORMATION, "allowable damage, dents and buckles", 9),
    Page(MATERIAL_LOSS, "allowable damage, scratches gouges and nicks", 9),
    Page(CORROSION_PAGE, "allowable damage, corrosion", 9),
    Page(LIGHTNING_PAGE, "allowable damage, lightning strike", 10),
    Page(PENETRATION, "allowable damage, cracks and penetration", 9),
)

TWO_DIAMETERS = Decimal("2.0")
BLEND = Taper(20)

NOTHING_ALLOWED_IN = (Region.FASTENER_ROW, Region.CUTOUT_CORNER, Region.CHEM_MILL_STEP)


def manual() -> Manual:
    return Manual(CHAPTER, REVISIONS)


def _dent_rows(revision: int) -> list[LimitRow]:
    pressurised_depth = "0.060" if revision >= DENT_CAMPAIGN_REVISION else "0.080"
    pressurised_sharpness = "0.05" if revision >= DENT_CAMPAIGN_REVISION else "0.08"
    rows = [
        LimitRow(
            LimitKey(DamageKind.DENT),
            LimitSet(
                max_depth=Length.of_inches("0.100"),
                max_sharpness=Ratio("0.10"),
                min_edge_margin_diameters=TWO_DIAMETERS,
            ),
            DEFORMATION,
        ),
        LimitRow(
            LimitKey(DamageKind.DENT, PanelKind.PRESSURISED_SKIN),
            LimitSet(
                max_depth=Length.of_inches(pressurised_depth),
                max_sharpness=Ratio(pressurised_sharpness),
                min_edge_margin_diameters=TWO_DIAMETERS,
            ),
            DEFORMATION,
        ),
        LimitRow(
            LimitKey(DamageKind.DENT, PanelKind.CROWN_SKIN),
            LimitSet(
                max_depth=Length.of_inches("0.080"),
                max_sharpness=Ratio("0.08"),
                min_edge_margin_diameters=TWO_DIAMETERS,
            ),
            DEFORMATION,
        ),
        LimitRow(
            LimitKey(DamageKind.DENT, PanelKind.UNPRESSURISED_SKIN),
            LimitSet(
                max_depth=Length.of_inches("0.150"),
                max_sharpness=Ratio("0.15"),
                min_edge_margin_diameters=TWO_DIAMETERS,
            ),
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
    for kind, share, residual in (
        (DamageKind.SCRATCH, "0.10", "0.90"),
        (DamageKind.GOUGE, "0.08", "0.92"),
        (DamageKind.NICK, "0.10", "0.90"),
    ):
        rows.append(
            LimitRow(
                LimitKey(kind),
                LimitSet(
                    max_depth_fraction=Ratio(share),
                    min_residual_fraction=Ratio(residual),
                    min_edge_margin_diameters=TWO_DIAMETERS,
                    blend_taper=BLEND,
                ),
                MATERIAL_LOSS,
            )
        )
        rows.append(
            LimitRow(
                LimitKey(kind, PanelKind.PRESSURISED_SKIN),
                LimitSet(
                    max_depth_fraction=Ratio("0.05"),
                    min_residual_fraction=Ratio("0.95"),
                    min_edge_margin_diameters=TWO_DIAMETERS,
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
            LimitKey(DamageKind.CORROSION),
            LimitSet(
                max_depth_fraction=Ratio("0.10"),
                min_residual_fraction=Ratio("0.90"),
                max_area=Decimal("6.00"),
                blend_taper=Taper(30),
            ),
            CORROSION_PAGE,
        ),
        LimitRow(
            LimitKey(DamageKind.CORROSION, PanelKind.PRESSURISED_SKIN),
            LimitSet(
                max_depth_fraction=Ratio("0.05"),
                min_residual_fraction=Ratio("0.95"),
                max_area=Decimal("4.00"),
                blend_taper=Taper(30),
            ),
            CORROSION_PAGE,
        ),
        LimitRow(
            LimitKey(DamageKind.LIGHTNING_BURN),
            LimitSet(
                max_depth=Length.of_inches("0.010"),
                max_area=Decimal("2.00"),
                blend_taper=BLEND,
            ),
            LIGHTNING_PAGE,
        ),
    ]
    for kind, page in (
        (DamageKind.CORROSION, CORROSION_PAGE),
        (DamageKind.LIGHTNING_BURN, LIGHTNING_PAGE),
    ):
        rows.extend(
            LimitRow(LimitKey(kind, None, region), LimitSet.nothing_allowable(), page)
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
    """The whole allowable damage table for the barrel, at one revision."""
    rows = _dent_rows(revision) + _score_rows() + _corrosion_rows() + _penetration_rows()
    return LimitTable(ADL, revision, tuple(rows))
