from collections.abc import AsyncIterator
from typing import Annotated

from fastapi import Depends, HTTPException, Request, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth.dependencies import get_current_session
from app.auth.sessions import SessionPrincipal
from app.db.models import TenantMembership
from app.db.session import set_tenant_context


async def get_current_tenant_database_session(
    request: Request,
    principal: Annotated[SessionPrincipal, Depends(get_current_session)],
) -> AsyncIterator[AsyncSession]:
    factory = request.app.state.database_session_factory
    if factory is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Database is not configured.",
        )

    async with factory() as db:
        async with db.begin():
            # The session tenant is only a candidate scope. RLS is activated
            # before membership lookup, and no tenant data is accessed until
            # the current user is confirmed as an active member.
            await set_tenant_context(db, principal.tenant_id)
            membership_id = await db.scalar(
                select(TenantMembership.id).where(
                    TenantMembership.tenant_id == principal.tenant_id,
                    TenantMembership.user_id == principal.user_id,
                )
            )
            if membership_id is None:
                raise HTTPException(
                    status_code=status.HTTP_403_FORBIDDEN,
                    detail="Active workspace membership is required.",
                )
            yield db
