# API reference

Base path: `/api`. All endpoints require the headers below; all error responses share one JSON
shape. See [../README.md](../README.md) for how to start the service, and
[DECISIONS.md](./DECISIONS.md) for the reasoning behind the rules referenced here.

## Common headers

| Header | Required on | Notes |
|---|---|---|
| `X-User-Id` | every `/api/**` request | Must match a seeded `AppUser` id. |
| `X-User-Role` | every `/api/**` request | Must equal that user's actual seeded role. |
| `Idempotency-Key` | `POST /api/requests` only | Any client-chosen string; reused per logical retry. |

Missing/invalid `X-User-Id`/`X-User-Role`, or a role mismatch, or an unknown user id → **401**.
Missing `Idempotency-Key` on create → **400**.

## Error shape

```json
{
  "timestamp": "2025-01-06T09:15:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Pricing exception request not found: ...",
  "path": "/api/requests/...",
  "fieldErrors": null
}
```

`fieldErrors` is populated (list of `{field, message}`) only for Bean Validation failures on
create.

## Endpoints

### `POST /api/requests` — create (RM only)

Request:
```json
{ "applicationId": "app-1001", "requestedDiscountBps": 25, "reason": "Competing offer from another lender" }
```
- `requestedDiscountBps`: integer, `1..500`.
- `reason`: non-blank, max 2000 characters.
- `applicationId`: must reference an existing `MortgageApplication` (404 if not); max 64 characters.
- `Idempotency-Key` header: required, max 255 characters.
- Rejected with `409` if the application already has another `PENDING` request open (see
  [DECISIONS.md](./DECISIONS.md), "one open request per application").

Response `201`:
```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "applicationId": "app-1001",
  "requestedDiscountBps": 25,
  "reason": "Competing offer from another lender",
  "status": "PENDING",
  "createdByUserId": "rm-1",
  "createdAt": "2025-01-06T09:15:00Z",
  "decidedByUserId": null,
  "decidedAt": null,
  "decisionReason": null,
  "version": 0
}
```
- Idempotency: repeating the same `Idempotency-Key` with the same body replays the original `201`
  response (no new row created). Repeating it with a **different** body → `409`.
- Errors: `400` (validation / missing Idempotency-Key), `401`, `403` (caller isn't an RM), `404`
  (unknown `applicationId`), `409` (Idempotency-Key reused with a different body).

### `GET /api/requests/{id}` — fetch one (any authenticated user)

- Returns the same shape as create's response. `404` if not found.

### `GET /api/requests/{id}/history` — audit trail (any authenticated user)

Returns an ordered array of history events:
```json
[
  { "id": 1, "requestId": "...", "eventType": "CREATED", "actorUserId": "rm-1", "timestamp": "...", "notes": null },
  { "id": 2, "requestId": "...", "eventType": "APPROVED", "actorUserId": "rev-1", "timestamp": "...", "notes": "Approved given excellent credit history." }
]
```
- `404` if the request doesn't exist.

### `GET /api/requests?applicationId=...&status=...` — list/filter (any authenticated user)

- Both query params optional and combinable. `status` is one of `PENDING`, `APPROVED`, `DECLINED`,
  `WITHDRAWN`. Results are ordered **newest-created first** (`createdAt DESC`) — deterministic, so
  API/UI behaviour is predictable even without pagination.

### `POST /api/requests/{id}/decision` — approve/decline (reviewer only)

Request:
```json
{ "decision": "APPROVE", "reason": "Approved given excellent credit history." }
```
- `decision` is `APPROVE` or `DECLINE`. `reason` is required for `DECLINE`, optional for `APPROVE`.
- Response `200`: updated request representation.
- Errors: `400` (missing reason for DECLINE), `403` (caller isn't a reviewer), `404` (not found),
  `409` (request is no longer `PENDING`).

### `POST /api/requests/{id}/withdraw` — withdraw (any relationship manager)

- Request body optional: `{ "reason": "Client changed their mind" }`.
- Response `200`: updated request representation.
- Errors: `403` (caller is a reviewer), `404` (not found), `409` (request is no longer `PENDING`).

Any authenticated RM may withdraw a pending request, not only its creator — this supports
continuity (e.g. covering for an absent colleague) and mirrors how any REVIEWER may decide on a
request regardless of who it was assigned to. The actor is still recorded on the `WITHDRAWN`
history event, so accountability is preserved even though the ownership check is not enforced.
See [DECISIONS.md](./DECISIONS.md) for the full reasoning.

### `GET /api/applications/{applicationId}/approved-discount` — for the mortgage process (any authenticated user)

If an approval exists:
```json
{ "applicationId": "app-1002", "discountBps": 15, "hasApprovedDiscount": true, "decidedByUserId": "rev-1", "decidedAt": "2025-01-04T10:30:00Z", "requestId": "22222222-2222-2222-2222-222222222222" }
```
If none:
```json
{ "applicationId": "app-1003", "discountBps": null, "hasApprovedDiscount": false, "decidedByUserId": null, "decidedAt": null, "requestId": null }
```
- `404` only if `applicationId` itself doesn't exist (a "no approval yet" application is a normal
  `200`, not an error).

## Example curl session

```bash
# Create (as RM rm-1)
curl -s -X POST http://localhost:8080/api/requests \
  -H "X-User-Id: rm-1" -H "X-User-Role: RELATIONSHIP_MANAGER" \
  -H "Idempotency-Key: $(uuidgen)" -H "Content-Type: application/json" \
  -d '{"applicationId":"app-1004","requestedDiscountBps":20,"reason":"Loyalty discount"}'

# Approve (as reviewer rev-1) - substitute the id from the create response
curl -s -X POST http://localhost:8080/api/requests/<id>/decision \
  -H "X-User-Id: rev-1" -H "X-User-Role: REVIEWER" -H "Content-Type: application/json" \
  -d '{"decision":"APPROVE","reason":"Looks good"}'

# Check the approved discount
curl -s http://localhost:8080/api/applications/app-1004/approved-discount \
  -H "X-User-Id: rm-1" -H "X-User-Role: RELATIONSHIP_MANAGER"
```
