import pytest

from doublerbay.grid.ata import SKIN_CHAPTERS, Chapter, Reference, is_skin_chapter


def test_a_chapter_prints_as_two_digits():
    assert str(Chapter.FUSELAGE) == "53"
    assert Chapter.FUSELAGE.number == 53
    assert Chapter.STANDARD_PRACTICES.title == "standard practices"


def test_a_reference_prints_padded():
    assert str(Reference(Chapter.FUSELAGE, 10, 3)) == "53-10-03"
    assert str(Reference(Chapter.WINGS, 5)) == "57-05"


@pytest.mark.parametrize(
    ("text", "expected"),
    [
        ("53-10-03", Reference(Chapter.FUSELAGE, 10, 3)),
        ("53-10", Reference(Chapter.FUSELAGE, 10)),
        (" 57-20-01 ", Reference(Chapter.WINGS, 20, 1)),
        ("51-00", Reference(Chapter.STANDARD_PRACTICES, 0)),
    ],
)
def test_the_parser_reads_the_printed_form(text, expected):
    assert Reference.parse(text) == expected


@pytest.mark.parametrize("text", ["", "53", "5-10", "53-1", "53-10-3", "53/10/03", "53-10-03-01"])
def test_the_parser_refuses_a_malformed_reference(text):
    with pytest.raises(ValueError, match="not a manual reference"):
        Reference.parse(text)


def test_a_chapter_off_the_shelf_is_refused():
    with pytest.raises(ValueError, match="not one this desk reads"):
        Reference.parse("34-10-01")


def test_a_section_out_of_range_is_refused():
    with pytest.raises(ValueError, match="a section is two digits"):
        Reference(Chapter.FUSELAGE, 100)
    with pytest.raises(ValueError, match="a subject is two digits"):
        Reference(Chapter.FUSELAGE, 10, 100)


def test_a_reference_with_no_subject_names_a_whole_section():
    assert Reference(Chapter.FUSELAGE, 10).is_section
    assert not Reference(Chapter.FUSELAGE, 10, 3).is_section


def test_a_section_covers_every_subject_inside_it():
    section = Reference(Chapter.FUSELAGE, 10)
    assert section.covers(Reference(Chapter.FUSELAGE, 10, 3))
    assert section.covers(Reference(Chapter.FUSELAGE, 10, 99))
    assert section.covers(section)


def test_a_subject_covers_only_itself():
    subject = Reference(Chapter.FUSELAGE, 10, 3)
    assert subject.covers(subject)
    assert not subject.covers(Reference(Chapter.FUSELAGE, 10, 4))
    assert not subject.covers(Reference(Chapter.FUSELAGE, 10))


def test_nothing_covers_across_a_chapter_or_a_section():
    assert not Reference(Chapter.FUSELAGE, 10).covers(Reference(Chapter.WINGS, 10, 3))
    assert not Reference(Chapter.FUSELAGE, 10).covers(Reference(Chapter.FUSELAGE, 20, 3))


def test_dropping_the_subject_gives_the_section_it_is_filed_under():
    assert Reference(Chapter.FUSELAGE, 10, 3).section_only() == Reference(Chapter.FUSELAGE, 10)


def test_references_sort_with_the_section_ahead_of_its_subjects():
    ordered = sorted(
        [
            Reference(Chapter.WINGS, 10, 1),
            Reference(Chapter.FUSELAGE, 10, 3),
            Reference(Chapter.FUSELAGE, 10),
            Reference(Chapter.FUSELAGE, 5, 1),
        ]
    )
    assert [str(reference) for reference in ordered] == [
        "53-05-01",
        "53-10",
        "53-10-03",
        "57-10-01",
    ]


def test_references_hash_by_value():
    assert len({Reference.parse("53-10-03"), Reference(Chapter.FUSELAGE, 10, 3)}) == 1


def test_the_desk_owns_skin_and_not_hardware():
    assert is_skin_chapter(Chapter.FUSELAGE)
    assert is_skin_chapter(Chapter.WINGS)
    assert not is_skin_chapter(Chapter.DOORS)
    assert not is_skin_chapter(Chapter.STANDARD_PRACTICES)
    assert Chapter.WINDOWS not in SKIN_CHAPTERS
