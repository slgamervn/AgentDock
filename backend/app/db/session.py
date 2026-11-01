from uuid import UUID

from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncEngine, AsyncSession, async_sessionmaker, create_async_engine


def create_database_engine(database_url: str) -> AsyncEngine:
    """Create the async PostgreSQL engine used by API/worker processes."""
    if not database_url.startswith("postgresql+asyncpg://"):
        raise ValueError("AgentDock requires a postgresql+asyncpg database URL.")
    return create_async_engine(database_url, pool_pre_ping=True)


def create_session_factory(engine: AsyncEngine) -> async_sessionmaker[AsyncSession]:
    """Create a session factory; each concurrent request/task gets its own session."""
    return async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)


async def set_tenant_context(session: AsyncSession, tenant_id: UUID) -> None:
    """Set tenant scope for this transaction only (PostgreSQL SET LOCAL semantics)."""
    if not session.in_transaction():
        raise RuntimeError("Tenant context must be set inside an active transaction.")

    await session.execute(
        text("SELECT set_config('app.current_tenant_id', :tenant_id, true)"),
        {"tenant_id": str(tenant_id)},
    )
