# AgentDock Backend

Minimal FastAPI bootstrap for the AgentDock control plane.

## Current scope

- Versioned API prefix: /api/v1.
- GET /api/v1/health/live confirms the API process responds.
- Environment-based settings use the AGENTDOCK_ prefix.
- Interactive API documentation is disabled in production mode.
- Docker runs the service as UID/GID 10001, not root.

This is not a task API yet. It has no authentication, PostgreSQL connection, worker, orchestration runtime, or Agent execution. The liveness endpoint must not be used as a readiness signal for dependencies that are not configured.

## Local development

Use Python 3.14 and a virtual environment. From this directory:

1. Create a virtual environment with python -m venv .venv.
2. Activate it.
3. Install dependencies with pip install -r requirements-dev.txt.
4. Start the API with uvicorn app.main:app --reload.

API liveness: http://127.0.0.1:8000/api/v1/health/live.

Interactive docs are available at http://127.0.0.1:8000/docs outside production mode.

## Tests

Run python -m compileall -q app tests, then python -m pytest -q.

Copy .env.example to .env for local configuration. Never commit real .env files or credentials.
