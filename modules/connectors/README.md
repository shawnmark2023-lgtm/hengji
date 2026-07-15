# Connectors

Pure Kotlin Multiplatform ports and sandbox adapters for HENGJI. The first build deliberately contains no HTTP client, platform secret, scraper, password capture, or production OAuth token persistence.

The module provides:

- connector capability, authorization, availability, privacy, cursor, and error contracts;
- bounded CSV/JSON parsing, configurable field mapping, deterministic deduplication, preview, atomic commit, and batch undo contracts;
- clearly labeled Alipay/WeChat/Taobao/JD sandbox connectors;
- manual and non-live demonstration second-hand quote providers.

All external rows enter a preview/reconciliation area. A caller must explicitly commit accepted rows through `ImportLedger`; connectors never write the main ledger directly.
