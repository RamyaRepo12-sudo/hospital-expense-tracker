# Complete Guide: Run, Secure, Upload to GitHub and Deploy for Free

The website and the API are now **one application**: Spring Boot serves the React app and the REST API from the same address. That means one build, one Docker image, one free hosting service, one URL, and no CORS settings.

```
 Browser  ──HTTPS──▶  Spring Boot (one Docker service on Render)
                         ├─ serves the React web app (static files)
                         ├─ REST API under /api  (session in an HttpOnly cookie)
                         └─ MongoDB driver ──▶ MongoDB Atlas (free M0 cluster)
```

Only a few things must be configured: where the database is (`MONGODB_URI`), a signing secret (`JWT_SECRET`), an encryption key for two-factor secrets (`MFA_ENCRYPTION_KEY`) and how the first admin is created. Single sign-on, email and extra instances are optional.

First-time total: about 60 minutes. Follow the Parts in order.

---

## Part 1. Install the tools

| Tool | Version | Check with | Download |
|---|---|---|---|
| JDK | 17 or newer (Temurin) | `java -version` | adoptium.net |
| Maven | 3.9+ | `mvn -v` | maven.apache.org |
| Node.js | 18+ (20 LTS best) | `node -v` | nodejs.org |
| Git | any recent | `git --version` | git-scm.com |
| Docker (optional) | any recent | `docker -v` | docker.com |

Unzip `hospital-expense-tracker.zip`. You get `backend/`, `frontend/`, `Dockerfile`, `docs/`, `scripts/`, `.github/`, `docker-compose.yml`, `README.md`.

## Part 2. Connect MongoDB

The app reads one setting, `MONGODB_URI`. If it is not set it uses `mongodb://localhost:27017/hospital_expense`.

### Option A: MongoDB Atlas (cloud, free, needed for deployment)

1. Go to mongodb.com/cloud/atlas, create an account and sign in.
2. Create a project, then **Create** a cluster and choose **M0 Free**. Pick a region near you. Wait 1 to 3 minutes.
3. **Database Access → Add New Database User.** Password authentication, a username such as `hospitalapp`, and a strong password. Prefer letters and digits: characters like `@ : / ? # %` must be URL-encoded inside the connection string. Role: *Read and write to any database*.
4. **Network Access → Add IP Address.**
   - Local development: *Add Current IP Address*.
   - Render's free plan has no fixed address, so add `0.0.0.0/0`. This is acceptable only because the database still needs your strong password.
5. **Database → Connect → Drivers.** Copy the string:
   `mongodb+srv://hospitalapp:<password>@cluster0.xxxxx.mongodb.net/?retryWrites=true&w=majority`
6. Replace `<password>` and **add the database name before the `?`**:
   `mongodb+srv://hospitalapp:YourPassword1@cluster0.xxxxx.mongodb.net/hospital_expense?retryWrites=true&w=majority`
7. Keep it private. Never commit it to GitHub.

You do not create collections. On first start the app creates `users`, `expenses`, `budgets`, `lookups`, `audit_logs` (and `fs.*` for invoice files).

### Option B: MongoDB on your computer (Docker)

```bash
docker compose up -d        # starts MongoDB on port 27017
```

## Part 3. Run everything locally

Pick one way.

### 3A. Development (two terminals, instant reload)

Terminal 1, the API:
```bash
cd backend
export MONGODB_URI="mongodb+srv://..."      # skip for Option B. Windows PowerShell: $env:MONGODB_URI="..."
mvn spring-boot:run
```
Check http://localhost:8080/actuator/health shows `{"status":"UP"}`.

Terminal 2, the web app:
```bash
cd frontend
cp .env.example .env          # Windows: copy .env.example .env
npm install
npm run dev
```
Open **http://localhost:5173**. The dev server forwards `/api` to port 8080, so the browser sees one address, exactly like production, and the session cookie works.

### 3B. Single jar (what production runs)

```bash
./scripts/build.sh                                    # Windows: run the commands inside it by hand
java -jar backend/target/hospital-expense-api.jar     # then open http://localhost:8080
```

### 3C. Everything in Docker

```bash
docker compose --profile full up --build
```
Open http://localhost:8080.

### Sign in (local development)

With no profile set, the app starts in development mode with six demo accounts (`SEED_MODE=demo`), all with password `Password@123`:

