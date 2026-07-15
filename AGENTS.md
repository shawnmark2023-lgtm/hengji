# HENGJI engineering guide

## Dependency direction

Presentation -> Application -> Domain <- Ports <- Infrastructure.

- Do not reference Compose, SQL, HTTP, filesystem, or platform SDKs from domain modules.
- Store money as minor units in `Long`; never persist currency with floating-point values.
- Treat imports and provider payloads as untrusted input.
- Keep sample/sandbox market data visibly labeled as non-live.
- Never add a scraper, password autofill, private API reverse engineering, ad SDK, or behavior tracker.

## Module ownership

- `apps/client`: adaptive UI and platform entry points.
- `modules/core-domain`: entities, value objects, invariants, cost calculations.
- `modules/core-data`: repositories, persistence, migrations, export.
- `modules/core-insights`: deterministic metrics and explainable recommendations.
- `modules/connectors`: provider contracts, import parsers, sandbox adapters.
- `services`: optional remote OAuth and price aggregation boundaries.

## Done means

Run formatting, static checks, unit tests, and the build task appropriate to the touched module. Document platform-specific checks that cannot run on the current host. Do not claim iOS/macOS signing success from a Windows host.
