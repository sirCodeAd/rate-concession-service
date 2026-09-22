# Decisions on ambiguous / conflicting requirements

The brief explicitly called out several points as needing a decision, plus a stakeholder conflict
to resolve. All of the following are implemented exactly as described — they are not open
questions.

- **Editing after submission vs. "approvals apply to the exact request reviewed":** resolved in
  favour of immutability. `PricingExceptionRequest` has **no update/edit endpoint**. Once created,
  `applicationId`, `requestedDiscountBps` and `reason` never change. `PENDING` can only transition
  to `APPROVED`, `DECLINED` (via a reviewer) or `WITHDRAWN` (via any RM); all three are terminal.
  If an RM wants different terms, they withdraw the pending request and create a new one. This
  guarantees a reviewer's decision always applies to precisely the request they read.
- **Who may withdraw a pending request:** any authenticated RM, not only its creator. This
  mirrors the reviewer side of the workflow, where any `REVIEWER` may decide on a request
  regardless of who it was assigned to — there is no concept of "my" review queue vs. "someone
  else's". Restricting withdrawal to the original creator would break continuity (e.g. an RM on
  leave with a request that needs pulling back), and the risk is low: withdrawal only affects a
  `PENDING` request (nothing already decided), and the acting user is still recorded on the
  `WITHDRAWN` history event, so accountability is preserved.
- **Idempotency key:** the client supplies an opaque `Idempotency-Key` header (any client-generated
  string, e.g. a UUID) on `POST /api/requests`. It is *not* derived from the payload, so the same
  logical retry (same key) can be distinguished from a genuinely new request that happens to look
  similar. **Scoped per caller**: the primary key is `(managerId, idempotencyKey)`, not the key
  alone — two different relationship managers may reuse the same key value independently without
  colliding or seeing each other's stored response. The stored request body hash detects
  (and rejects with `409`) a key being reused for a different payload within the same manager's
  scope — a sign of a client bug rather than a legitimate retry.
- **One open request per application:** at most one `PENDING` request is allowed per application at
  a time, enforced both at the application layer (a friendly `409` pre-check) and at the database
  layer (a unique index on a computed column that's non-null only for `PENDING` rows — see
  [ARCHITECTURE.md](./ARCHITECTURE.md) — which is what actually wins any true concurrent-create
  race). Once a request reaches a terminal status, a new request may be submitted for the same
  application (e.g. renegotiating terms later in the application's life).
  `GET /api/applications/{id}/approved-discount` follows a **supersession policy**: the most
  recently approved request is authoritative; older approved requests remain untouched in the
  audit trail but are no longer "current".
- **Authorization model:** roles are `RELATIONSHIP_MANAGER` and `REVIEWER`, seeded as `AppUser`
  rows. Real OAuth2/JWT auth is out of scope for this exercise; instead every request carries
  simulated identity via `X-User-Id` / `X-User-Role` headers, validated against the seeded users
  (see [ARCHITECTURE.md](./ARCHITECTURE.md) for why, and
  [PRODUCTION_READINESS.md](./PRODUCTION_READINESS.md) for what a production version would use
  instead).
- **Concurrent decisions:** exactly one of two racing decisions (or a decision racing a
  withdrawal) can win. Implemented as a single conditional atomic
  `UPDATE ... WHERE status = 'PENDING'` (see [ARCHITECTURE.md](./ARCHITECTURE.md)). A dedicated
  concurrency test exercises this with two threads and a `CountDownLatch`.
- **Data retention / scale:** out of scope for this exercise — no expiry, archival, or pagination
  is implemented; see [PRODUCTION_READINESS.md](./PRODUCTION_READINESS.md) for what would be added
  for production scale. The list endpoint is, however, already deterministically ordered
  (newest-created first, via `ORDER BY created_at DESC`) so behaviour is predictable even without
  pagination (see [API.md](./API.md)).
- **Storage:** H2, file-based (`./data/rate-concession`), so state survives restarts during local
  exploration, with Flyway-managed schema and seed data (see [ARCHITECTURE.md](./ARCHITECTURE.md)).
- **Approved-discount response shape:** returns just the current approved discount and its
  provenance (who/when/which request), not the full history — the mortgage process doesn't need
  the review history, and can call `GET /api/requests/{id}/history` separately if it ever does.
  "No approved discount yet" is a normal `200` response, not an error, since it's an expected
  steady state for a request that's pending, declined, or never made.
