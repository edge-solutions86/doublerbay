import sample
from doublerbay.damage.corrosion import Corrosion
from doublerbay.damage.crack import Crack
from doublerbay.damage.dent import Dent
from doublerbay.damage.geometry import Geometry, Measured, has_a_depth
from doublerbay.damage.opening import Buckle, Disbond, Opening
from doublerbay.damage.scoring import Scoring
from doublerbay.damage.strike import LightningBurn
from doublerbay.grid.surface import Offset
from doublerbay.measure.angle import Angle
from doublerbay.measure.length import Length


def inch(text):
    return sample.inch(text)


def deg(value):
    return Angle.of_degrees(value)


CENTRE = Offset(Length.of_inches("100.000"), Length.of_inches("50.000"))

WITH_A_DEPTH = (
    (Dent(inch("0.030"), inch("3.000"), inch("2.000")), inch("0.030")),
    (Scoring(inch("0.010"), inch("2.000"), inch("0.060"), deg(0)), inch("0.010")),
    (Corrosion(inch("4.000"), inch("3.000"), inch("0.006")), inch("0.006")),
    (LightningBurn(inch("2.000"), inch("2.000"), 4, inch("0.008")), inch("0.008")),
)

WITHOUT_A_DEPTH = (
    Crack(inch("0.750"), deg(0)),
    Opening(inch("0.500"), inch("0.500")),
    Buckle(inch("6.000"), inch("0.120"), deg(90)),
    Disbond(inch("3.000"), inch("2.000")),
)

EVERY_KIND: tuple[Geometry, ...] = tuple(one for one, _ in WITH_A_DEPTH) + WITHOUT_A_DEPTH


def test_every_measurement_set_satisfies_the_protocol():
    for geometry in EVERY_KIND:
        assert isinstance(geometry, Measured)


def test_every_measurement_set_covers_some_skin():
    for geometry in EVERY_KIND:
        covered = geometry.footprint(CENTRE)
        assert covered.along.mils > 0
        assert covered.around.mils > 0
        assert covered.contains(CENTRE)


def test_the_kinds_with_a_depth_answer_with_the_one_the_tables_read():
    for geometry, expected in WITH_A_DEPTH:
        assert geometry.limit_depth() == expected
        assert has_a_depth(geometry)


def test_the_kinds_without_a_depth_answer_with_nothing():
    for geometry in WITHOUT_A_DEPTH:
        assert geometry.limit_depth() is None
        assert not has_a_depth(geometry)


def test_corrosion_answers_with_its_worst_pit_and_not_an_average():
    pitted = Corrosion(inch("4.000"), inch("3.000"), inch("0.012"), pitted=True)
    assert pitted.limit_depth() == inch("0.012")


def test_a_lightning_burn_answers_with_its_deepest_pit():
    burn = LightningBurn(inch("2.000"), inch("2.000"), 9, inch("0.014"))
    assert burn.limit_depth() == inch("0.014")


def test_the_union_covers_every_kind_the_desk_measures():
    for geometry in EVERY_KIND:
        assert isinstance(
            geometry,
            Dent | Scoring | Crack | Corrosion | LightningBurn | Opening | Buckle | Disbond,
        )
