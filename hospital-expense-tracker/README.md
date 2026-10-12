# Hospital Financial Control Center

One application for hospital expense management: expense entry with invoices, approvals, budgets, vendors, overall reports, dashboard analytics and a tamper-evident audit trail.

**Stack:** React 18 (Vite, React Router, Recharts) and Spring Boot 3.3 (Java 17, Spring Security) with MongoDB. The web app is bundled into the Spring Boot jar, so **one Docker image / one free service / one URL** runs everything.

```
hospital-expense-tracker/
├── Dockerfile           builds web app + API into ONE image
├── backend/             Spring Boot API (also serves the web app)
├── frontend/            React app (Vite)
├── docs/SETUP_AND_DEPLOYMENT_GUIDE.md   <- start here
├── scripts/             build.sh, backup.sh, restore.sh
├── .github/workflows/   ci.yml, keepalive.yml, backup.yml
└── docker-compose.yml   local MongoDB (and the whole app with --profile full)
```

## What is in it

- **Overall expense management:** Dashboard with smart insights, **Reports** (period presets incl. Indian financial year, filters by department, category, vendor and status, charts, Excel/PDF/print), Budgets with rings and alerts, Vendors, Approvals.
- **Your own master data:** admins manage departments, categories and vendors in **Settings**; expenses can only use active entries.
- **Real-world security:** HttpOnly SameSite=Strict session cookie, CSRF header check, strict CSP and security headers, BCrypt(12), **two-factor authentication** (authenticator app + recovery codes, required for admins in production, secrets encrypted), **single sign-on** (OpenID Connect: Entra ID, Google, Keycloak, Okta), login lockout and request limits **shared across instances through MongoDB**, strong password policy with forced first-login change, instant session revocation, server-side role checks, content-verified uploads, hash-chained audit log with IP addresses and an integrity check, safe-start checks in production.
- **Recurring expenses:** rent, contracts and subscriptions create Draft expenses automatically on each due date (catches up after a sleeping server).
- **Operations:** invoice upload (PDF/JPG/PNG), email notifications (optional), CSV/Excel/PDF export, JSON and mongodump backups, a "waking up" banner and optional keep-alive for free hosting, GitHub Actions CI, automated tests (JUnit and Vitest).
- **Experience:** animated dashboard, command palette (Ctrl/Cmd+K), light and dark themes, responsive layout, reduced-motion support.

## Quick start (about 10 minutes)

1. Get a MongoDB connection string (free MongoDB Atlas, or `docker compose up -d` for local). See the guide, section 2.
2. Terminal 1: `cd backend`, set `MONGODB_URI`, then `mvn spring-boot:run` (needs JDK 17 and Maven).
3. Terminal 2: `cd frontend`, `cp .env.example .env`, `npm install`, `npm run dev`.
4. Open http://localhost:5173 and sign in with a demo account.

Or build the single jar with `./scripts/build.sh`, or run everything in Docker with `docker compose --profile full up --build` (http://localhost:8080).

Demo accounts exist only with `SEED_MODE=demo` (the default for local development). Password = `SEED_PASSWORD`, default `Password@123`: `admin` (Super Admin), `finance` (Finance Admin), `manager` (Department Manager), `accountant`, `staff`, `auditor`.

**For real use deploy with `SEED_MODE=bootstrap` and a strong `ADMIN_PASSWORD`.** The production image refuses to start with development secrets. See Parts 5 and 6 of the guide.

## Pages

Dashboard · Expenses · **Recurring** · Approvals · Budgets · **Reports** · Vendors · **Settings** (departments, categories, vendors) · Audit Logs (with integrity check) · Users · **Security** (2FA). Full table of who can do what: guide, Part 8.

## REST API (all under /api, JSON, session cookie, `X-Requested-With: XMLHttpRequest` required for changes)

| Method | Path | Roles |
|---|---|---|
| POST | /auth/login · /auth/mfa · /auth/logout | public |
| GET | /auth/config · /auth/sso/login · /auth/sso/callback | public |
| GET | /auth/me | signed in |
| GET | /expenses?department=&status=&q= | all (STAFF: own only) |
| POST | /expenses?submit= | all except Auditor |
| PUT | /expenses/{id}?submit= | creators (Draft/Rejected only) |
| POST | /expenses/{id}/submit · /approve · /reject · /pay | creators · approvers · approvers · payers |
| DELETE | /expenses/{id} | Super Admin, Finance Admin |
| POST/GET | /expenses/{id}/attachment | creators / anyone who can see the expense |
| GET | /reports/summary · /reports/expenses.xlsx · /reports/expenses.pdf | all except Staff (exports: Staff get own) |
| GET | /dashboard · /budgets | all except Staff |
| PUT | /budgets/{department} | Super Admin, Finance Admin |
| GET | /lookups | signed in |
| POST/PUT | /lookups · /lookups/{id} · /lookups/{id}/active | Super Admin, Finance Admin |
| GET | /audit · /audit/verify | Super Admin, Finance Admin, Auditor |
| GET/POST/PUT | /users · /users/{id}/active · /users/{id}/password | Super Admin |
| POST | /account/password | signed in |
| POST | /account/mfa/setup · /enable · /disable · /recovery-codes | signed in |
| GET/POST/PUT/DELETE | /recurring · /recurring/{id}/active | creators (Staff: own) |
| PUT | /users/{id}/mfa/reset | Super Admin |
| GET | /admin/backup | Super Admin |
| GET | /actuator/health (outside /api) | public |

## Not included yet

Passkeys / hardware keys (WebAuthn), SCIM user provisioning, a self-service "forgot password" email, server-side pagination for very large data sets, virus scanning for uploads.
