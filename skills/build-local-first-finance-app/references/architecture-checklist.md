# Architecture checklist

## Required boundaries

- UI depends on use cases or repositories, not vendor SDKs.
- Domain contains money, transactions, assets, usage, market estimates, and insight contracts without platform APIs.
- Data owns durable storage, migrations, import batches, deduplication, export, and deletion.
- Connectors own authorization, cursors, provider schemas, rate limits, provenance, and fail-closed production configuration.
- Platform adapters own file pickers, secure storage, deep links, share extensions, notifications, and OS-specific permissions.
- Services are optional trust boundaries, not required for offline bookkeeping.

## Dependency direction

Prefer `platform/UI -> application -> domain <- data/connectors`. Put interfaces at the stable boundary. Do not let provider DTOs or database rows become domain entities.

## Cross-platform decision record

Record supported targets, native escape hatches, accessibility surface, packaging, update strategy, local database support, CI hosts, minimum OS versions, and why the choice beats credible alternatives. Verify current framework stability from official sources.

## Data lifecycle

Specify create, edit, import, deduplicate, undo, soft delete, export, restore, migrate, sync, account deletion, and retention. Define what happens offline and after app reinstall.

## Production evolution

Keep account and sync behind interfaces from day one, but do not ship empty security theater. Add them only with credential vaults, verified sessions, key rotation, conflict semantics, recovery, and revocation.
