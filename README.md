# Mortgage Pricing Exception Service

A small Spring Boot HTTP API that manages **mortgage pricing exception requests**:
relationship managers (RMs) request a discount (in basis points) off the standard rate on a
mortgage application, reviewers approve or decline the request, and a downstream mortgage
application process can look up the currently-approved discount for an application. Every state
transition is recorded in an append-only audit trail.

This was built as a take-home-style exercise. This README covers what the problem is and how to
run/try it; deeper design rationale, the full API reference, and process notes live in
[Further documentation](#further-documentation) below.

**Time spent:** approximately 5 hours.

## Requirements

- Java 21
- Spring Boot 4

## Problem summary

- RMs create pricing exception requests against an existing mortgage application, with a
  requested discount and a reason.
- Reviewers approve or decline pending requests, and any RM can withdraw a pending request.
- A mortgage application process needs to query "what discount, if any, is currently approved for
  this application" without needing to know about the review workflow.
- Every state transition must be auditable (who did what, when, and why).
- Create must be safe to retry (idempotent) since RM clients may resubmit on network failure.
- Everything must run locally with no paid/private infrastructure.

Several points in the brief were deliberately ambiguous or conflicting (e.g. can an RM edit a
submitted request?) — see [docs/DECISIONS.md](docs/DECISIONS.md) for how each was resolved and why.

## Quick start

```bash
# Run the app (serves the API and the bonus UI at http://localhost:8080/)
./mvnw spring-boot:run

# Run the full test suite
./mvnw test

# Just compile
./mvnw compile
```

If any of these fail with a dependency-resolution/connectivity error, it's likely because your
machine has a corporate-wide Maven mirror configured (in your own `~/.m2/settings.xml`) that isn't
reachable from your current network. This repo ships a `.mvn/settings.xml` that forces public
Maven Central instead — retry the failing command with `-s .mvn/settings.xml`, e.g.:

```bash
./mvnw -s .mvn/settings.xml test
```


Once running:
- **Bonus UI**: http://localhost:8080/ — pick a seeded user from the dropdown, create/list/view
  requests, approve/decline/withdraw, and look up an application's approved discount.
- **H2 console**: http://localhost:8080/h2-console — JDBC URL `jdbc:h2:file:./data/rate-concession`,
  user `sa`, empty password.
- Data persists in `./data/rate-concession.mv.db` across restarts (it's a file-based DB, not
  in-memory); delete that file (or the `data/` directory) to reset to a freshly-seeded state.

## Seeded data

Flyway seeds (`V2__seed_data.sql`) the following so the whole workflow can be exercised
immediately:

| User id | Name | Role |
|---|---|---|
| `rm-1` | Dana Whitfield | RELATIONSHIP_MANAGER |
| `rm-2` | Marcus Ojo | RELATIONSHIP_MANAGER |
| `rev-1` | Priya Kapoor | REVIEWER |
| `rev-2` | Tom Bracewell | REVIEWER |

Mortgage applications `app-1001` .. `app-1005` (varied standard rates), plus one `PENDING`, one
`APPROVED` (with full history), and one `DECLINED` (with full history) example request.

## API summary

Full request/response bodies, validation rules, and a curl walkthrough are in
[docs/API.md](docs/API.md). Quick reference:

| Method & path | Who | Purpose |
|---|---|---|
| `POST /api/requests` | RM | Create a pricing exception request (idempotent via `Idempotency-Key`). |
| `GET /api/requests/{id}` | any authenticated user | Fetch one request. |
| `GET /api/requests/{id}/history` | any authenticated user | Audit trail for a request. |
| `GET /api/requests?applicationId=&status=` | any authenticated user | List/filter, newest-created first. |
| `POST /api/requests/{id}/decision` | reviewer | Approve or decline a pending request. |
| `POST /api/requests/{id}/withdraw` | any RM | Withdraw a pending request. |
| `POST /api/requests/reason-feedback` | RM | Optional, read-only mock feedback on a draft reason before submitting (never writes anything). See below and [docs/AI_REQUEST_QUALITY_ASSISTANT.md](docs/AI_REQUEST_QUALITY_ASSISTANT.md). |
| `GET /api/applications/{applicationId}/approved-discount` | any authenticated user | The currently-approved discount, if any, for the mortgage process to consume. |

### Request Quality Assistant (mock, optional)

Before submitting, an RM can get short, deterministic feedback on a draft reason/discount. This
is a mocked stand-in for an LLM (see [docs/AI_REQUEST_QUALITY_ASSISTANT.md](docs/AI_REQUEST_QUALITY_ASSISTANT.md))
— it never writes, decides, or blocks anything, and can be turned off with
`ai.reason-feedback.enabled=false` (returns `404` when disabled).

```bash
curl -s -X POST http://localhost:8080/api/requests/reason-feedback \
  -H "X-User-Id: rm-1" -H "X-User-Role: RELATIONSHIP_MANAGER" -H "Content-Type: application/json" \
  -d '{"applicationId":"app-1001","requestedDiscountBps":150,"reason":"good customer"}'
```

## Further documentation

| Document | Contents |
|---|---|
| [docs/DECISIONS.md](docs/DECISIONS.md) | The ambiguous/conflicting requirements from the brief, and the decision made on each, with reasoning. |
| [docs/API.md](docs/API.md) | Full API reference: headers, validation, error shapes, every endpoint, and a curl walkthrough. |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Domain model, state machine, concurrency handling, simulated auth, storage/migrations, diagrams, and source layout. |
| [docs/PRODUCTION_READINESS.md](docs/PRODUCTION_READINESS.md) | What would change before this went anywhere near production. |
| [docs/AI_DEVELOPMENT.md](docs/AI_DEVELOPMENT.md) | How AI-assisted/agentic development was actually used and validated while building this. |
| [docs/AI_REQUEST_QUALITY_ASSISTANT.md](docs/AI_REQUEST_QUALITY_ASSISTANT.md) | Bonus: a mocked "Request Quality Assistant" that gives RMs pre-submission feedback on a draft reason, plus the proposal for a real version, control/verification, and risks. |
