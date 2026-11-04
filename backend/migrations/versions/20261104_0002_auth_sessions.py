"""Add external identity mapping and revocable AgentDock sessions.

Revision ID: 20261104_0002
Revises: 20261101_0001
Create Date: 2026-11-04
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision: str = "20261104_0002"
down_revision: Union[str, None] = "20261101_0001"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # An external identity is authoritative by (issuer, subject), not email.
    # Email is optional because OIDC access tokens are not required to carry it.
    op.alter_column(
        "users",
        "email",
        existing_type=sa.String(length=320),
        nullable=True,
    )

    op.create_table(
        "auth_identities",
        sa.Column("id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("issuer", sa.String(length=512), nullable=False),
        sa.Column("subject", sa.String(length=255), nullable=False),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("default_tenant_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.Column(
            "last_authenticated_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["default_tenant_id"], ["tenants.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("issuer", "subject", name="uq_auth_identities_issuer_subject"),
    )
    op.create_index("ix_auth_identities_user_id", "auth_identities", ["user_id"])

    op.create_table(
        "user_sessions",
        sa.Column("id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("tenant_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("revoked_at", sa.DateTime(timezone=True), nullable=True),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["tenant_id"], ["tenants.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(
        "ix_user_sessions_user_created",
        "user_sessions",
        ["user_id", "created_at"],
    )
    op.create_index("ix_user_sessions_expires_at", "user_sessions", ["expires_at"])

    op.create_table(
        "session_tokens",
        sa.Column("id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("session_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("token_hash", sa.String(length=64), nullable=False),
        sa.Column("token_type", sa.String(length=8), nullable=False),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("used_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("revoked_at", sa.DateTime(timezone=True), nullable=True),
        sa.CheckConstraint(
            "token_type IN ('ACCESS', 'REFRESH')",
            name="ck_session_tokens_type",
        ),
        sa.ForeignKeyConstraint(["session_id"], ["user_sessions.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("token_hash", name="uq_session_tokens_hash"),
    )
    op.create_index(
        "ix_session_tokens_session_type",
        "session_tokens",
        ["session_id", "token_type"],
    )
    op.create_index("ix_session_tokens_expires_at", "session_tokens", ["expires_at"])


def downgrade() -> None:
    op.drop_index("ix_session_tokens_expires_at", table_name="session_tokens")
    op.drop_index("ix_session_tokens_session_type", table_name="session_tokens")
    op.drop_table("session_tokens")
    op.drop_index("ix_user_sessions_expires_at", table_name="user_sessions")
    op.drop_index("ix_user_sessions_user_created", table_name="user_sessions")
    op.drop_table("user_sessions")
    op.drop_index("ix_auth_identities_user_id", table_name="auth_identities")
    op.drop_table("auth_identities")

    # Do not silently invent email addresses to reverse account provisioning.
    op.execute(
        """
        DO $$
        BEGIN
            IF EXISTS (SELECT 1 FROM users WHERE email IS NULL) THEN
                RAISE EXCEPTION
                    'Cannot downgrade auth session migration: users without email exist';
            END IF;
        END
        $$;
        """
    )
    op.alter_column(
        "users",
        "email",
        existing_type=sa.String(length=320),
        nullable=False,
    )
