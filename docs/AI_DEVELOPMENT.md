# Agentic development

This solution was built end-to-end with an AI coding agent (GitHub Copilot CLI), using a
spec-driven, plan-first workflow rather than open-ended "vibe coding." Summarised below so another
engineer can judge how much confidence to place in this codebase.

## 1. Plan before code

Before any implementation, the agent read the assignment brief in full and produced a written set
of **clarifying decisions** for every deliberately-ambiguous point (RM-edit-vs-immutability, the
idempotency-key strategy, auth model, concurrency handling, storage choice, retention, the
discount-retrieval shape, the state machine) — signed off *before* any code was written.
[DECISIONS.md](./DECISIONS.md) is the direct, unedited output of that step.

## 2. Decompose into small, verifiable tasks

The build was specified as discrete, independently checkable units — domain model, schema/seed
migrations, idempotency, the concurrency-safe decision/withdrawal transition, simulated auth, the
REST API/validation, tests, and the bonus UI — each mapping to a section of
[ARCHITECTURE.md](./ARCHITECTURE.md) / [API.md](./API.md), so every piece could be reviewed on its
own terms rather than as one opaque blob.

## 3. Validate, don't trust

An agent's own "tests pass" claim was never accepted at face value. For every change: run the full
suite independently and read the real output, then manually exercise the running app end-to-end
with `curl` (create, idempotent replay, RBAC rejection, approval, conflicts, history, discount
lookup) to confirm actual HTTP behaviour that tests alone might miss — only then was a change
considered done.

This caught two real problems, not hypothetical ones:
- A partial revert left two call sites referencing already-renamed repository methods — a silent
  compilation break, caught by re-running the build, not by reading the diff.
- A blank `applicationId` collapsed a URL to a double slash, which Spring routed as an unmatched
  static resource (`NoResourceFoundException`) instead of reaching the controller — surfacing as a
  `500` instead of the intended `400`. Found by reproducing the exact symptom with `curl`, fixed
  with a client-side guard plus a proper server-side exception mapping, and covered by a
  regression test.

## 4. Iterate on human review, not just automated tests

After the initial build, several rounds of structured human review followed the same discipline:
re-read the actual implicated code before accepting or pushing back on a finding, and for every
accepted finding, ship the fix, its regression test, a full re-run, and a live re-verification
before closing it. This is how the following were found and fixed — none caught by the original
test suite, because each is an *absence* of a safeguard rather than a broken one:
- idempotency keys scoped globally instead of per-authenticated-caller (a cross-user data-leakage
  risk),
- no constraint stopping multiple simultaneous open requests per application,
- request DTOs missing size validation matching the database's own column limits (oversized input
  became a `500` instead of a `400`).

## What this demonstrates, and its limits

Plan first, decompose into checkable units, never trust a self-report, treat human review as
ongoing rather than a one-off gate — this is the same control philosophy proposed for a
hypothetical in-product AI feature in [AI_REVIEWER_COPILOT.md](./AI_REVIEWER_COPILOT.md): AI
accelerates the work, but a human stays accountable, and every acceptance is backed by something
checkable rather than the agent's own narrative. The limit is symmetrical: this only works as well
as the review applied to it — a passing-tests claim is only meaningfully validated because a human
actually re-ran them and exercised the running system, rather than taking it on faith.
