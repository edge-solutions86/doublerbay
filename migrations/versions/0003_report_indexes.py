"""indexes for the queries the reports actually run

Revision ID: 0003
Revises: 0002
Created: 2026-04-16 08:41:52.104773

None of these change an answer. They change how long the answer takes, which
starts to matter at about eight hundred cards and stops being tolerable at
about four thousand, and a fleet reaches four thousand cards in a decade.

Each one is a composite index over the columns one query filters on, in the
order it filters on them. A single column index on the serial alone reads
every card the aeroplane has ever carried and discards most of them, which is
the shape that looks fine on a test database and is not fine on a real one.
"""

from __future__ import annotations

from collections.abc import Sequence

from alembic import op

revision: str = "0003"
down_revision: str | None = "0002"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None

INDEXES = (
    ("ix_repair_fitted", "repair", ["serial", "part", "removed"]),
    ("ix_deferral_live", "deferral", ["lifted", "granted"]),
    ("ix_corrosion_here_before", "corrosion_entry", ["serial", "part", "found_on"]),
)


def upgrade() -> None:
    for name, table, columns in INDEXES:
        op.create_index(name, table, columns, unique=False)


def downgrade() -> None:
    for name, table, _ in reversed(INDEXES):
        op.drop_index(name, table_name=table)