| Username | Role | Highlights |
|---|---|---|
| admin | Super Admin | everything, including Users and Settings |
| finance | Finance Admin | approve, pay, delete, budgets, settings, audit |
| manager | Department Manager | create, approve or reject |
| accountant | Accountant | create, mark paid |
| staff | Staff | own expenses only |
| auditor | Auditor | read-only dashboards, reports, audit log |

The demo-account buttons on the sign-in page appear only because `.env` has `VITE_SHOW_DEMO=true`.

### Quick connection test

1. Sign in as `finance`. The dashboard shows numbers and charts.
2. Add an expense and submit it; in Atlas open `expenses` and find the new document.
3. Press **Ctrl K** (Cmd K on Mac) for the command palette.

## Part 4. Upload to GitHub

1. Create a free account at github.com and set your identity once:
   ```bash
   git config --global user.name "Your Name"
   git config --global user.email "you@example.com"
   ```
2. **+ → New repository**, name `hospital-expense-tracker`, **Private** (strongly recommended for a hospital project). Leave "Add a README / .gitignore / license" **unchecked**. Create it.
3. In the project root:
   ```bash
   git init
   git add .
   git status
   ```
   **Read the `git status` list.** It must NOT contain `.env`, `node_modules`, `target`, `dist` or `backend/src/main/resources/static`. If it does, stop and fix `.gitignore` first.
   ```bash
   git commit -m "Initial commit: hospital expense tracker"
   git branch -M main
   git remote add origin https://github.com/YOUR_USERNAME/hospital-expense-tracker.git
   git push -u origin main
   ```
4. **Signing in when Git asks:** GitHub no longer accepts your account password for pushes. Use the browser sign-in window, or create a *Personal Access Token* (Settings → Developer settings → Personal access tokens, `repo` scope) and paste it as the password, or use GitHub Desktop.
5. Later changes: `git add .`, `git commit -m "What changed"`, `git push`. Render redeploys automatically on every push to `main`.
6. **Automatic checks:** `.github/workflows/ci.yml` runs the backend tests, the frontend tests and build, and builds the Docker image on every push. Open the **Actions** tab to see a green tick or a red cross.
7. **Secrets:** if you ever commit a password or connection string, change it immediately (Atlas password, new `JWT_SECRET`). Deleting the file later does not remove it from Git history. Avoid the "Upload files" button on github.com: it ignores `.gitignore`.

## Part 5. Deploy for free (one service on Render)

Free plans change, so check Render's and MongoDB's pricing pages.

### 5.1 Generate your secrets

```bash
openssl rand -base64 48        # use as JWT_SECRET
```
No openssl? `node -e "console.log(require('crypto').randomBytes(48).toString('base64'))"`.
Generate a second one for `MFA_ENCRYPTION_KEY`. Also choose a strong first-admin password (10+ characters, with a letter and a number, not a common word, not containing the username).

### 5.2 Create the service

1. Sign up at render.com with GitHub.
2. **New + → Web Service →** select your repository.
3. Settings:
   - **Language / Runtime:** Docker
   - **Root Directory:** leave **empty** (the `Dockerfile` is in the project root)
   - **Instance Type:** Free
   - **Health Check Path:** `/actuator/health`
4. **Environment Variables.** The image runs in `prod` mode and **refuses to start** with the built-in development secret or the default demo password:

   | Key | Value |
   |---|---|
   | `MONGODB_URI` | your Atlas string (with `/hospital_expense`) |
   | `JWT_SECRET` | the generated secret (32+ characters) |
   | `SEED_MODE` | `bootstrap` (real use: one admin, no sample data) or `demo` (six demo users and sample expenses) |
   | `ADMIN_PASSWORD` | the first admin's password (required for `bootstrap`) |
   | `ADMIN_USERNAME` | optional, default `admin` |
   | `ADMIN_NAME` | optional, default `Hospital Admin` |
   | `ADMIN_EMAIL` | optional, for notifications |
   | `SEED_PASSWORD` | only for `SEED_MODE=demo`: a strong password for the demo users (not `Password@123`) |
   | `MFA_ENCRYPTION_KEY` | a second random secret (generate it like `JWT_SECRET`). It encrypts 2FA secrets in the database. **Set it once and never change it.** |
   | `APP_URL` | your service address, e.g. `https://hospital-expense.onrender.com` (used in email links and as the SSO redirect address) |
   | `MFA_REQUIRED_ROLES` | optional. Default in production: `SUPER_ADMIN,FINANCE_ADMIN` must use two-factor authentication. Use e.g. `SUPER_ADMIN,FINANCE_ADMIN,AUDITOR`, or `none` to switch the requirement off. |
   | `RATE_LIMIT_STORE` | optional. `mongo` (default) shares lockouts and limits between instances. `memory` saves one database write per request when you run a single instance. |
   | SSO settings | optional, see Part 7.7 |

