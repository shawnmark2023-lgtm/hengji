# Quality gates

## Prototype

- Plan, development list, architecture, threat model, and research sources exist.
- Money edge cases and insight rules have deterministic tests.
- One desktop or mobile target runs through add transaction, view totals, inspect asset cost, and view insight.
- Every sandbox or demo source is visibly non-live.
- Test report distinguishes passed, unverified, externally blocked, and prohibited work.

## Beta

- Durable database, migration, export, restore, undo, and deletion pass cross-restart tests; encrypted production policy fails closed until platform-backed encryption is verified.
- Import preview/mapping/deduplication/rollback has UI and contract coverage.
- Accessibility, large text, keyboard, screen reader, dark/light, narrow/wide layouts pass.
- Large-ledger performance and memory budgets pass on representative devices.
- Android and Apple CI compile; Windows/macOS packages install and upgrade.
- Release-minified artifacts launch and exercise durable storage; debug success is not accepted as release evidence.

## Production

- Provider scope and contracts are approved; no sandbox endpoint appears in production.
- Threat model, dependency audit, penetration test, privacy declarations, incident response, and rollback pass.
- Account verification, secure credential storage, recovery, revocation, encrypted sync, and conflict handling pass.
- Store signing, notarization, staged rollout, observability minimization, and support procedures are complete.

## Evidence format

For each gate record the exact command, timestamp, environment, test count, artifact path/hash, and limitation. A configured CI job is not a passed job until a run result exists. A generated installer is not a passed installer until its contents and executable launch are verified; signing, upgrade, and uninstall remain separate gates.
