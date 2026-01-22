"""carry the conditions an approval came back with

Revision ID: 0002
Revises: 0001
Created: 2026-01-26 09:12:04.331902

An approval is almost never unconditional. It comes back as a category and a
sheet number with two or three lines under it: inspect at every C check, do not
extend past the next heavy, seal both faces. Storing the category and dropping
the conditions is how a fleet ends up flying a repair to a condition nobody
recorded.

They go in one column, newline separated, rather than a child table. There are
never more than a handful, they are free text, nothing queries them, and a
join for something that is only ever read whole is a join nobody wants.
"""

from __future__ import annotations

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0002"
down_revision: str | None = "0001"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    with op.batch_alter_table("engineering_order", schema=None) as batch_op:
        batch_op.add_column(sa.Column("conditions", sa.String(length=1024), nullable=True))


def downgrade() -> None:
    with op.batch_alter_table("engineering_order", schema=None) as batch_op:
        batch_op.drop_column("conditions")