5. **Create Web Service.** The first build takes several minutes (it builds the web app, then the API). When it says *Live*, open `https://YOUR-SERVICE.onrender.com`: the sign-in page loads from the same address as the API. `/actuator/health` should show `{"status":"UP"}`.

There is no Vercel, no CORS setting and no separate frontend address. If you ever put the site on a custom domain, nothing needs changing except `APP_URL`.

### 5.3 First sign-in

Sign in as your admin (`ADMIN_USERNAME`, `ADMIN_PASSWORD`). You are asked to choose your own password immediately. Then follow Part 6.

## Part 6. Security model and go-live checklist

### What protects the system

| Risk | Protection built into the code |
|---|---|
| Stolen login by injected script (XSS) | The session is an **HttpOnly, SameSite=Strict, Secure** cookie. JavaScript cannot read it. A strict **Content-Security-Policy** allows scripts only from your own site. |
| Forged requests from other sites (CSRF) | SameSite=Strict cookie **plus** a required `X-Requested-With` header on every change, and no cross-origin access at all. |
| Password guessing | BCrypt (cost 12). Five failed sign-ins lock that username and address for 15 minutes. A per-address limit of 240 API requests per minute. Response time does not reveal whether a username exists. **These counters are stored in MongoDB, so they hold across several running instances.** |
| Stolen or guessed passwords | **Two-factor authentication** (authenticator app, 6-digit codes, one-time recovery codes). Required by default for Super Admin and Finance Admin in production. 2FA secrets are encrypted at rest (AES-256-GCM). Codes cannot be replayed, and wrong codes are rate-limited. |
| Many logins to manage | **Single sign-on** with OpenID Connect (Microsoft Entra ID, Google, Keycloak, Okta), using PKCE and server-side code exchange. Accounts are never created automatically; the work email must match an existing active user. |
| Weak passwords | 10+ characters, a letter and a number, no common passwords, must not contain the username. New users and admin resets must **choose their own password at first sign-in**. |
| Old sessions after a change | Changing, resetting or deactivating an account **invalidates every existing session immediately** (token version). Roles are always read from the database, never from the token. |
| Privilege abuse | Role checks on every API call. You cannot approve your own expense. Staff only ever see their own expenses (in lists, exports and files). |
| Bad or hostile data | Input length and amount limits. Departments, categories and vendors must come from the admin-managed lists. Uploads are checked by file **content** (PDF, JPG, PNG, 5 MB). |
| Tampering with history | The audit log is **hash-chained**, records each user's IP address, and **Verify integrity** (Audit Logs page) detects edited or deleted entries. A unique sequence number plus retry keeps the chain from forking when two instances write at once. Expense numbers come from an atomic database counter, so they stay unique even after deletions. |
| Insecure startup | The production image refuses to start with the development secret or the default demo password. |
| Clickjacking, sniffing | `X-Frame-Options: deny`, `frame-ancestors 'none'`, HSTS, `nosniff`, strict referrer and permissions policies. |
| Container | Runs as a non-root user. Error pages hide stack traces. |

What it does **not** do: it is not a substitute for your hospital's own compliance review. Not included: hardware security keys / passkeys, SMS codes, automatic user provisioning (SCIM) and a self-service "forgot password" email (a Super Admin resets passwords).

### Go-live checklist

1. Deploy with `SEED_MODE=bootstrap` and a strong `ADMIN_PASSWORD` (Part 5). If the database already holds demo data, use a fresh database name in `MONGODB_URI` (for example `hospital_prod`), because seeding only runs on empty collections.
2. Sign in and set your own password.
3. **Settings:** add your real departments, categories and vendors (and deactivate any you do not use). New departments get a default monthly budget of ₹1,00,000.
4. **Budgets:** set real monthly limits.
5. **Two-factor authentication:** the Super Admin and Finance Admin are asked to set it up at their next sign-in (production default). Add more roles with `MFA_REQUIRED_ROLES`. Everyone else can turn it on under **Security**. Keep each person's recovery codes safe.
6. **Users:** create real accounts with temporary passwords (add email addresses if you will use notifications). Each person is forced to choose a new password at first sign-in. Create a second Super Admin so you are never locked out.
7. Set up **backups** (Part 7.2) and test a restore once.
8. Keep the GitHub repository **private**.
9. Atlas: keep a strong database password. `0.0.0.0/0` is needed only because Render's free plan has no fixed address.
10. Keep patient clinical data out of this system. It is for operational finance records only.
11. Optional: connect **single sign-on** (Part 7.7).
12. Review who holds Super Admin and Finance Admin roles every quarter, and use **Verify integrity** on the audit log regularly.

