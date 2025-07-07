"""Checking the type definition against itself before anybody assesses damage with it.

A structure definition is transcribed by hand off a manual, and the two things
that go wrong are a panel that covers nothing and two panels that both claim the
same bay. Neither shows up as an error. The first makes a damage card come back
as no panel found, which somebody will work around by picking the nearest panel.
The second makes the same card resolve to different limits depending on which
panel the search happened to reach first, which nobody notices at all.

So it is worth walking the whole thing once and saying what does not add up.
The complaints come out sorted, blocking ones first, and a definition with a
blocking complaint in it is not one to disposition damage against. The two
types they come back as are shared with the manual audit, which asks the same
kind of question about a different document.
"""

from __future__ import annotations

from collections import Counter
from collections.abc import Iterator

from doublerbay.complaints import Complaint, Severity, sorted_complaints
from doublerbay.grid.lattice import Bay, Frame, Stringer
from doublerbay.grid.zone import Side
from doublerbay.structure.airframe import Airframe
from doublerbay.structure.grid import FuselageGrid
from doublerbay.structure.wingbox import WingBox


def _sided(stringer: Stringer, side: Side) -> Stringer:
    if stringer.is_crown:
        return stringer
    return Stringer(stringer.number, side)


def grid_bays(grid: FuselageGrid, side: Side) -> Iterator[Bay]:
    """Every single bay the grid defines on one hand."""
    for forward, aft in zip(grid.frames, grid.frames[1:], strict=False):
        for upper, lower in zip(grid.stringers, grid.stringers[1:], strict=False):
            yield Bay(
                forward.frame,
                aft.frame,
                _sided(upper.stringer, side),
                _sided(lower.stringer, side),
            )


def _coverage(airframe: Airframe) -> Counter[Bay]:
    counted: Counter[Bay] = Counter()
    for build in airframe.panels:
        counted.update(build.panel.bays())
    return counted


def _members_on_the_grid(airframe: Airframe) -> list[Complaint]:
    found: list[Complaint] = []
    for build in airframe.panels:
        panel = build.panel
        for frame in (panel.forward, panel.aft):
            try:
                airframe.grid.station_of(frame)
            except LookupError:
                found.append(
                    Complaint(Severity.BLOCKING, str(panel.part), f"{frame} is not on the grid")
                )
        for stringer in (panel.upper, panel.lower):
            try:
                airframe.grid.around_of(stringer)
            except LookupError:
                found.append(
                    Complaint(Severity.BLOCKING, str(panel.part), f"{stringer} is not on the grid")
                )
    return found


def _joints_on_their_panel(airframe: Airframe) -> list[Complaint]:
    found: list[Complaint] = []
    for build in airframe.panels:
        panel = build.panel
        for joint in build.joints:
            member = joint.follows
            on_panel = (
                panel.spans_frame(member)
                if isinstance(member, Frame)
                else panel.spans_stringer(member)
            )
            if not on_panel:
                found.append(
                    Complaint(
                        Severity.BLOCKING,
                        str(panel.part),
                        f"{joint.kind} follows {member}, which is not on this panel",
                    )
                )
    return found


def _cutouts_inside_their_panel(airframe: Airframe) -> list[Complaint]:
    found: list[Complaint] = []
    for build in airframe.panels:
        panel = build.panel
        try:
            forward = airframe.grid.station_of(panel.forward).at
            aft = airframe.grid.station_of(panel.aft).at
            upper = airframe.grid.around_of(panel.upper)
            lower = airframe.grid.around_of(panel.lower)
        except LookupError:
            # Already reported as a member off the grid. Nothing to compare to.
            continue
        for cutout in build.cutouts:
            inside_along = forward <= cutout.centre.along <= aft
            inside_around = upper <= cutout.centre.around <= lower
            if not (inside_along and inside_around):
                found.append(
                    Complaint(
                        Severity.ADVISORY,
                        str(panel.part),
                        f"{cutout.name} is centred outside the panel it is listed on",
                    )
                )
    return found


def _doublers_clear_of_each_other(airframe: Airframe) -> list[Complaint]:
    found: list[Complaint] = []
    for build in airframe.panels:
        fitted = build.doublers
        for index, first in enumerate(fitted):
            for second in fitted[index + 1 :]:
                if first.footprint.overlaps(second.footprint):
                    found.append(
                        Complaint(
                            Severity.ADVISORY,
                            str(build.panel.part),
                            f"{first.part} and {second.part} share skin",
                        )
                    )
    return found


def audit_airframe(airframe: Airframe) -> tuple[Complaint, ...]:
    """Walk a barrel definition and report everything that does not add up."""
    found = _members_on_the_grid(airframe)
    found.extend(_joints_on_their_panel(airframe))
    found.extend(_cutouts_inside_their_panel(airframe))
    found.extend(_doublers_clear_of_each_other(airframe))

    covered = _coverage(airframe)
    for bay, count in sorted(covered.items(), key=str):
        if count > 1:
            found.append(Complaint(Severity.BLOCKING, str(bay), f"{count} panels claim this bay"))
    for side in (Side.LEFT, Side.RIGHT):
        for bay in grid_bays(airframe.grid, side):
            if bay not in covered:
                found.append(Complaint(Severity.ADVISORY, str(bay), "no panel covers this bay"))
    return sorted_complaints(found)


def audit_wing_box(box: WingBox) -> tuple[Complaint, ...]:
    """The same walk for a wing definition, on ribs instead of bays."""
    found: list[Complaint] = []
    for build in box.panels:
        panel = build.panel
        grid = box.grid_for(panel.side)
        for rib in (panel.inboard, panel.outboard):
            try:
                grid.station_of(rib)
            except LookupError:
                found.append(
                    Complaint(Severity.BLOCKING, str(panel.part), f"{rib} is not on the wing grid")
                )
    claimed: Counter[tuple[str, str]] = Counter()
    for build in box.panels:
        panel = build.panel
        for bay in panel.bays():
            claimed[(str(bay.inboard), str(panel.surface))] += 1
    for (rib_name, surface), count in sorted(claimed.items()):
        if count > 1:
            found.append(
                Complaint(
                    Severity.BLOCKING,
                    f"{rib_name} {surface}",
                    f"{count} panels claim this rib bay",
                )
            )
    return sorted_complaints(found)
