# Connector Gateway

Minimal Node.js/TypeScript boundary for future official OAuth connectors. It starts in `sandbox` mode, uses authorization code + PKCE S256 semantics, exact redirect URI allowlisting, expiring one-time state, bounded JSON requests, and never stores or returns a production token.

Production mode is intentionally fail-closed until a reviewed connector registry, encrypted token vault, secret manager, outbound host allowlist, revocation flow, and audit sink are configured.

```powershell
npm ci
npm test
npm start
```

No consumer platform in this build has production authorization. `/v1/connectors` reports sandbox capability only.
