# AgentDock Backend

FastAPI control-plane bootstrap with the first PostgreSQL persistence boundary.

## Implemented

- Versioned API prefix: `/api/v1`.
- `GET /api/v1/health/live` is a process liveness check and never requires a database connection.
- `GET /api/v1/health/ready` executes `SELECT 1` against the configured runtime database and returns 503 when the database is not configured or unavailable. It is a database readiness probe, not a migration-version or worker-readiness check.
- The API creates the async engine during application lifespan and disposes it during shutdown. Connection establishment and pool acquisition are bounded.
- Runtime configuration uses the `AGENTDOCK_` prefix; database URLs use `SecretStr` in application settings.
- SQLAlchemy async engine/session helpers and Alembic migrations.
- Initial records: tenants, users, tenant memberships, tasks, ordered task events and idempotency records.
- Verified external identities are mapped by the exact `(issuer, subject)` pair. First sign-in provisions a minimal account and personal workspace without trusting or linking by email.
- AgentDock sessions use opaque random access/refresh tokens. Only SHA-256 token hashes are stored; refresh tokens rotate and replay revokes the full session family. Access tokens are short-lived and checked against PostgreSQL so logout revocation takes effect immediately.
- PostgreSQL row-level security (RLS) is enabled and forced on every tenant-owned table.
- Docker runs as UID/GID 10001, not root.

The API now has an optional OIDC JWT access-token verification boundary. When configured, `GET /api/v1/auth/identity` checks the RFC 9068 access-token type (`at+jwt` or `application/at+jwt`), signature against the configured HTTPS JWKS endpoint, a fixed allowlist of asymmetric algorithms, issuer, API audience and the RFC 9068 required claims (`iss`, `sub`, `aud`, `exp`, `iat`, `client_id`, `jti`); it returns only issuer, subject and expiry. Missing or non-access-token `typ` values are rejected; do not send OIDC ID tokens. A provider-specific non-RFC 9068 token format requires a separately reviewed adapter. JWKS retrieval runs off the event loop, is cached, and has a bounded timeout. Invalid tokens return 401; temporary JWKS connection failures return a generic 503.

This is **not a complete sign-in or account system**. Android's Authorization Code + PKCE flow, provider-specific token contract, identity-to-user mapping, first-user/workspace provisioning, internal session issuance/rotation/revocation, tenant membership authorization, task CRUD, workers and Agent execution are still not implemented. Do not protect production task routes with this verifier alone until the provider's access-token contract and server-side user/session authorization are integrated and tested. The API constructs an async engine during application startup but does not open a database connection until the readiness check or a database operation. `/health/live` does not assert database readiness.

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
GRANT SELECT, INSERT, UPDATE ON TABLE tenants, users TO agentdock_runtime;
GRANT SELECT, INSERT, UPDATE
  ON TABLE auth_identities, user_sessions, session_tokens
  TO agentdock_runtime;
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

API liveness: http://127.0.0.1:8000/api/v1/health/live. Database readiness: http://127.0.0.1:8000/api/v1/health/ready. The readiness route requires a configured and reachable runtime PostgreSQL database. Interactive docs are available at http://127.0.0.1:8000/docs outside production mode.

## Tests

Run `python -m compileall -q app migrations tests`, then `python -m pytest -q`. PostgreSQL isolation tests require `AGENTDOCK_TEST_ADMIN_DATABASE_URL` and `AGENTDOCK_TEST_DATABASE_URL`; they verify a real non-owner, non-superuser runtime role, forced RLS, transaction-local tenant scope, cross-tenant read isolation and rejected cross-tenant writes. Those tests are always enabled in GitHub Actions.

Copy `.env.example` for local configuration. Never commit real `.env` files, credentials or signing material.

## Optional OIDC access-token verification

Set `AGENTDOCK_OIDC_ISSUER`, `AGENTDOCK_OIDC_AUDIENCE` and `AGENTDOCK_OIDC_JWKS_URL` together. The issuer and JWKS URLs must use HTTPS and must be provisioned by the operator, never supplied by a request. The audience identifies the AgentDock API/resource server. The default algorithm allowlist is RS256; optional algorithms must remain asymmetric and explicitly allowlisted. The configured JWKS client caches keys and uses a bounded network timeout.

The protected `GET /api/v1/auth/identity` endpoint is a verification probe and a reference for future protected routes. It does not create an AgentDock user or session. The backend maps `(issuer, subject)` to an internal account and provisions a personal workspace on first verified sign-in. It issues short-lived opaque AgentDock access tokens and rotating refresh tokens, persists only token hashes, detects refresh-token replay and supports immediate session revocation. The Android Authorization Code + PKCE flow and provider-specific setup remain separate work. This session foundation does not itself implement task CRUD or worker execution. Never send an ID token to this endpoint; configure and validate the identity provider's access-token contract for the AgentDock API audience.
