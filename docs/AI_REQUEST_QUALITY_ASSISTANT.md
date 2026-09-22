# Bonus: Request Quality Assistant — a grounded proposal

> Addresses the assignment's bonus ask: *"Demonstrate how AI agents can enhance the solution or
> the SDLC around it. Keep the demonstration grounded: explain the problem it addresses, how you
> would control or verify the agent's output, and what risks remain."*
>
> Part of this is implemented as a small offline mock (see [§4](#4-what-is-implemented-vs-mocked));
> the rest is a design proposal.

## 1. The problem

A reviewer can only decide well if the RM's free-text `reason` actually explains the case. A vague
reason ("good customer, please approve") forces a decline, or a round of back-and-forth to get the
missing detail, and different reviewers end up asking for different things given the same gap.
The cheapest place to fix this is before the request is even submitted, by giving the RM feedback
on their own draft.

This rests on an assumption — that vague reasons are actually causing declines and back-and-forth
today — which isn't verified anywhere in this proposal. Before building more than the mock below,
you'd check real data: how often declines cite a vague or incomplete reason, how often reviewers
ask a follow-up question, and how review time varies with reason length/detail. If that data
doesn't show a real pattern, this isn't worth building further.

## 2. Core principle: feedback only, the RM writes, the reviewer decides

The assistant never touches the regulated decision at all — it sits one step earlier, on the RM's
own draft, before anything is submitted. That leads to a small set of hard principles, all
enforced in the code, not just documented:

- The assistant **never writes, rewrites, or stores** the `reason`. It only returns feedback text;
  the RM reads it and edits their own draft if they choose to.
- It is **optional and non-blocking** — nothing about request creation changes, and the endpoint
  can be disabled entirely without affecting `POST /api/requests`.
- It has **no access to decisions, reviewer actions, or the approved-discount endpoint** — it only
  ever sees a draft `reason` and requested discount.
- The **authorised-reviewer-decides rule is completely untouched**: the reviewer still makes every
  approve/decline call, on the request the RM actually submitted, exactly as today.

## 3. What it produces

The response is a short, structured list of feedback items — never a rewritten reason, never a
verdict:

```json
{
  "feedback": [
    { "type": "CLARITY", "message": "Reason is very brief; explain why this case needs an exception." },
    { "type": "MISSING_INFO", "message": "Consider stating how long the customer has been with the bank." },
    { "type": "REVIEWER_QUESTION", "message": "This discount is high; explain what makes the case exceptional." }
  ],
  "provider": "mock",
  "promptVersion": "mock-v1"
}
```

`type` is one of `MISSING_INFO`, `CLARITY`, or `REVIEWER_QUESTION`. `provider`/`promptVersion`
identify which implementation and version produced the feedback, so behaviour is reproducible and
comparable across changes (see [§5](#5-control-and-verification)).

## 4. Where it lives in the flow

The assistant only ever sits at the create step, called before `POST /api/requests` — the RM drafts
a reason, optionally asks for feedback on it, edits it themselves, then submits through the normal
endpoint exactly as if the assistant didn't exist. It never sees a request that already exists, a
decision, or the approved-discount endpoint.

That placement is deliberate: keeping the assistant entirely before submission means it structurally
cannot influence the regulated approve/decline decision.

## 5. What is implemented vs. mocked

Implemented, in this repository, as a small offline mock:

- A `ReasonFeedbackProvider` interface — one method taking a draft (`reason`,
  `requestedDiscountBps`, optional `applicationId`) and returning a list of feedback items. This is
  the integration seam: a real `LlmReasonFeedbackProvider` would implement the same interface and
  plug in without changing the endpoint, validation, or response shape.
- A `MockReasonFeedbackProvider` — **fixed-rule search method that imitate the shape of what an
  LLM would return**. It flags a very short reason, a competing offer mentioned without a
  rate, no mention of customer tenure, and a requested discount above a documented threshold.
- A read-only `POST /api/requests/reason-feedback` endpoint, restricted to relationship managers,
  using the same user-identification headers and the same `reason`/discount validation limits as
  `POST /api/requests`. It performs no database writes.
- A config flag, `ai.reason-feedback.enabled` (default `true`, since the mock needs no external
  service). When disabled, the endpoint returns `404`.

A natural production evolution of the LLM provider would ground feedback in the bank's
actual exception-approval policy documents (retrieval over a versioned policy corpus), so a
message like "no mention of tenure" becomes something more specific, e.g. "requests citing a competing
offer typically need the offer's rate and lender: see policy §4.2," and stays current as policy
text changes instead of being hardcoded into fixed rules.

Not implemented: any real model call. building one is out of scope for this exercise (see [§7](#7-what-this-is-and-isnt)), and would
need real answers to the privacy questions in [§6](#6-risks).

## 6. Control and verification

1. **Structural control**: the interface has no method that can create, modify, or decide a
   request. The worst case of bad output is "the RM saw unhelpful feedback," never "a decision or
   a stored reason changed."
2. **A fixed, versioned evaluation set gates every new prompt/model version.** A small set of
   example draft reasons with expected feedback must pass before any new
   LLM provider prompt or model version is released — a regression test for
   quality, not just a monitoring signal after the fact. The mock's own unit tests play this role
   today, one per rule. If retrieval over policy documents is ever added (§5), the same
   evaluation set would also need to cover retrieval quality — that the right clause is retrieved
   and cited — not just feedback wording.
3. **Effect should be measured over time, not assumed.** The metrics that would confirm this is
   actually helping — decline rate for missing-information reasons, follow-up questions from
   reviewers, time from creation to decision — all need recording whether a request's reason was
   checked before submission. That flag doesn't exist yet; it's future work, not implemented here,
   and is a prerequisite before drawing any conclusion about impact.
4. **`promptVersion` makes every response attributable and reproducible** — a specific piece of
   feedback can always be traced back to the exact provider version that produced it.

## 7. Risks

- **Wrong or irrelevant feedback.** Low impact by design: the RM reads it, decides whether it
  applies, and edits (or ignores) their own reason. Nothing downstream depends on the feedback
  being correct.
- **Reasons written to satisfy the assistant rather than to be honest.** An RM could learn to add
  keywords the rules look for without the underlying facts changing. This is mitigated by the
  assistant never rewriting text itself, and by the reviewer judging the actual content of the
  reason, not whether it "passed" the assistant — the assistant has no way to mark a request as
  checked or pre-approved.
- **Prompt injection in the reason.** A draft reason could contain text aimed at a future LLM
  provider. Low impact here because the output is only ever shown back to the RM who wrote the
  draft, and is never acted on automatically or read by anyone else — there's no downstream system
  or reviewer decision an injected instruction could reach.
- **Stale or wrong retrieval, if policy-document lookup is ever added (§5).** Retrieval could cite
  an outdated policy version or the wrong clause, and the retrieved documents become a second
  prompt input alongside the reason — one that also needs to come from a controlled, versioned,
  access-restricted source and be treated as data, not instructions, for the same reason the
  `reason` field is.
- **Privacy of customer details in the reason.** A draft reason can contain personal/sensitive
  applicant details before a real model is ever involved. A real provider would need explicit
  answers on where the model runs, what data leaves the organisation's boundary, retention period,
  and whether the draft needs redaction (e.g. stripping names) before it reaches a model.
- **Over-reliance by RMs.** An RM might treat "no feedback" as implicit approval of their reason's
  content or quality, rather than as "the rules found nothing." The response should stay
  framed as feedback on a draft, not a pass/fail check, and this is worth watching for in practice.

## 8. What this is (and isn't)

This is a small, offline demonstration of the integration seam — the interface, a deterministic
mock behind it, and an endpoint wired to the normal create flow — not a real model integration.
Real model use would need its own service, its own privacy/retention answers (§6), and by default
requires no paid or private service in this repository, matching the assignment's constraint.
