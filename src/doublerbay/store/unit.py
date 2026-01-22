"""One transaction, with every repository on it, and nothing committed by halves.

Raising a damage card writes the card and opens its disposition. If the first
goes in and the second does not, there is a finding on the register that no
report will ever show because nothing knows where it is. So the two go in
together or neither does, and the boundary for that is here rather than in
whoever remembered to call commit.

Nothing commits on its own. The unit of work commits once on the way out of the
block, and rolls the whole thing back if anything raised on the way through.
"""

from __future__ import annotations

from types import TracebackType

from sqlalchemy import Engine
from sqlalchemy.orm import Session

from doublerbay.errors import OutOfSequence
from doublerbay.store.base import session_factory
from doublerbay.store.repository import (
    AircraftRepository,
    CorrosionRepository,
    DeferralRepository,
    DispositionRepository,
    FindingRepository,
    OrderRepository,
    RepairRepository,
)


class UnitOfWork:
    """A transaction with the repositories hanging off it."""

    def __init__(self, engine: Engine) -> None:
        self._factory = session_factory(engine)
        self._session: Session | None = None

    def __enter__(self) -> UnitOfWork:
        self._session = self._factory()
        return self

    def __exit__(
        self,
        kind: type[BaseException] | None,
        value: BaseException | None,
        traceback: TracebackType | None,
    ) -> None:
        session = self._open()
        try:
            if kind is None:
                session.commit()
            else:
                session.rollback()
        finally:
            session.close()
            self._session = None

    @property
    def session(self) -> Session:
        return self._open()

    @property
    def aircraft(self) -> AircraftRepository:
        return AircraftRepository(self._open())

    @property
    def findings(self) -> FindingRepository:
        return FindingRepository(self._open())

    @property
    def dispositions(self) -> DispositionRepository:
        return DispositionRepository(self._open())

    @property
    def deferrals(self) -> DeferralRepository:
        return DeferralRepository(self._open())

    @property
    def repairs(self) -> RepairRepository:
        return RepairRepository(self._open())

    @property
    def corrosion(self) -> CorrosionRepository:
        return CorrosionRepository(self._open())

    @property
    def orders(self) -> OrderRepository:
        return OrderRepository(self._open())

    def flush(self) -> None:
        """Push the writes down so a later read in the same block sees them."""
        self._open().flush()

    def rollback(self) -> None:
        self._open().rollback()

    def _open(self) -> Session:
        if self._session is None:
            raise OutOfSequence("this unit of work is not open, use it in a with block")
        return self._session
