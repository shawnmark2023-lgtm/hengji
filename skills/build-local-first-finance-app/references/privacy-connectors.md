# Privacy and connector rules

## Data classification

Financial transactions, purchase history, assets, usage patterns, and inferred budgets remain sensitive even after direct identifiers are removed. Data minimization means collecting only fields required for the feature, not claiming the remaining data is non-personal.

## Connector ladder

1. Manual entry and user-selected CSV/JSON/PDF.
2. Share extension or OCR with preview and confirmation.
3. Official OAuth, entitlement, or system financial API after scope approval.
4. Contracted/licensed aggregator with provenance and deletion obligations.

Reject credential capture, cookie reuse, undocumented private endpoints, terms-violating scraping, and background collection that the platform cannot authorize.

## Authorization contract

Require PKCE S256, one-time state with expiry, exact redirect binding, least scopes, short-lived tokens, secure token storage, revocation, bounded responses, rate limits, and production fail-closed behavior. Do not persist tokens in the prototype.

## Import contract

Limit file size and row count, normalize encoding and dates, reject spreadsheet formulas on export, use deterministic fingerprints, preview mappings, commit atomically, and expose batch rollback. Retain source provenance without retaining unnecessary raw files.

## Price contract

Use `MANUAL`, `DEMO`, `OFFICIAL_API`, or `LICENSED_AGGREGATOR` provenance. Include specification, condition, currency, shipping, collection time, confidence, and source URL only when permitted. Demo data must never claim an external URL or live status.

## Model contract

Default off. Keep raw records local. Send minimized aggregates only after explicit consent, publish the exact fields, set retention to the narrowest supported value, and keep deterministic analysis available offline.
