import sample
from doublerbay.grid.lattice import Frame, Stringer
from doublerbay.grid.surface import Offset
from doublerbay.grid.zone import Side
from doublerbay.measure.length import Length
from doublerbay.structure.airframe import Airframe
from doublerbay.structure.audit import (
    Complaint,
    Severity,
    audit_airframe,
    audit_wing_box,
    blocking,
    grid_bays,
)
from doublerbay.structure.build import PanelBuild
from doublerbay.structure.cutout import Cutout, CutoutKind
from doublerbay.structure.joint import Joint, JointKind
from doublerbay.v320.fuselage import barrel
from doublerbay.v320.wing import wing_box


def inch(text):
    return Length.of_inches(text)


def test_the_grid_bays_are_every_square_the_ladders_bound():
    built = sample.grid()
    left = list(grid_bays(built, Side.LEFT))
    assert len(left) == (len(built.frames) - 1) * (len(built.stringers) - 1)
    assert all(bay.is_single for bay in left)
    assert all(bay.side is Side.LEFT for bay in left)


def test_the_transcribed_barrel_has_nothing_to_complain_about():
    assert audit_airframe(barrel()) == ()


def test_the_transcribed_wing_has_nothing_to_complain_about():
    assert audit_wing_box(wing_box()) == ()


def test_a_bay_two_panels_claim_is_blocking():
    doubled = Airframe(
        "test",
        sample.grid(),
        (sample.forward_upper(), sample.aft_upper(), sample.forward_lower(), sample.overlapping()),
    )
    complaints = audit_airframe(doubled)
    claimed = [c for c in complaints if "panels claim this bay" in c.says]
    assert claimed
    assert all(c.severity is Severity.BLOCKING for c in claimed)


def test_a_bay_no_panel_covers_is_advisory():
    complaints = audit_airframe(sample.airframe())
    uncovered = [c for c in complaints if c.says == "no panel covers this bay"]
    assert uncovered
    assert all(c.severity is Severity.ADVISORY for c in uncovered)


def test_a_joint_on_a_member_off_its_panel_is_blocking():
    stray = Joint(JointKind.LAP, Stringer(7, Side.LEFT), sample.lap().rows)
    build = PanelBuild(
        panel=sample.forward_upper().panel,
        thickness=sample.milled(),
        joints=(stray,),
    )
    complaints = audit_airframe(Airframe("test", sample.grid(), (build,)))
    named = [c for c in complaints if "which is not on this panel" in c.says]
    assert len(named) == 1
    assert named[0].severity is Severity.BLOCKING


def test_a_cutout_centred_off_its_panel_is_advisory():
    stray = Cutout(
        "W99L",
        CutoutKind.WINDOW,
        Offset(inch("900.000"), inch("20.000")),
        inch("13.000"),
        inch("9.000"),
        inch("2.000"),
    )
    build = PanelBuild(
        panel=sample.forward_upper().panel,
        thickness=sample.milled(),
        cutouts=(stray,),
    )
    complaints = audit_airframe(Airframe("test", sample.grid(), (build,)))
    named = [c for c in complaints if "centred outside the panel" in c.says]
    assert len(named) == 1
    assert named[0].severity is Severity.ADVISORY


def test_two_doublers_sharing_skin_are_advisory():
    build = PanelBuild(
        panel=sample.forward_lower().panel,
        thickness=sample.forward_lower().thickness,
        doublers=(sample.old_patch(), sample.second_patch()),
    )
    complaints = audit_airframe(Airframe("test", sample.grid(), (build,)))
    named = [c for c in complaints if "share skin" in c.says]
    assert len(named) == 1
    assert named[0].severity is Severity.ADVISORY


def test_a_panel_on_a_member_off_the_grid_is_blocking():
    short = sample.grid()
    trimmed = type(short)(short.frames[:3], short.stringers)
    complaints = audit_airframe(Airframe("test", trimmed, (sample.forward_upper(),)))
    named = [c for c in complaints if "is not on the grid" in c.says]
    assert named
    assert all(c.severity is Severity.BLOCKING for c in named)


def test_blocking_filters_out_the_advice():
    complaints = audit_airframe(sample.airframe())
    assert all(c.severity is Severity.BLOCKING for c in blocking(complaints))
    assert len(blocking(complaints)) < len(complaints)


def test_complaints_come_back_sorted_with_the_blocking_ones_first():
    complaints = audit_airframe(sample.airframe())
    severities = [c.severity for c in complaints]
    assert severities == sorted(severities, key=lambda severity: severity.value)


def test_a_complaint_reads_back_what_it_is_about():
    complaint = Complaint(Severity.BLOCKING, "5310-142L", "FR90 is not on the grid")
    assert str(complaint) == "[blocking] 5310-142L: FR90 is not on the grid"
    assert Severity.BLOCKING.stops_work
    assert not Severity.ADVISORY.stops_work


def test_a_frame_off_the_grid_is_still_a_frame():
    assert Frame(90).number == 90
