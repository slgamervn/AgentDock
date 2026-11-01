import asyncio
from uuid import uuid4

import pytest
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.session import set_tenant_context


def test_tenant_context_requires_an_active_transaction() -> None:
    async def check_guard() -> None:
        session = AsyncSession()
        try:
            with pytest.raises(RuntimeError, match="active transaction"):
                await set_tenant_context(session, uuid4())
        finally:
            await session.close()

    asyncio.run(check_guard())
