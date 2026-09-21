# Production-readiness notes

What would change before this went anywhere near production:
- **Real authentication**: replace the `X-User-Id`/`X-User-Role` header stand-in with OAuth2/JWT
  (e.g. Spring Security's resource-server support), with roles/claims from a real identity
  provider, not a seeded table.
- **Rate limiting** on write endpoints (create/decision/withdraw) to prevent abuse.
- **Structured logging, tracing, and monitoring**: correlation/request IDs, JSON logs, metrics
  (request counts/latencies/error rates per endpoint), and alerting on elevated 409/5xx rates.
- **Migration review process**: Flyway migrations should go through the same PR review as code,
  with a policy against editing already-applied migrations in shared environments.
- **Secrets management**: DB credentials, etc. via a vault/secret manager, not plaintext config.
- **Input validation hardening**: stricter length limits, allow-listing of characters in free-text
  fields, and reconsidering the 500bps upper bound with actual business/risk sign-off.
- **OWASP considerations**: this exercise doesn't face untrusted browsers directly, but a real
  deployment would need CSRF/consider CORS policy, output encoding review, dependency scanning,
  and disabling the H2 console entirely.
- **A real database** (e.g. PostgreSQL) with connection pooling tuned for the expected load,
  proper backups, and replication.
- **Retention/archival policy**: this exercise keeps all history forever; production would need an
  explicit retention window and an archival strategy for old requests/history.
- **Pagination** on `GET /api/requests` once volumes grow beyond a page. Note: the list is already
  deterministically ordered (newest-created first) even without pagination, so this is purely about
  bounding response size at scale, not about the ordering itself (see [API.md](./API.md)).
- **API versioning** (e.g. `/api/v1/...`) so the contract can evolve without breaking consumers.
- **Idempotency record retention**: currently kept forever; production should expire old
  `IdempotencyRecord` rows after a bounded retry window.
