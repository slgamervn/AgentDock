# AgentDock Backend

FastAPI control-plane bootstrap with the first PostgreSQL persistence boundary.

## Implemented

- Versioned API prefix: `/api/v1`.
- `GET /api/v1/health/live` is a process liveness check only.
- Runtime configuration uses the `AGENTDOCK_` prefix; database URLs use `SecretStr` in application settings.
- SQLAlchemy async engine/session helpers and Alembic migrations.
- Initial records: tenants, users, tenant memberships, tasks, ordered task events and idempotency records.
- PostgreSQL row-level security (RLS) is enabled and forced on every tenant-owned table.
- Docker runs as UID/GID 10001, not root.

This is not a remote task API yet. Authentication, session issuance/revocation, membership authorization endpoints, task CRUD, workers, orchestration and Agent execution are not implemented. The API process does not connect to PostgreSQL at startup, and `/health/live` does not assert database readiness.

## Database roles and tenant isolation

Use different database principals for migrations and normal API/worker traffic:

- The migration role owns the schema objects and is used only by Alembic deployments.
- The runtime role must be `NOSUPERUSER NOBYPASSRLS`, must not own tenant tables, and receives only the required table DML grants.
- Never run the API as the PostgreSQL superuser or migration owner.

The migration applies `ENABLE ROW LEVEL SECURITY` and `FORCE ROW LEVEL SECURITY` to `tenant_memberships`, `tasks`, `task_events` and `idempotency_records`. Policies fail closed when the transaction has no `app.current_tenant_id`.

Every tenant query must run in an explicit transaction and set its scope with `set_tenant_context(session, tenant_id)`. Resolve and authorize the user's membership on the server before setting this value; never trust a tenant ID supplied by the client. RLS is a database isolation boundary, not a replacement for authentication or authorization. PostgreSQL superusers and roles with `BYPASSRLS` can bypass policies.

Example role setup (execute as a database administrator after creating the database):

```sql
CREATE ROLE agentdock_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;
CREATE ROLE agentdock_runtime LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;
ALTER SCHEMA public OWNER TO agentdock_migrator;
GRANT CONNECT ON DATABASE agentdock TO agentdock_migrator, agentdock_runtime;
GRANT USAGE ON SCHEMA public TO agentdock_runtime;
```

Set `AGENTDOCK_MIGRATION_DATABASE_URL` to the migration role URL and run migrations:

```bash
python -m alembic upgrade head
```

Then, as the migration role or database administrator, grant only the required runtime access:

```sql
GRANT SELECT ON TABLE tenants, users TO agentdock_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE
  ON TABLE tenant_memberships, tasks, task_events, idempotency_records
  TO agentdock_runtime;
```

Do not grant the runtime role access to `alembic_version`, schema ownership, role creation or `BYPASSRLS`. Future migrations must explicitly grant only the access needed for each new table. Use `AGENTDOCK_DATABASE_URL` for the restricted runtime role. Never copy production credentials into the repository or CI logs.

## Local development

Use Python 3.14 and a virtual environment. From this directory:

1. Create and activate a virtual environment.
2. Install dependencies with `pip install -r requirements-dev.txt`.
3. Copy `.env.example` to `.env` and configure the two database URLs with your locally provisioned roles.
4. Apply migrations with `python -m alembic upgrade head`.
5. Start the API with `uvicorn app.main:app --reload`.

API liveness: http://127.0.0.1:8000/api/v1/health/live. Interactive docs are available at http://127.0.0.1:8000/docs outside production mode.

## Tests

Run `python -m compileall -q app migrations tests`, then `python -m pytest -q`. PostgreSQL isolation tests require `AGENTDOCK_TEST_ADMIN_DATABASE_URL` and `AGENTDOCK_TEST_DATABASE_URL`; they verify a real non-owner, non-superuser runtime role, forced RLS, transaction-local tenant scope, cross-tenant read isolation and rejected cross-tenant writes. Those tests are always enabled in GitHub Actions.

Copy `.env.example` for local configuration. Never commit real `.env` files, credentials or signing material.
