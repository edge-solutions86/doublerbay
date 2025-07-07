"""Reading the book against itself before anybody dispositions damage with it.

A limit table is transcribed by hand and the mistakes it makes are quiet ones.
A kind with no row at all reads as a lookup miss, which somebody works around.
A row that allows something and caps nothing reads as an answer, and clears
everything. A depth cap that leaves less metal than the residual floor demands
is a contradiction that fires only on the thicknesses where the two cross, so
it passes on most of the fleet and fails on the heavy panels.

The sweep here walks the whole cross product of damage kind, panel kind and
region and asks the table to answer each one, which is the only way to find the
pairs of rows that tie. That is a few hundred lookups and it costs nothing next
to getting one of them wrong on an aeroplane.
"""

from __future__ import annotations

from doublerbay.complaints import Complaint, Severity, sorted_complaints
from doublerbay.damage.kind import DamageKind
from doublerbay.errors import BadReading, NotOnFile
from doublerbay.manual.book import Book
from doublerbay.manual.document import Page
from doublerbay.manual.table import LimitTable
from doublerbay.measure.length import Length
from doublerbay.structure.build import Region
from doublerbay.structure.panel import PanelKind

# The thicknesses a fractional limit is checked against. Thin enough and thick
# enough to catch a cap and a floor that cross somewhere in between.
PROBE_THICKNESSES = (
    Length.of_inches("0.016"),
    Length.of_inches("0.040"),
    Length.of_inches("0.063"),
    Length.of_inches("0.125"),
    Length.of_inches("0.250"),
)


def _sweep(table: LimitTable) -> list[Complaint]:
    """Ask the table every question it could be asked and report what it cannot answer."""
    found: list[Complaint] = []
    for damage in DamageKind:
        for panel in PanelKind:
            for region in Region:
                try:
                    table.lookup(damage, panel, region)
                except NotOnFile:
                    found.append(
                        Complaint(
                            Severity.ADVISORY,
                            str(table.reference),
                            f"no row answers for {damage} on {panel.value} {region.value}",
                        )
                    )
                except BadReading as clash:
                    found.append(Complaint(Severity.BLOCKING, str(table.reference), str(clash)))
    return found


def _open_rows(table: LimitTable) -> list[Complaint]:
    return [
        Complaint(
            Severity.BLOCKING,
            str(row.reference),
            f"{row.key} allows damage and caps nothing",
        )
        for row in table.rows
        if row.limits.is_open
    ]


def _crossed_limits(table: LimitTable) -> list[Complaint]:
    """Rows whose depth cap leaves less metal than their own residual floor."""
    found: list[Complaint] = []
    for row in table.rows:
        for thickness in PROBE_THICKNESSES:
            left = row.limits.implied_residual(thickness)
            floor = row.limits.residual_floor(thickness)
            if left is None or floor is None:
                continue
            if left < floor:
                found.append(
                    Complaint(
                        Severity.BLOCKING,
                        str(row.reference),
                        f"{row.key} leaves {left.inches} at {thickness.inches} "
                        f"and asks for {floor.inches}",
                    )
                )
                break
    return found


def _uncovered_kinds(table: LimitTable) -> list[Complaint]:
    missing = frozenset(DamageKind) - table.kinds_covered()
    return [
        Complaint(Severity.BLOCKING, str(table.reference), f"no row anywhere for a {kind}")
        for kind in sorted(missing, key=str)
    ]


def _orphan_pages(table: LimitTable, pages: tuple[Page, ...]) -> list[Complaint]:
    """Rows pointing at a page that is not in the book at this revision."""
    live = {page.reference for page in pages if page.is_live_at(table.revision)}
    cited = {row.reference for row in table.rows}
    return [
        Complaint(
            Severity.ADVISORY,
            str(reference),
            f"cited by the table and not in the book at revision {table.revision}",
        )
        for reference in sorted(cited - live, key=str)
    ]


def audit_table(table: LimitTable, pages: tuple[Page, ...] = ()) -> tuple[Complaint, ...]:
    """Walk one table and report everything it cannot answer or contradicts itself on."""
    found = _uncovered_kinds(table)
    found.extend(_open_rows(table))
    found.extend(_crossed_limits(table))
    found.extend(_orphan_pages(table, pages))
    found.extend(_sweep(table))
    return sorted_complaints(found)


def audit_book(
    book: Book, revision_by_chapter: dict[str, int] | None = None
) -> tuple[Complaint, ...]:
    """Walk every chapter on the shelf at its latest revision."""
    found: list[Complaint] = []
    for chapter in sorted(book.chapters(), key=str):
        holding = book.holding(chapter)
        revision = holding.manual.latest.number
        if revision_by_chapter is not None:
            revision = revision_by_chapter.get(str(chapter), revision)
        table = holding.table_at(revision)
        found.extend(audit_table(table, holding.pages_at(revision)))
    return sorted_complaints(found)