## Part 7. Operations

### 7.1 The free server sleeps (Render)
- **Waking banner:** if the app is asleep, the page retries quietly for about 70 seconds and shows "Waking up the server" instead of an error.
- **Keep-alive (optional):** `.github/workflows/keepalive.yml` pings `/actuator/health` every 10 minutes. Enable it with a repository **variable** `API_URL` = `https://YOUR-SERVICE.onrender.com` (Settings → Secrets and variables → Actions → Variables). It uses your monthly free instance hours, and GitHub pauses scheduled workflows on public repositories after 60 days without activity. A hospital that needs instant responses should use a paid instance.

### 7.2 Backups (Atlas free has no guaranteed backups)
1. **In the app:** Users page → **Download JSON backup** (Super Admin): expenses, budgets, lists, audit logs and users (no password hashes, no invoice files).
2. **Full copy with mongodump** (includes invoice files):
   ```bash
   export MONGODB_URI="mongodb+srv://..."
   ./scripts/backup.sh                 # creates hospital-backup-<date>.archive.gz
   ./scripts/restore.sh <that-file>    # asks you to type YES, then replaces the data
   ```
   Install the MongoDB Database Tools first.
3. **Weekly encrypted backup** with `.github/workflows/backup.yml`. It **only runs on a private repository**. Add secrets `MONGODB_URI` and `BACKUP_PASSPHRASE`. Files appear under Actions → the run → Artifacts for 30 days. Restore:
   ```bash
   openssl enc -d -aes-256-cbc -pbkdf2 -pass env:BACKUP_PASSPHRASE -in backup.enc -out backup.archive.gz
   mongorestore --uri="$MONGODB_URI" --archive=backup.archive.gz --gzip --drop
   ```
Test a restore into a spare database at least once.

### 7.3 Email notifications (optional)
Approvers are emailed when an expense is submitted; the creator is emailed when it is approved, rejected or paid. Add in Render:

| Key | Example |
|---|---|
| `MAIL_ENABLED` | `true` |
| `SPRING_MAIL_HOST` | `smtp-relay.brevo.com` (or `smtp.gmail.com`) |
| `SPRING_MAIL_PORT` | `587` |
| `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` | your SMTP login and key |
| `MAIL_FROM` | an address your provider allows |
| `APP_URL` | your service address |

Add each person's email on the Users page. Emails contain only expense ID, vendor, department, category and total.

