# Agentic development

This solution was built end-to-end using an AI coding agent (GitHub Copilot CLI), following a
deliberate spec-driven, plan-first workflow rather than open-ended "vibe coding." The process, and
how its output was actually validated (not just trusted), is described below so another engineer
can judge how much confidence to place in this codebase.

## Plan before code

Before any implementation, the agent read both source documents in full (the assignment PDF and
this repo's structured implementation prompt) and produced a written set of **clarifying
decisions** on every deliberately-ambiguous point in the brief — the RM-edit-vs-immutability
conflict, the idempotency-key strategy, the auth model, concurrency handling, storage choice,
retention, the shape of the discount-retrieval response, and the state machine. These were
presented for explicit sign-off *before* a single line of application code was written.
[DECISIONS.md](./DECISIONS.md) is the direct output of that step — it isn't retrospective
documentation, it's the actual plan that was agreed first.

## Decompose into small, verifiable tasks

The build-out itself was specified as a set of discrete, independently checkable units rather than
one large undifferentiated request: domain model and state machine, database schema and seed
migrations, the idempotency mechanism, the concurrency-safe decision/withdrawal transition, the
simulated authentication interceptor, the REST API and DTO validation, the test suite, and the
bonus UI. Each unit maps to a section of [ARCHITECTURE.md](./ARCHITECTURE.md) and
[API.md](./API.md), which made it straightforward to check that every planned piece actually got
built and to review each one on its own terms rather than as one opaque blob of generated code.

## Validate, don't trust — every task, every change

The agent's own report that "tests pass" was never treated as sufficient by itself. For the
initial build-out, and for every change made afterwards, the same validation loop was applied:

1. Run the full automated test suite independently (`./mvnw test`) and read the actual output —
   not just accept a claimed pass count.
2. Boot the packaged jar and manually exercise the real HTTP API end-to-end with `curl` — create,
   idempotent replay, RBAC rejection, approval, conflict handling, history, and the
   approved-discount lookup — to confirm behaviour that unit/integration tests alone might not
   surface (e.g. actual HTTP status codes, real serialised JSON shapes, an actually-running
   server).
3. Only then consider a change complete.

This loop caught real problems, not hypothetical ones. Two concrete examples:

- **A self-introduced regression during a revert.** A partial revert of an in-progress change left
  two call sites referencing repository methods that had already been renamed, which silently
  broke compilation. It wasn't caught by re-reading the diff — it was caught by re-running the
  build, which is exactly why "run it, don't just read it" is step 1 above rather than optional.
- **A routing edge case that testing alone hadn't reached.** A blank `applicationId` in a UI-built
  URL collapsed to a double slash, which Spring resolved as an unmatched static-resource route
  (`NoResourceFoundException`) rather than reaching the controller at all — surfacing as a
  confusing `500` instead of the intended `400`. This was found by manually reproducing the exact
  browser-observed symptom via `curl`, not by inspection, and was fixed with both a client-side
  guard and a proper server-side exception mapping, then covered by a new regression test.

## Iterating on human review, not just automated tests

After the initial build, this codebase went through several rounds of structured human code
review. Each finding was handled the same disciplined way: re-read the actual implicated code
first (never took a review finding at face value without checking it against the real source),
reasoned about whether to agree or push back, and — for every finding that was accepted — the fix,
its regression test, a full test-suite re-run, and a live manual re-verification were all done
before considering the finding closed. Examples of what this caught: idempotency keys scoped
globally instead of per-authenticated-caller (a real cross-user data-leakage risk), no constraint
preventing multiple simultaneous open requests per application (an undocumented, untested implicit
business rule), and request DTOs missing size validation that matched the database's own column
limits (turning oversized-but-otherwise-valid input into `500`s instead of `400`s). None of these
were caught by the original automated test suite — they required a human deliberately looking for
the *absence* of a safeguard, which is a class of gap AI-generated code (and, to be fair, a lot of
human-written code) is prone to leaving behind.

## What this demonstrates, and its limits

The pattern above — plan first, decompose into checkable units, never trust a self-report, and
treat human review as an ongoing input rather than a one-off gate — is the same control philosophy
proposed for a hypothetical AI feature *within* the product itself in
[AI_REVIEWER_COPILOT.md](./AI_REVIEWER_COPILOT.md): AI accelerates the work, but a human stays
accountable for verifying it, and every acceptance is backed by something checkable (a passing
test, a reproduced HTTP response, a re-read diff) rather than the agent's own narrative about its
work. The obvious limit is that this only works as well as the review applied to it — an agent
narrating "all tests pass" is only meaningfully validated because a human (or a second,
independent check) actually re-ran them and manually exercised the running system rather than
taking that claim on faith.
