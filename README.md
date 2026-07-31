# HarvestFlow Backend — Spring Boot API

REST API for the Harvesting Planning, Purchase, Sales & Logistics Workflow
System. Implements the same 11-stage state machine as the Flutter frontend,
with real server-side role + status enforcement on every transition.

## Stack

- **Java 17 + Spring Boot 3.3** — Web, Data JPA, Security, Validation
- **PostgreSQL** (H2 in-memory for tests)
- **JWT** (jjwt) for stateless auth
- **Lombok** to cut entity/DTO boilerplate

## Architecture

```
domain/
  enums/       UserRole, CaseStatus (state machine + nextActorRole()), MasterCategory, LogisticsKind, PackingStage
  entity/      JPA entities — HarvestCase is the spine record; every stage
               after Plot Selection is a separate child entity that stays
               null until that stage's endpoint saves it
repository/    Spring Data JPA interfaces
security/      JwtService, JwtAuthFilter, SecurityUser (UserDetails), CustomUserDetailsService
config/        SecurityConfig (stateless JWT + CORS), DataSeeder
dto/
  auth/        Login, change-password
  user/        Create user, user response, one-time credential response
  master/      Master data request/response
  cases/       One request DTO per stage + response DTOs (summary, full detail, timeline)
  reports/     Reports summary
exception/     ResourceNotFoundException, InvalidTransitionException,
               ForbiddenRoleException, DocumentNotReadyException +
               GlobalExceptionHandler for a consistent error shape
service/       AuthService, UserService, MasterDataService, CaseService
               (all the state-machine logic), ReportsService
  pdf/         PdfDocumentService — generates the four SOW documents
               (OpenPDF) on demand from a case's current data; nothing
               is persisted, so there's no storage decision to make here
controller/    Thin REST layer — AuthController, UserController,
               MasterDataController, CaseController, ReportsController,
               DocumentController
util/          CredentialGenerator (auto username + temp password), InvoiceNumberGenerator
```

### The state machine — where the real enforcement lives

`CaseStatus.nextActorRole()` is the single source of truth for "whose turn
is it": each of the 11 statuses maps to exactly one role. Every stage
transition in `CaseService` goes through `assertTransition(case, actor,
expectedStatus)`, which checks **both**:

1. the case is currently in the exact status this action expects (not
   skipped, not repeated), and
2. the requesting user's role matches that status's `nextActorRole()`.

This is what the Flutter prototype flagged as missing — a client-side
"view only" flag can't actually stop anyone; this can. `updatePlotSelection`
is the concrete example: Plot Selection can edit their own record only
while status is still `SUBMITTED_FOR_PLANNING` — the moment Planning saves
a plan, the status moves on and the same edit call returns 403.

### Why some SOW roles share one entity/endpoint

- **Pickup, Transport, Labor, Local Labor** (SOW steps 6, 8, 9, 10) are all
  "a name, an amount, an optional advance, optional remarks" — modeled as
  one `LogisticsEntry` entity tagged with a `LogisticsKind`, not four
  near-identical tables.
- **Labor + Local Labor** are submitted together in one `/labor` call,
  because one role (Labor Coordinator) owns both and a case can only sit
  in one pending queue at a time — splitting them would strand local labor
  entry with nothing to make it "pending" on.
- **Packing issue and return** (SOW 5.5 and step 11) share a
  `PackingMaterialRecord` shape, tagged with a `PackingStage`.
- **Eicher Truck Driver** has no dedicated status in the SOW's workflow
  table, so `logMilestone` is informational only — it appends to the
  timeline without moving the state machine, gated only by "is this case
  currently in an active trip" rather than a status transition.

## Running it

### 1. Start Postgres

```bash
docker compose up -d
```

### 2. Configure environment

Copy `.env.example` → `.env` (or export the variables directly) and adjust
`JWT_SECRET` for anything beyond local dev.

### 3. Run

```bash
./mvnw spring-boot:run
```

(No `mvnw` wrapper is checked in yet — run `mvn -N io.takari:maven:wrapper`
once, or use a local Maven install: `mvn spring-boot:run`.)

The API starts on `http://localhost:4000`. On first run with an empty
database, `DataSeeder` creates:

- One user per role — see the table below for login credentials
- Master data for all 9 categories
- Two sample cases: one fresh (Plot Selection, farmer Suresh Jadhav) and one
  already through Planning + Rate Update (farmer Anil Kadam) — matching
  the Flutter app's own seed data so both sides demo consistently.

### Seeded logins