### 7.4 Invoices and exports
- **Invoices:** attach a PDF, JPG or PNG (5 MB) on the expense form while it is a draft or rejected. Files live in MongoDB GridFS and count against Atlas's 512 MB free storage. Download them from the expense's **View** dialog.
- **Exports:** CSV, Excel and PDF on Expenses and Approvals; Excel and PDF on Reports (with the report's filters). Capped at 5,000 rows. Staff only receive their own expenses.

### 7.5 Tests
```bash
cd backend  && mvn test      # login lockout, shared rate store, JWT and token versions, 2FA codes (RFC 6238 test vectors), secret encryption, recovery codes, SSO helpers (PKCE RFC 7636 vector), recurrence dates, cookies, CSRF header, audit chain, password policy, startup checks, workflow rules, upload checks, money maths
cd frontend && npm test      # formatting, budget levels, permissions, API client (cookie, CSRF header, retry, session expiry)
```

### 7.6 Two-factor authentication (2FA)
- **Turn it on:** Security page → *Turn on two-factor authentication* → confirm your password → scan the QR code with Google Authenticator, Microsoft Authenticator, Authy or 1Password → type the 6-digit code → save your 10 **recovery codes** (each works once; they are shown only now).
- **Signing in:** password first, then the 6-digit code (or a recovery code).
- **Required roles:** in production Super Admin and Finance Admin must set it up before they can use anything else. Change this with `MFA_REQUIRED_ROLES`. Users in a required role cannot turn it off.
- **Lost phone:** sign in with a recovery code and generate new ones under Security, or ask a Super Admin to use **Users → Reset 2FA**. The user then signs in with the password and sets it up again. If the only Super Admin loses everything, restore from a backup or clear that user's `totpEnabled` and `totpSecretEnc` fields in Atlas.
- **`MFA_ENCRYPTION_KEY`:** 2FA secrets are encrypted with it (it falls back to `JWT_SECRET` if you forget). Changing either value makes existing 2FA secrets unreadable, so set it once and keep it with your other secrets.
- Phone clock wrong? Codes fail. Turn on automatic time on the phone.

### 7.7 Single sign-on (OpenID Connect)
Lets staff sign in with their work account (and the identity provider's own multi-factor rules). Accounts are **not** created automatically: create the user under **Users** with the same work **email address**, then the person can use the single sign-on button. SSO sign-ins skip the app's own 2FA because your identity provider handles it.

1. In your identity provider create a **web application** and set the redirect address to `APP_URL` + `/api/auth/sso/callback`, for example `https://hospital-expense.onrender.com/api/auth/sso/callback`.
2. Add these in Render, then redeploy:

   | Key | Value |
   |---|---|
   | `SSO_ENABLED` | `true` |
   | `OIDC_ISSUER` | the provider's issuer address (see below) |
   | `OIDC_CLIENT_ID` | the application (client) ID |
   | `OIDC_CLIENT_SECRET` | the client secret value |
   | `APP_URL` | your service address (must match the redirect address above) |
   | `SSO_PASSWORD_LOGIN` | optional. `false` turns off password sign-in for everyone except Super Admin accounts (your break-glass access). |

| Provider | Where to register | `OIDC_ISSUER` |
|---|---|---|
| Microsoft Entra ID | Azure portal → Entra ID → App registrations → New registration → Web redirect → Certificates & secrets → New client secret (copy the *Value*) | `https://login.microsoftonline.com/<tenant-id>/v2.0` (the Directory/tenant ID, not "common") |
| Google Workspace | Google Cloud console → APIs & Services → Credentials → OAuth client ID → Web application | `https://accounts.google.com` |
| Keycloak | Clients → Create (OpenID Connect, client authentication on), valid redirect URI, Credentials tab for the secret | `https://HOST/realms/REALM` |
| Okta | Applications → Create App Integration → OIDC → Web | `https://YOUR.okta.com/oauth2/default` |

The app matches the ID token's `email` claim, or Microsoft's `preferred_username` / `upn`, to the user's email (case-insensitive). With Entra ID make sure users' work email equals their sign-in name, or add the optional `email` claim. Auth0 issuers end with a slash and are not supported out of the box.

### 7.8 Recurring expenses
Open **Recurring** → *New recurring expense*: pick how often (weekly, monthly, every 3 months, yearly), the first and optional last date, and the usual department, category, vendor and amount. On each due date a **Draft** expense is created for you (invoice number "RECURRING-date" as a placeholder). Open it, check the amount and invoice, attach the file, and submit it for approval as usual. Nothing is approved or paid automatically.
- It runs every night, when the server starts, and after each sign-in, so a free server that was asleep catches up as soon as it wakes (up to 12 missed dates per run).
- Month-end dates do not drift (31 Jan, 28 Feb, 31 Mar). You can pause, resume or delete a rule; drafts already created are kept.
- With email turned on (7.3) the creator gets a note when drafts are ready.

### 7.9 Running more than one instance
Free hosting runs one instance, but the code is safe for several: lockouts and request limits live in MongoDB (`RATE_LIMIT_STORE=mongo`), audit entries and expense numbers are protected by unique database keys and atomic counters, and recurring drafts use a unique key per rule and date so nothing is created twice. If you stay on one instance and want fewer database writes, set `RATE_LIMIT_STORE=memory`.

## Part 8. Using the website

| Page | Who | What you can do |
|---|---|---|
| Dashboard | all except Staff | KPIs, smart insights, trends, budget alerts. Click a department bar to open its expenses. |
| Expenses | everyone (Staff: own) | Add, edit, submit, approve, reject, pay, delete. Search, filter, sort, export. Attach invoices. |
| Approvals | approvers | Pending items only. |
| Budgets | all except Staff | Rings and bars per department; finance roles edit limits. |
| **Recurring** | everyone who can create expenses | Set up repeating expenses; drafts are created automatically on each due date. |
| **Security** | everyone | Turn two-factor authentication on or off, new recovery codes. |
| **Reports** | all except Staff | Pick a period (month, last month, 3 months, financial year from 1 April, calendar year, all time or custom dates), then filter by department, category, vendor and status. See totals, averages, GST, charts by month, department, category and vendor, status and payment tables. Export Excel or PDF, or print. |
| Vendors | all except Staff | Invoices, total paid and outstanding per vendor. |
| **Settings** | Super Admin, Finance Admin | Manage departments, categories and vendors. Deactivating hides an item from new expenses and keeps history. |
| Audit Logs | Super Admin, Finance Admin, Auditor | Who did what, from which address, plus **Verify integrity**. |
| Users | Super Admin | Create users, reset passwords, reset 2FA for a lost phone, deactivate, download a backup. |

Press **Ctrl K** anywhere to jump to a page or search expenses.

## Part 9. Troubleshooting

| Symptom | Likely cause and fix |
|---|---|
| Render log: `Refusing to start: set JWT_SECRET...` or `...set a strong SEED_PASSWORD...` | Intended safety check. Set `JWT_SECRET`, and either `SEED_MODE=bootstrap` with `ADMIN_PASSWORD`, or a strong `SEED_PASSWORD`, or `SEED_DATA=false`. |
| Render log: `SEED_MODE=bootstrap requires ADMIN_PASSWORD` | Add `ADMIN_PASSWORD` (10+ characters, a letter and a number). |
| Cannot sign in after switching to bootstrap | Seeding only runs on empty collections. Use a fresh database name or drop `users` in Atlas. |
| `MongoTimeoutException` / cannot connect | Atlas Network Access does not allow your address (`0.0.0.0/0` for Render). |
| `Authentication failed` | Wrong database user or password, or special characters not URL-encoded. |
| Page loads but every call says 401 / you keep being signed out | Your password was changed or the account was deactivated (sessions end at once), or the 8-hour session expired. Sign in again. |
| `403 Missing required request header` | A script or tool is calling the API without `X-Requested-With: XMLHttpRequest`. The web app sends it automatically. |
| "Too many failed attempts" | Wait 15 minutes, or restart the service. |
| "Too many requests" | More than 240 API requests per minute from one address. Wait a minute. |
| Blank page after deploy | The Docker build did not include the web app. Check the build log for the `npm run build` step; make sure Root Directory is empty. |
| "Waking up the server" never ends | The service crashed. Open Render logs (usually `MONGODB_URI` or a refused safety check). |
| Department, category or vendor "is not in the active list" | Add it under **Settings** first. |
| Invoice upload says "Only PDF, JPG or PNG" | The file's content is not one of those types, whatever its name says. |
| No emails arrive | `MAIL_ENABLED=true`, `SPRING_MAIL_HOST` and credentials set? Does the user have an email? Check logs for "Email to ... failed". |
| "Set up two-factor authentication before continuing" | Your role requires 2FA. Complete the setup dialog (Security page), then continue. |
| 2FA code is always rejected | The phone clock is wrong (turn on automatic time) or the code was already used. Use a recovery code, or ask a Super Admin to reset your 2FA. |
| 2FA suddenly fails for everyone / "Could not decrypt" in logs | `MFA_ENCRYPTION_KEY` (or `JWT_SECRET` if the key was never set) was changed. Restore the old value, or reset users' 2FA. |
| SSO button missing | `SSO_ENABLED` is not `true`. If it is `true` but `OIDC_ISSUER`, `OIDC_CLIENT_ID` or `OIDC_CLIENT_SECRET` is missing the app refuses to start; check the logs. |
| SSO: "did not complete" | The redirect address in the provider differs from `APP_URL` + `/api/auth/sso/callback`, or the client secret or issuer is wrong. Check Render logs for "SSO sign-in failed". |
| SSO: "not linked to an active user" | Create the user under Users with the same work email as in the identity provider. |
| Recurring draft did not appear | Is the rule paused or finished? Check the next date. Drafts are created the first time the server runs on or after the due date. |
| Port 8080 already in use | Stop the other process or set `PORT=8081` (and the proxy target in `frontend/vite.config.js`). |
| Red cross in GitHub Actions | Open the failed job and read the first error line. |

## Part 10. Ideas for later

Passkeys / hardware security keys (WebAuthn), automatic user provisioning (SCIM), a self-service "forgot password" email, server-side pagination for very large data sets, virus scanning for uploads, error monitoring, and a custom domain (add it in Render, then update `APP_URL` and the SSO redirect address).
