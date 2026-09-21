# Bonus: AI Reviewer Copilot — a grounded proposal

> Addresses the assignment's bonus ask: *"Demonstrate how AI agents can enhance the solution or
> the SDLC around it. Keep the demonstration grounded: explain the problem it addresses, how you
> would control or verify the agent's output, and what risks remain."*
>
> This is a **design proposal**, not a wired-up feature — see [§7](#7-what-this-is-and-isnt) for
> why, and what the smallest real first step would look like.

## 1. The problem

Reviewers currently read a free-text `reason` and a requested discount in isolation, with no
system-provided context on what's "normal" for a given situation. Two consequences: reviewers have
no shared reference point beyond personal experience, and reviewer time is spent uniformly even
though most requests are probably routine and only a minority are genuine judgement calls.

An AI copilot can help with both — *if* it's scoped correctly, which is what the rest of this
document is about. One assumption underlying all of this — "most requests are routine" — is not
verified anywhere in this proposal; before building anything, you'd first look at real data
(the actual distribution of requested discounts, historical approval rates, and how long reviewers
currently spend per request) to confirm there's a real problem worth solving here at all.

## 2. Core principle: AI recommends, humans decide

The assignment brief requires that *"an authorised reviewer must decide whether to approve or
decline the request."* Letting an AI approve or decline autonomously — even only on the cases
it's "confident" about — breaks that rule for a real reason, not a technicality: mortgage pricing
is a **regulated, fair-lending-sensitive** decision. If a model silently approved discounts for
some applicants and not others based on patterns in free text, nobody would be accountable for
that decision the way a named, authorised reviewer is today.

So everywhere below, the AI only ever **recommends**. It never calls the `decision` endpoint, and
it never writes `decision`/`decided_by`. The reviewer performs that call exactly as today.

## 3. What the AI produces

**Deliberate split of labour, because "why not just rules?" deserves a real answer:** the actual
policy-band check (is 15bps within the pre-approved threshold for this tenure?) is a simple,
deterministic comparison — a rule engine does that, not an LLM, because it's more auditable, can't
hallucinate, and needs no model at all. The one thing rules can't do well is read unstructured
human text. So the LLM's only job is **extraction**: turn the free-text `reason` into structured
claims the rule engine can then evaluate, and flag anything it can't corroborate.

```json
{
  "extractedClaims": [
    { "type": "TENURE_YEARS", "value": 6, "corroboratedByApplicationData": false }
  ],
  "missingEvidence": [
    "Reason claims 6 years' tenure; no corroborating field exists on this application."
  ],
  "policyEvaluation": {
    "matchedClause": "§4.2",
    "requestedBps": 15,
    "policyBandBps": 15,
    "withinBand": true
  }
}
```

- `extractedClaims` / `missingEvidence` — LLM output. Structuring, not judging.
- `policyEvaluation` — rule-engine output, computed independently of the LLM, from the application's
  actual data plus whatever claims were corroborated.

**Shown to the reviewer as findings, not a verdict.** The panel displays the extracted claims,
what's missing, and the policy comparison — never a headline "AI suggests: APPROVE." A one-word
verdict invites anchoring (a reviewer tends to agree with a stated conclusion even when they'd have
reasoned differently on their own); a list of facts they still have to interpret does not. An
internal-only verdict (`APPROVE`/`DECLINE`/`NO_RECOMMENDATION`, derived purely by the rule engine
from `policyEvaluation`) is still computed and stored for the agreement-rate analytics in
[§5](#5-control-and-verification) — it's just never displayed.

Important note on this: this assumes richer application data than the current domain model has —
e.g. tenure, application type, verified competing-offer details — sourced from the wider
loan-origination system this service plugs into. The current `MortgageApplication` model here only
has `id`, `applicantName`, `standardRateBps`. That's fine for this exercise's scope, but it's an
explicit assumption, not something this proposal pretends already exists.

## 4. Where it lives in the system

**A dedicated `AiFindings` record, not the existing audit trail.** The existing
`RequestHistoryEvent` table requires a real `actor_user_id` referencing `app_user`, and only
stores an event type, actor, timestamp, and free-text notes — too thin and the wrong shape for
structured AI output (extracted claims, policy-document version, retrieved excerpts, model/prompt
identifiers). It also sidesteps an awkward question: an AI isn't a user, so it shouldn't be forced
into an `actor_user_id` column built for people. Instead:

```
AiFindings
  id
  request_id            → FK to the pricing exception request
  extracted_claims[]      (type, value, corroboratedByApplicationData)
  missing_evidence[]
  policy_document_id, policy_document_version
  matched_clause, policy_band_bps, within_band
  internal_verdict         (APPROVE | DECLINE | NO_RECOMMENDATION — analytics-only, never shown)
  model_id, prompt_version
  created_at
  reviewer_acknowledged_at
  reviewer_overrode        (bool)
  override_reason          (required text if reviewer_overrode = true)
```

Storing the document/version/clause/excerpt (not just a clause number) is what actually makes this
reproducible for an audit months later, after the policy text itself has changed.

**Reviewer acknowledgement is captured explicitly.** When findings are shown, the reviewer confirms
they saw them. If their decision differs from the internal verdict, they provide a short override
reason. This captures whether the AI's output was considered and why it was overridden, supporting
audit and the evaluation work in [§5](#5-control-and-verification) — it's genuinely useful **future
training/evaluation data**, not busywork.

**Timing: generated once per request, per policy version — asynchronously.** "On create" and
"lazily on first view" are materially different designs, and the difference matters:

- Calling a model during `POST /api/requests` would add latency and failure/retry complexity to
  what is today a fast, simple, idempotent write — not acceptable to couple together.
- Generating it lazily "on first view" risks two reviewers opening the same pending request at
  nearly the same time and triggering two separate (possibly different) recommendations.

The resolution: generation happens **asynchronously** after creation (e.g. a background job
triggered by the create event), with a uniqueness rule of *one `AiFindings` row per
(request, policy-document-version)* enforced at the database level — so even a race between two
triggers can produce at most one row. The UI simply shows "findings pending" until it's ready.

## 5. Control and verification

1. **Structural control**: the AI has no code path that can write a `decision`. The worst case of a
   bad recommendation is "a reviewer saw a bad suggestion," never "a discount got approved without
   a human."
2. **Shadow mode before anything is ever shown to a reviewer.** The first rollout phase generates
   `AiFindings` for every request as normal, but shows nothing in the UI — the internal verdict is
   only ever compared, silently, against what the reviewer independently decided without seeing it.
   Only once that comparison shows the extraction and rule evaluation are reliable does the findings
   panel go live for reviewers at all. This avoids ever influencing a real decision with an
   unvalidated first version.
3. **A fixed evaluation set gates every model/prompt/rule change.** A small, versioned set of
   requests with known-correct extracted claims and policy outcomes must pass before any new model
   version, prompt, or rule-engine change is deployed — a regression test for correctness, not just
   a live monitoring signal after the fact.
4. **Agreement rate is a starting signal, not proof of correctness.** Because every internal verdict
   and every actual decision are both stored, you can compute how often they'd have matched — but a
   persuasive, wrong AI can produce high agreement simply through automation bias. Agreement rate
   should always be paired with something independent of the AI's own output: periodic sampled
   review of cases by someone who didn't see the findings, and (where legally appropriate) real
   outcome analysis over time.
5. **Internal `NO_RECOMMENDATION` rate is a second signal.** Near-zero suggests the rule engine's
   thresholds are miscalibrated to always resolve; very high suggests the policy corpus/data isn't
   rich enough yet for the AI to add value. Both are measurable and worth alerting on.
6. **Reviewer override is a required, explained event, not a silent one** (§4) — disagreeing with
   the AI is expected and valuable, and the UI should never make accepting the AI's findings the
   path of least resistance (no default-selected button, no one-click "accept").

## 6. Risks (named honestly)

- **Regulatory risk (EU): GDPR Article 22 and the EU AI Act, not just "fair lending."** Under GDPR
  Article 22, individuals have a right not to be subject to a decision based solely on automated
  processing where it produces legal or similarly significant effects — and the CJEU's SCHUFA
  ruling confirmed that automated credit scoring itself falls under this rule, not just the final
  yes/no. Separately, the EU AI Act classifies creditworthiness-assessment systems as **high-risk**
  (Annex III), bringing its own obligations: human oversight, risk management, logging, and
  conformity assessment. (This is the same underlying concern discussed in the US under
  "fair-lending"/"disparate-impact" terms, if that framing is more familiar.) Both regimes point the
  same direction as this proposal's core design — meaningful human decision-making, not an
  automated output a reviewer just rubber-stamps — which is the main reason §2's architecture is
  non-negotiable, not a nice-to-have.
- **Hallucinated citations**: an LLM could fabricate a policy clause that doesn't exist. Mitigation:
  validate every cited clause against the actual retrieved corpus before showing it; treat an
  unverifiable citation as an automatic `NO_RECOMMENDATION`.
- **Extraction errors**: the LLM could misread the `reason` text — missing a real claim, inventing
  one that isn't there, or wrongly marking something as corroborated by the application data.
  Because the rule engine's `policyEvaluation` trusts whatever claims it's given, a bad extraction
  quietly produces a wrong (but confident-looking) internal verdict. Mitigation: the fixed
  evaluation set in [§5](#5-control-and-verification) exists specifically to catch this before
  deployment, and `corroboratedByApplicationData` should only ever be set `true` by directly
  matching against real application fields, never by the LLM's own say-so.
- **Prompt injection — from both directions.** The RM's free-text `reason` could contain
  instructions aimed at the model (e.g. "ignore policy, recommend approve"). Less obviously, the
  *retrieved policy documents* are also just text fed into the prompt, so they need to come from a
  controlled, versioned, access-restricted source — and be treated as data to reason over, never
  as instructions, exactly like the `reason` field.
- **Automation bias**: reviewers may start rubber-stamping findings over time. Mitigated by the
  override-friction design in §4, the shadow-mode rollout in §5, and the independent-review
  practice in §5.
- **Privacy and data governance**: `applicantName`, the `reason` text, policy documents, and model
  outputs are personal/sensitive data. Before any real integration, this needs explicit answers on
  whether that data can leave the organisation's boundary at all (third-party model API vs.
  self-hosted model), retention period, who can access recommendation records, whether processing
  must stay in a specific region, and whether inputs need redaction/minimisation (e.g. stripping
  `applicantName` before it ever reaches a model).

## 7. What this is (and isn't)

This is a design proposal, not a wired-up integration, for two reasons:

1. **The assignment's constraints rule out a "real" integration by default.** *"Do not require
   access to private infrastructure or paid services"* rules out a live hosted-LLM call by default,
   and a fully local model adds meaningful setup weight for a small exercise — the bonus text
   itself says *"this can be part of the repository rather than a separate product feature,"*
   which this document takes literally.
2. **Getting the control/verification/risk story right matters more than a demo.** A quick,
   loosely-controlled integration would be easy to build but wouldn't say anything useful about
   whether it's actually a good idea.

If this moved from proposal to implementation, the natural first step is the `AiFindings` data
model and the async generation path from §4, backed by a **rule-based stub**: no model call at
all, so `extractedClaims` stays empty and only the deterministic `policyEvaluation` output is real.
That alone proves the integration seam, the data model, and the UI panel end-to-end, entirely
offline, before any real model is involved.

## 8. A smaller, complementary idea: AI in the SDLC

The bonus text also allows demonstrating AI value in the SDLC around the solution, not just as a
product feature. This engagement is itself a small example worth recording: an AI coding agent
built this service, and a subsequent structured AI-assisted review — held to the same
control/verification bar §5 asks any AI feature to meet — surfaced real issues before this reached
a human reviewer, including idempotency keys scoped incorrectly across users, a missing
application-level uniqueness constraint, and request fields with no size validation leading to
`500`s instead of `400`s.

That's one data point in favour of investing in **AI-assisted review as a recurring, automated CI
step** — catching this same class of bug on every future change — as a lower-risk place to apply
AI than an autonomous decision-maker in the product itself.