| Role | Username | Password |
|---|---|---|
| Admin | admin.user | admin123 |
| Plot Selection | priya.sharma | plot123 |
| Planning | rahul.deshpande | plan123 |
| Godown | suman.gaikwad | god123 |
| Supervisor | ganesh.more | sup123 |
| Purchase Account | vikram.joshi | pur123 |
| Pickup Person | ramesh.sable | pick123 |
| Transport Person | anita.kulkarni | trans123 |
| Labor Coordinator | suresh.patil | labor123 |
| Eicher Truck Driver | dilip.chavan | drive123 |

All seeded users have `mustChangePassword = false`, unlike real
Admin-created users, purely so the demo doesn't force a password change
on first login.

## API surface

All endpoints except `/api/auth/**` require `Authorization: Bearer <token>`.

**Auth**
- `POST /api/auth/login` → `{ token, userId, username, name, role, mustChangePassword }`
- `POST /api/auth/change-password`

**Users** (Admin only)
- `GET /api/users`
- `POST /api/users` → returns the plaintext temp password once
- `PATCH /api/users/{id}/toggle-active`
- `POST /api/users/{id}/reset-password` → returns a new temp password once

**Master Data** (reads open to any authenticated user; writes Admin only)
- `GET /api/master-data/{category}?activeOnly=true`
- `POST /api/master-data/{category}`
- `PATCH /api/master-data/{category}/{id}/toggle-active`
- `DELETE /api/master-data/{category}/{id}`

**Cases** — the workflow itself
- `GET /api/cases?q=&status=` — search/filter (Plot Selection list view)
- `GET /api/cases/pending` — the logged-in user's own queue
- `GET /api/cases/active-trips` — feeds the Eicher Truck Driver's screen
- `GET /api/cases/{id}` — full detail across every stage + timeline
- `POST /api/cases` — create (Plot Selection)
- `PUT /api/cases/{id}/plot-selection` — edit, only while still editable
- `POST /api/cases/{id}/planning`
- `POST /api/cases/{id}/purchase-rate`
- `POST /api/cases/{id}/packing-issue`
- `POST /api/cases/{id}/harvest-completion`
- `POST /api/cases/{id}/pickup`
- `POST /api/cases/{id}/transport`
- `POST /api/cases/{id}/labor` — `{ labor, localLabor? }`
- `POST /api/cases/{id}/packing-return`
- `POST /api/cases/{id}/purchase-invoice`
- `POST /api/cases/{id}/sales-invoice`
- `POST /api/cases/{id}/milestone` — Eicher Truck Driver only

**Reports**
- `GET /api/reports/summary`

**Documents** — generated on demand from the case's current data, nothing
persisted, so they always reflect the latest state rather than a stale
snapshot from whenever first requested. Open to any authenticated user,
same access model as case reads generally. Returns 409 if the underlying
stage data doesn't exist yet (e.g. asking for the purchase invoice before
one has been generated).
- `GET /api/cases/{id}/documents/selection-report`
- `GET /api/cases/{id}/documents/packing-issue-slip`
- `GET /api/cases/{id}/documents/purchase-invoice`
- `GET /api/cases/{id}/documents/sales-invoice`

Every mutating endpoint returns 409 if the case isn't in the expected
status, or 403 if the caller's role doesn't own that transition — the
same two checks described above, surfaced as real HTTP responses.

## Not yet built

- File storage for weight slip / plot photo uploads — `weightSlipFileName`
  and `photoPath` are plain string fields today; wiring them to actual
  object storage (S3-compatible) is the next step once uploads are needed.
- Automatic email/WhatsApp sending — the SOW itself describes WhatsApp
  sharing as a "download and send" approach, so `PdfDocumentService` +
  `DocumentController` intentionally stop at generating the PDF; the
  Flutter app hands it to the device's native share sheet from there
  (WhatsApp, email, or anything else installed) rather than this backend
  calling a WhatsApp Business API or SMTP server. If automatic server-sent
  email is wanted later, that's a small, separate addition once mail
  server credentials exist.
- Excel export for reports.
- Flyway/Liquibase migrations — `ddl-auto: update` is fine for early
  development but should be replaced before any real deployment.
- Rate limiting / login attempt lockout, session/token refresh flow.

## Testing

`src/test/resources/application-test.yml` configures an in-memory H2
database so tests don't need Postgres running. Run with:

```bash
mvn test
```

No tests are checked in yet — this is the natural next addition once the
Flutter app is wired up and the request/response contracts are confirmed
against real usage.
