# Nova Bank demonstration

Nova is a synthetic banking demonstration, not a real bank. Never use real customer information or expose seeded credentials publicly. It includes sign-in and session controls, customer portfolio and account transactions with statement download, offers and interest registration, support inquiries and a contextual assistant. Facilitator controls provide demo fixture reset, a bounded error simulation probe and a redacted audit view. The reset restores demo users, accounts, transactions and offers, clears demo activity and invalidates customer sessions; it rejects conflicting fixture references rather than overwriting unrelated data.

## Run with Compose

Set `DB_PASSWORD` and a unique `JWT_SECRET` of at least 32 bytes in your shell. For an isolated demo only, set `DEMO_MODE=true` and `DEMO_PASSWORD=demo-only`; the startup seeder creates Miguel, Rosa and admin if absent and does not reset existing users. Then run `docker compose up --build` and open http://localhost:8080. Terminate TLS in front of deployed traffic. Configuration examples are in each tier's `.env.example`; Spring does not automatically load those files. PostgreSQL uses a named volume.

## Development and verification

Provision an empty PostgreSQL database first. Export backend settings shown in `backend/.env.example`, then run `mvn -f backend/pom.xml spring-boot:run`. Install frontend dependencies with `cd frontend && npm install`, then `npm run build`. Production Nginx proxies same-origin `/api/` to `API_UPSTREAM`; Angular development needs an equivalent reverse proxy or the Compose frontend.

### Backend tests: isolate the database

All backend tests use PostgreSQL, run migrations and seed or mutate data. **Never point them at development, shared, staging or production data.** Provision a fresh, empty, dedicated disposable database for each full run, use a dedicated restricted user and run the suites single-threaded, with no other process using that database. Do not reuse a database after a failed or interrupted run without recreating it. Tests use shared seeded fixtures; most ordinary writes are rolled back, while reset journeys restore fixtures after each test. Set `TEST_DATABASE_URL` to its JDBC URL and `TEST_DB_USERNAME` and `TEST_DB_PASSWORD` to its credentials. All Spring test contexts require explicit `TEST_DISPOSABLE=1` and `TEST_DATABASE_NAME` equal to the actual connected database name, which must end in `_test`. The shared guard checks the server-reported name before context startup; reset tests also check before destructive operations. This is an extra safeguard, not proof that a database is disposable.

For example, after independently provisioning an isolated database and credentials:

```sh
export TEST_DISPOSABLE=1 TEST_DATABASE_NAME=example_test
export TEST_DATABASE_URL=jdbc:postgresql://127.0.0.1:5432/example_test
export TEST_DB_USERNAME=example_test_user TEST_DB_PASSWORD=local-test-password
mvn -f backend/pom.xml -T 1 -Djunit.jupiter.execution.parallel.enabled=false test
```

Component tests: `cd frontend && npm test -- --browsers=ChromeHeadless`.

### Browser tests: isolate the whole environment

Playwright resets fixtures and changes simulation state. Set `E2E_DISPOSABLE=1` only after confirming the application and its database are isolated and disposable. The default launcher requires a dedicated `COMPOSE_PROJECT_NAME` beginning with `e2e-`, plus Compose settings (`DB_PASSWORD`, `JWT_SECRET`, `DEMO_MODE=true`, `DEMO_PASSWORD`); it starts Compose with its own named volume and does not reuse an existing server. Choose a free `FRONTEND_PORT`. Run `node frontend/node_modules/@playwright/test/cli.js test --config frontend/playwright.config.ts` from the repository root. Alternatively set `E2E_REUSE_SERVER=1` and an explicit `E2E_BASE_URL` only for a separately provisioned disposable server; the suite will not start Compose in that mode. For external-server database checks, install `psql` on the test runner and set `PGHOST`, `PGDATABASE`, `PGUSER` and `PGPASSWORD` to the server's same disposable PostgreSQL database (and `PGPORT` if needed). Independently verify that connection matches the server, then set `E2E_PG_MATCHES_SERVER=1`; this is an attestation, not an automatic identity check. The database role needs SELECT for the checks and sufficient privileges to take `ACCESS EXCLUSIVE` lock on `promotions` (for example, table ownership or an appropriate UPDATE, DELETE or TRUNCATE privilege). Clean up only the test project and its volume after confirming the project name; do not remove a shared volume.

Support inquiry fields and chat messages are validated and trimmed; HTML-like tags and declarations are rejected, while ordinary comparison text such as `5 < 10` is allowed. Accepted inquiry text is stored as data, not sanitized HTML. The inquiry API returns a receipt, not the stored text, and there is currently no inquiry viewer. Chat messages and replies use Angular text interpolation, not HTML. This rejection is not a substitute for output encoding: any future viewer or export must apply context-appropriate output encoding.

JWTs expire after 24 hours by default and are independently invalidated by logout or 150 seconds of server inactivity. A client warning begins at 120 seconds. Remember User ID stores only the username; the session token is stored in the current tab's session storage. Demo passwords are BCrypt-hashed; the facilitator audit omits identity and account details.
