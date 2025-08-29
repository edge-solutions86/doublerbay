"""Where a damage card is in its life, and which moves are legal from there.

A card is raised, assessed, and then it goes one of four ways: it is deferred
with a limit on it, it goes into work, it is held while somebody asks the
manufacturer, or it is closed because there was nothing to do. Every one of
those can come back to assessed, because a re measurement changes the answer.

The moves are written down rather than left to whoever is calling. A card going
straight from raised to closed has skipped the assessment, and the only way
anybody finds that out later is by reading the history and noticing what is not
in it. Refusing the move is cheaper.

Superseded is the one terminal state and it is deliberately separate from
closed. Closed means the damage was dealt with. Superseded means this card was
a duplicate and another one is carrying it, which is a different thing to have
in a fleet report.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from enum import Enum

from doublerbay.errors import BadReading, OutOfSequence
from doublerbay.ids import FindingId


class DispositionState(Enum):
    """Where the card is."""

    RAISED = "raised"
    ASSESSED = "assessed"
    DEFERRED = "deferred"
    HELD = "held for engineering"
    IN_WORK = "in work"
    CLOSED = "closed"
    SUPERSEDED = "superseded"

    @property
    def is_terminal(self) -> bool:
        return self is DispositionState.SUPERSEDED

    @property
    def is_open(self) -> bool:
        """Anything the desk still owns."""
        return self not in (DispositionState.CLOSED, DispositionState.SUPERSEDED)

    @property
    def is_flying_with_it(self) -> bool:
        """States where the aeroplane is in service carrying the damage."""
        return self in (
            DispositionState.RAISED,
            DispositionState.ASSESSED,
            DispositionState.DEFERRED,
            DispositionState.HELD,
        )

    def can_move_to(self, other: DispositionState) -> bool:
        return other in MOVES[self]

    def __str__(self) -> str:
        return self.value


MOVES: dict[DispositionState, frozenset[DispositionState]] = {
    DispositionState.RAISED: frozenset({DispositionState.ASSESSED, DispositionState.SUPERSEDED}),
    DispositionState.ASSESSED: frozenset(
        {
            DispositionState.DEFERRED,
            DispositionState.HELD,
            DispositionState.IN_WORK,
            DispositionState.CLOSED,
            DispositionState.SUPERSEDED,
        }
    ),
    DispositionState.DEFERRED: frozenset(
        {
            DispositionState.ASSESSED,
            DispositionState.HELD,
            DispositionState.IN_WORK,
            DispositionState.SUPERSEDED,
        }
    ),
    DispositionState.HELD: frozenset(
        {DispositionState.ASSESSED, DispositionState.IN_WORK, DispositionState.SUPERSEDED}
    ),
    DispositionState.IN_WORK: frozenset({DispositionState.CLOSED, DispositionState.HELD}),
    DispositionState.CLOSED: frozenset({DispositionState.ASSESSED}),
    DispositionState.SUPERSEDED: frozenset(),
}


@dataclass(frozen=True, slots=True)
class Transition:
    """One move, who made it and why."""

    was: DispositionState
    now: DispositionState
    on: date
    by: str
    note: str = ""

    def __post_init__(self) -> None:
        if not self.by.strip():
            raise BadReading("a move is made by somebody")

    def __str__(self) -> str:
        tail = f" ({self.note})" if self.note else ""
        return f"{self.on.isoformat()} {self.was} to {self.now} by {self.by}{tail}"


@dataclass(frozen=True, slots=True)
class DispositionRecord:
    """One card's state and everything it has been through."""

    reference: FindingId
    state: DispositionState
    history: tuple[Transition, ...] = ()

    @classmethod
    def raised(cls, reference: FindingId) -> DispositionRecord:
        return cls(reference, DispositionState.RAISED)

    @property
    def is_open(self) -> bool:
        return self.state.is_open

    @property
    def moves_made(self) -> int:
        return len(self.history)

    def moved_to(
        self, state: DispositionState, on: date, by: str, note: str = ""
    ) -> DispositionRecord:
        """The card after a move, refusing anything the lifecycle does not allow."""
        if not self.state.can_move_to(state):
            raise OutOfSequence(f"{self.reference} cannot go from {self.state} to {state}")
        if self.history and on < self.history[-1].on:
            raise BadReading(f"{self.reference}: a move cannot be dated before the one before it")
        return DispositionRecord(
            self.reference,
            state,
            (*self.history, Transition(self.state, state, on, by, note)),
        )

    def has_been(self, state: DispositionState) -> bool:
        """Whether the card has ever sat in a state, the one it started in included.

        The history records where each move went, so the state a card was
        raised into only ever appears as the state a move came from.
        """
        if self.state is state:
            return True
        return any(move.now is state or move.was is state for move in self.history)

    def entered(self, state: DispositionState) -> date | None:
        """When the card last went into a state, if it ever did."""
        for move in reversed(self.history):
            if move.now is state:
                return move.on
        return None

    def __str__(self) -> str:
        return f"{self.reference} {self.state}, {self.moves_made} moves"
