"""Index tenant task timeline pagination.

Revision ID: 20261105_0003
Revises: 20261104_0002
Create Date: 2026-11-05
"""
from typing import Sequence, Union

from alembic import op

revision: str = "20261105_0003"
down_revision: Union[str, None] = "20261104_0002"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_index(
        "ix_tasks_tenant_created_id",
        "tasks",
        ["tenant_id", "created_at", "id"],
    )


def downgrade() -> None:
    op.drop_index("ix_tasks_tenant_created_id", table_name="tasks")
