# core-data

`core-data` owns Hengji's local ledger persistence boundary. Application modules depend on
`PersistentLedgerRepository`; they do not construct or configure Room.

## Platform entry points

- Desktop: `createDesktopLedgerRepository(absolutePath, policy)`
- Android: `createAndroidLedgerRepository(context, databaseName, policy)`
- iOS: `createIosLedgerRepository(absolutePath, policy)`

Each factory returns `RoomLedgerRepository`. Repository operations are suspending and mutations are committed in
Room transactions. Call `close()` when the platform application shuts down or replaces the repository.

Schema v2 persists ledger revision, transactions, assets, maintenance costs, usage events, market quotes, insight
preferences, and reversible import batches. Monetary values use signed 64-bit minor units; domain validation remains
the source of truth for whether a particular value is allowed.

## Import guarantees

`commitImportBatch` requires a stable fingerprint for every transaction. A repeated batch is idempotent, and existing
fingerprints are skipped. `rollbackImportBatch` removes only transactions recorded as inserted by that batch and marks
the batch rolled back in the same database transaction. A batch identifier cannot be reused after rollback.

## Backup and restore

`LedgerJsonCodec` exports the full aggregate as schema-versioned JSON and restores schema v0/v1 payloads. It rejects
future schemas and payloads larger than 25 MiB. Repository `replaceWith` validates referential integrity and replaces
the aggregate atomically.

## Security status

Room currently uses `BundledSQLiteDriver`, which is **not encrypted at rest**. The default
`ALLOW_UNENCRYPTED_DEVELOPMENT` policy is only for development and tests. Production must request
`REQUIRE_APPLICATION_ENCRYPTION`; it deliberately fails with `DatabaseEncryptionUnavailableException` until an audited
encrypted SQLite driver is integrated.

`DatabaseKeyProvider`, `PayloadCipher`, and `ProtectedLedgerPayloadCodec` define a fail-closed authenticated-encryption
boundary for protected exports or an encrypted snapshot adapter. `Aes256GcmPayloadCipher` uses cryptography-kotlin's
platform providers (JCA on JVM/Android and CryptoKit/CommonCrypto on Apple), generates a fresh 96-bit nonce, and binds
the envelope version, ledger schema, key alias, and algorithm as associated data. The versioned JSON envelope rejects
unknown versions, algorithms, malformed Base64, truncated tags, tampering, wrong keys, and oversized payloads.

This is a real cryptographic primitive, but it does **not** make the current Room database encrypted. Missing keys never
fall back to plaintext. `WindowsDpapiDatabaseKeyProvider` now provisions a 256-bit data key, protects it with
current-user DPAPI, stores only a versioned protected blob, and refuses to replace corrupt or unreadable material.
The key alias and blob format are bound as DPAPI optional entropy, so swapping protected blobs between aliases also
fails closed. Android Keystore, Apple Keychain, plaintext-to-ciphertext migration, and repository wiring are still
required before `REQUIRE_APPLICATION_ENCRYPTION` can be enabled.

## Verification

From the repository root:

```text
./gradlew :modules:core-data:desktopTest --configure-on-demand --no-configuration-cache
./gradlew :modules:core-data:compileAndroidMain :modules:core-data:compileIosMainKotlinMetadata --configure-on-demand
```

Native iOS simulator tests require a macOS host.
