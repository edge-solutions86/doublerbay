"""Chapter, section and subject, which is how every manual on the shelf is filed.

An allowable damage limit does not live anywhere on the aircraft. It lives at
53-10-03 in the structural repair manual, and the only way to get from a piece
of damaged skin to that number is to know which chapter the structure belongs
to. Fuselage skin is 53, wing skin is 57, the stabilisers are 55, and the
standard practices every repair leans on are 51.

The reference is three fields and it sorts and prints as one, because a page
number that reads 53-10-3 in one place and 53-10-03 in another is how a limit
gets looked up in the wrong table.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from enum import Enum
from functools import total_ordering

from doublerbay.errors import BadReading

_REFERENCE = re.compile(r"^(\d{2})-(\d{2})(?:-(\d{2}))?$")


class Chapter(Enum):
    """The chapters this desk reads. Anything else is somebody else's manual."""

    STANDARD_PRACTICES = 51
    DOORS = 52
    FUSELAGE = 53
    NACELLES = 54
    STABILISERS = 55
    WINDOWS = 56
    WINGS = 57

    @property
    def number(self) -> int:
        return self.value

    @property
    def title(self) -> str:
        return self.name.replace("_", " ").lower()

    def __str__(self) -> str:
        return f"{self.value:02d}"


# The chapters that carry skin the desk can be asked to disposition. Doors and
# windows have skin too, but their damage goes to the systems desk with the
# hardware it is attached to.
SKIN_CHAPTERS: frozenset[Chapter] = frozenset(
    {Chapter.FUSELAGE, Chapter.NACELLES, Chapter.STABILISERS, Chapter.WINGS}
)


@total_ordering
@dataclass(frozen=True, slots=True)
class Reference:
    """A place in the manual, as chapter, section and optionally subject."""

    chapter: Chapter
    section: int
    subject: int | None = None

    def __post_init__(self) -> None:
        if not 0 <= self.section <= 99:
            raise BadReading(f"a section is two digits, got {self.section}")
        if self.subject is not None and not 0 <= self.subject <= 99:
            raise BadReading(f"a subject is two digits, got {self.subject}")

    @classmethod
    def parse(cls, text: str) -> Reference:
        match = _REFERENCE.match(text.strip())
        if match is None:
            raise BadReading(f"{text!r} is not a manual reference")
        chapter_number = int(match.group(1))
        try:
            chapter = Chapter(chapter_number)
        except ValueError:
            raise BadReading(f"chapter {chapter_number} is not one this desk reads") from None
        subject = match.group(3)
        return cls(chapter, int(match.group(2)), int(subject) if subject is not None else None)

    @property
    def is_section(self) -> bool:
        """A reference with no subject names a whole section."""
        return self.subject is None

    def section_only(self) -> Reference:
        return Reference(self.chapter, self.section)

    def covers(self, other: Reference) -> bool:
        """Whether this reference is at or above another in the filing.

        A section covers every subject inside it, which is what lets a limit
        filed at 53-10 answer for damage looked up at 53-10-03.
        """
        if self.chapter is not other.chapter or self.section != other.section:
            return False
        return self.subject is None or self.subject == other.subject

    def _key(self) -> tuple[int, int, int]:
        return (self.chapter.number, self.section, -1 if self.subject is None else self.subject)

    def __lt__(self, other: Reference) -> bool:
        if not isinstance(other, Reference):
            return NotImplemented
        return self._key() < other._key()

    def __str__(self) -> str:
        head = f"{self.chapter}-{self.section:02d}"
        return head if self.subject is None else f"{head}-{self.subject:02d}"


def is_skin_chapter(chapter: Chapter) -> bool:
    return chapter in SKIN_CHAPTERS
