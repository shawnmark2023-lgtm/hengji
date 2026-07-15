---
name: build-local-first-finance-app
description: Plan, implement, and audit privacy-first cross-platform expense, asset-value, second-hand pricing, authorized import, and explainable financial insight applications. Use when building or reviewing finance apps that must separate local data, sandbox connectors, licensed live sources, deferred accounts, and production release gates across mobile and desktop.
---

# Build a Local-First Finance App

Build an evidence-backed product plan first, convert it into an auditable delivery list, then implement against explicit privacy and quality gates. Keep source truth, financial math, and platform limitations visible throughout.

## Route the work

1. Inspect the repository, available skills, toolchain, dirty worktree, and existing decisions.
2. Search current official documentation before choosing frameworks, platform permissions, APIs, security controls, or release rules.
3. Produce the product plan and numbered delivery list before implementation.
4. Use multiple agents only when the user explicitly requests delegation. Give each agent exclusive module ownership and one integration contract.
5. Implement vertical slices that connect UI, repository, domain calculation, and tests. Do not leave a polished UI backed by unrelated static totals.
6. Run local builds, service tests, dependency audits, and a real UI flow before claiming completion.
7. Record verified, deferred, externally blocked, and prohibited work separately.

Read [architecture-checklist.md](references/architecture-checklist.md) before selecting modules or technologies. Read [privacy-connectors.md](references/privacy-connectors.md) before designing imports, OAuth, model calls, or second-hand price sources. Read [quality-gates.md](references/quality-gates.md) before testing or release handoff.

## Research and plan

- Treat recommendations, versions, policies, permissions, and API availability as time-sensitive. Verify them from primary sources.
- Research user pain as well as vendor capabilities. Translate findings into problem statements, exclusions, and measurable outcomes.
- State what "Apple-quality" means operationally: coherent hierarchy, predictable behavior, accessibility, privacy clarity, responsive layouts, recovery, and release discipline.
- Separate four scopes in the plan: runnable prototype, Beta, production integration, and store release.
- Create a list with stable IDs, priorities, acceptance criteria, dependencies, and status. Never mark an item complete because a placeholder or roadmap exists.

## Preserve financial correctness

- Store money as integer minor units plus currency. Never use binary floating point for stored or calculated money.
- Make transaction direction explicit with kinds such as expense, income, and refund; do not overload negative values ambiguously.
- Define ownership cost as purchase cost plus eligible maintenance and fees. Define residual value provenance before calculating net cost.
- Specify zero-day, zero-use, refund, future-event, mixed-currency, overflow, negative-net-cost, and stale-quote behavior.
- Use the same domain calculators for UI totals, reports, and insights.

## Build local-first data boundaries

- Classify transaction and asset history as sensitive financial data even when names, phone numbers, and government identifiers are absent.
- Default to local storage and no account. Make every network path discoverable and separately switchable.
- Put repositories between UI and storage. Require atomic imports, stable deduplication fingerprints, soft deletion or undo, schema versioning, export, and migration tests.
- Call an in-memory build a prototype. Do not imply restart persistence until a durable database is implemented and tested.
- Add account verification, credential vaults, encrypted sync, conflict handling, recovery, and session revocation only at the production stage defined by the plan.

## Design imports and live data honestly

- Implement the connector ladder in order: manual/file import, share/OCR with confirmation, approved official OAuth or entitled API, licensed aggregator.
- Forbid password capture, cookie theft, private API reverse engineering, and scraping that violates platform terms.
- Treat merchant-side bill APIs as merchant data, not as a consumer-wide ledger.
- Implement sandbox connectors with production fail-closed behavior. Use PKCE, one-time expiring state, exact redirect URI matching, narrow scopes, bounded payloads, and a token-vault interface.
- Tag every quote with provenance, collection time, currency, condition, specification, shipping, confidence, and live/non-live status. A demo quote must never expose itself as live.

## Make intelligence explainable

- Start with deterministic rules: category share, trend, budget pace, merchant concentration, duplicate charges, recurring charges, low-use assets, and sell candidates.
- Return structured evidence, thresholds, estimated impact, confidence, actionability, and a user action for every insight.
- Rank and deduplicate deterministically. Let users adopt, snooze, mute, or dismiss suggestions locally.
- Keep raw transactions off external models by default. If a model is later enabled, send only explicitly consented, minimized aggregates and preserve a deterministic fallback.
- Use robust second-hand statistics such as landed-price median, quartiles, outlier filtering, freshness decay, and match confidence. Hide single-point estimates when confidence is low.

## Verify and hand off

- Test domain edge cases, connector contracts, service fail-closed behavior, and at least one real end-to-end UI flow.
- Compile every platform possible in the current environment. Route Apple native builds to macOS/Xcode CI and label them unverified until CI evidence exists.
- Generate artifacts and hashes when possible. Distinguish a successful source compile from installer signing, notarization, and store approval.
- Run `python scripts/validate_finance_app.py --project <project-root>` near handoff. Resolve errors; explain warnings that are intentionally deferred.
- Deliver links to the plan, list, architecture, test report, runnable artifact, and release blockers. Include exact test counts and commands.

## Completion rules

Claim a prototype complete only when its runnable flows, calculations, source labels, and tests pass. Claim Beta only after durable encrypted persistence, recovery, accessibility, performance, and platform CI pass. Claim production only after real provider authorization, security review, signing, privacy declarations, rollback, and store release gates pass.
