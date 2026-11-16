# Nova Bank demonstration — identity slice

This is an isolated synthetic demonstration, not a real bank. Never use real customer information or expose seeded credentials to the public. Only sign-in, session activity and sign-out are implemented; portfolio, support and facilitator reset are not yet implemented. Locked users cannot be unlocked through the application in this slice.

## Run with Compose

Set `DB_PASSWORD` and a unique `JWT_SECRET` of at least 32 bytes in your shell. For an isolated demo only, set `DEMO_MODE=true` and `DEMO_PASSWORD=demo-only`; the startup seeder creates Miguel, Rosa and admin if absent and never resets existing users. Then run `docker compose up --build`. Open http://localhost:8080. TLS must be terminated in front of deployed traffic. Configuration examples are in each tier's `.env.example`; Spring does not automatically load those files. The database uses a named volume.

## Development and verification

Provision an empty PostgreSQL database first. Export backend settings shown in `backend/.env.example`, then run `mvn -f backend/pom.xml spring-boot:run`. Install frontend dependencies with `cd frontend && npm install`, then `npm run build`. The production Nginx configuration proxies same-origin `/api/` to `API_UPSTREAM`; for Angular development serve behind an equivalent reverse proxy or use the Compose frontend.

To run the backend tests, set `TEST_DATABASE_URL`, `TEST_DB_USERNAME` and `TEST_DB_PASSWORD` to a **dedicated disposable PostgreSQL database**; tests delete identity tables' contents. Then run `mvn -f backend/pom.xml -Dtest=AuthServiceTest,AuthApiTest test`. Component tests: `cd frontend && npm test -- --browsers=ChromeHeadless`. Playwright requires running services and a fresh demo database: `node frontend/node_modules/@playwright/test/cli.js test --config frontend/playwright.config.ts`.

JWTs expire after 24 hours by default and are independently invalidated by logout or 150 seconds of server inactivity. A client warning begins at 120 seconds. Remember User ID stores only the username; the session token is stored in the current tab's session storage. Demo passwords are BCrypt-hashed; audit records omit passwords and account details.
