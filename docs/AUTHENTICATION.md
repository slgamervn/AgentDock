# AgentDock authentication and session contract

**Status:** Backend implementation in feature/auth-session-foundation; automated PostgreSQL CI is required before this contract is considered validated. No identity provider has been selected or configured.

## Security boundary

The Android app is a public OAuth client. When the client login flow is implemented, it must use Authorization Code + PKCE with S256 through the system browser or a browser-backed authentication tab. Never embed an OAuth client secret in the APK.

The current backend verifier accepts only signed RFC 9068-style JWT access tokens whose JOSE typ is at+jwt or application/at+jwt. It validates the configured HTTPS JWKS, asymmetric algorithm allowlist, issuer, AgentDock API audience, expiry, issued-at, client_id and jti; azp is checked when configured. Do not send an OIDC ID token. A provider that does not issue this access-token profile for the AgentDock API requires a separately reviewed adapter; do not weaken the verifier to make a provider fit.

Configure AGENTDOCK_OIDC_ISSUER, AGENTDOCK_OIDC_AUDIENCE and AGENTDOCK_OIDC_JWKS_URL together. The issuer and JWKS URL must be HTTPS and operator-provisioned, never request-controlled.

## Endpoints

| Endpoint | Credential | Behavior |
|---|---|---|
| GET /api/v1/auth/identity | External OIDC access token | Verification probe; returns minimal issuer/subject/expiry |
| POST /api/v1/auth/session | External OIDC access token | Maps identity, provisions a minimal account/workspace on first sign-in, issues AgentDock token pair |
| POST /api/v1/auth/session/refresh | Refresh token in request body | Rotates the refresh token and issues a new access token |
| GET /api/v1/auth/me | AgentDock access token | Returns current user, workspace and session identifiers |
| POST /api/v1/auth/session/logout | AgentDock access token | Revokes the current session and all its tokens |

The mapping key is the exact (issuer, subject) pair. Email is not used for account matching or linking; accounts provisioned through this flow may have a null email until a separately verified profile flow exists. First sign-in creates one personal workspace and an owner membership. Provisioning is serialized by a PostgreSQL transaction advisory lock and protected by a unique database constraint.

## AgentDock session tokens

- Access and refresh tokens are cryptographically random opaque bearer strings.
- Only SHA-256 hashes are persisted; plaintext tokens are returned only in the session response.
- Access token lifetime defaults to 15 minutes.
- Session lifetime defaults to a fixed 30 days.
- Refresh tokens rotate on every successful refresh; the previous access token is invalidated.
- A previously used refresh token indicates replay and revokes the entire session family.
- Logout revokes the session in PostgreSQL; subsequent access-token checks fail immediately.
- Refresh requests from one client must be serialized. Concurrent refresh calls using the same token are treated as replay and revoke the session.
- The server checks the session-token hash in PostgreSQL on each authenticated request. This enables immediate revocation but adds a database read per request and must be load-tested.

The refresh-token rotation and replay-detection design follows the public-client guidance in [RFC 9700, Section 4.14](https://www.rfc-editor.org/rfc/rfc9700.html).

## Database permissions

The migration creates auth_identities, user_sessions and session_tokens, and makes users.email nullable. The runtime role receives only SELECT/INSERT/UPDATE on identity/session tables and users/tenants, plus the existing tenant-scoped DML permissions. It does not receive schema ownership, migration-history access, superuser or BYPASSRLS privileges.

Identity/session registry tables are not tenant-owned application data; they are accessed only through the backend service. Tenant membership writes still require the transaction-local app.current_tenant_id context. Before building task routes, the backend must resolve the session workspace membership on the server, set RLS context in the same transaction, and deny requests with no active membership.

## Not yet implemented

- Choosing and configuring the actual OIDC provider and verifying its real access-token contract.
- Android browser-based Authorization Code + PKCE, redirect handling, secure local token storage and login/logout UI.
- Account profile/email verification, account linking, multiple workspaces and tenant switching.
- Per-device session listing and remote revocation.
- Expired session/token cleanup, retention schedule and rate limiting/abuse controls.
- Task routes and membership enforcement on every tenant data operation.
- Production deployment migration and secret provisioning.

Do not describe AgentDock as having end-to-end login until the Android flow has been integrated and tested against the chosen provider.