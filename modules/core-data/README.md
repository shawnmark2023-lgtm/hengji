# core-data

`core-data` owns Hengji's local ledger persistence boundary. Application modules depend on
`PersistentLedgerRepository`; they do not construct or configure Room.

## Platform entry points

- Encrypted desktop: `openDesktopProtectedLedger(dataDirectory)`
- Encrypted Android: `openAndroidProtectedLedger(context, plaintextSource = ...)`
- Encrypted iOS: `openIosProtectedLedger(applicationSupportDirectory)`
- Plaintext development Room: `createDesktopLedgerRepository`, `createAndroidLedgerRepository`,
  and `createIosLedgerRepository`

All repositories implement `PersistentLedgerRepository`. The Room factories remain explicit development and migration
sources. The protected factories use an authenticated encrypted envelope and the platform key providers; Android and
iOS refuse to create an unrelated empty ledger when legacy Room artifacts exist without an explicit migration source.

Schema v3 persists ledger revision, transactions, assets, maintenance costs, usage events, market quotes, insight
preferences, and reversible import batches. Monetary values use signed 64-bit minor units; domain validation remains
the source of truth for whether a particular value is allowed.

## Import guarantees

`commitImportBatch` requires a stable fingerprint for every transaction. A repeated batch is idempotent, and existing
fingerprints are skipped, including fingerprints held by soft-deleted tombstones. `rollbackImportBatch` removes only
transactions recorded as inserted by that batch and marks the batch rolled back in the same database transaction. It
refuses a rollback that would orphan an active refund outside the batch. A batch identifier cannot be reused after
rollback.

## Transaction lifecycle

Normal snapshots hide soft-deleted transactions; full snapshots and JSON/CSV exports retain their tombstones for
audit and recovery. Restore is compare-and-set: callers must present the exact non-negative deletion timestamp, and a
stale token cannot revive a transaction deleted again later. An active refund prevents deletion of its original
transaction, and a refund cannot be restored while its original is absent or deleted. Deleting an asset-linked
purchase transaction does not cascade to the asset, maintenance history, usage history, or market quotes.

## Backup and restore

`LedgerJsonCodec` exports the full aggregate as schema-versioned JSON and restores schema v0/v1/v2 payloads. It rejects
future schemas and payloads larger than 25 MiB. Repository `replaceWith` validates referential integrity and replaces
the aggregate atomically. Replacement revision is `max(current, imported) + 1`, so restore cannot move the local
revision backward.

## Security status

Room uses `BundledSQLiteDriver`, which is **not encrypted at rest**. The default
`ALLOW_UNENCRYPTED_DEVELOPMENT` policy is only for development and tests. Production must request
`REQUIRE_APPLICATION_ENCRYPTION`; it deliberately fails with `DatabaseEncryptionUnavailableException` until an audited
encrypted SQLite driver is integrated.

`DatabaseKeyProvider`, `PayloadCipher`, and `ProtectedLedgerPayloadCodec` define a fail-closed authenticated-encryption
boundary. `ProtectedLedgerRepository` is the encrypted snapshot adapter: it refreshes before every operation, applies
mutations to a candidate snapshot, authenticates and atomically compare-and-swaps the encrypted envelope, and publishes
the candidate to memory only after the durable write succeeds. A stale process cannot silently overwrite a newer
cooperating writer. Cancellation during the commit window cannot leave the in-memory instance behind its own committed
write.

`Aes256GcmPayloadCipher` uses cryptography-kotlin's
platform providers (JCA on JVM/Android and CryptoKit/CommonCrypto on Apple), generates a fresh 96-bit nonce, and binds
the envelope version, ledger schema, key alias, and algorithm as associated data. The versioned JSON envelope rejects
unknown versions, algorithms, malformed Base64, truncated tags, tampering, wrong keys, and oversized payloads.

This is a real cryptographic primitive, but it does **not** make the current Room database encrypted. Missing keys never
fall back to plaintext. `WindowsDpapiDatabaseKeyProvider` now provisions a 256-bit data key, protects it with
current-user DPAPI, stores only a versioned protected blob, and refuses to replace corrupt or unreadable material.
The key alias and blob format are bound as DPAPI optional entropy, so swapping protected blobs between aliases also
fails closed. `AndroidKeystoreDatabaseKeyProvider` uses a non-exportable AES-256-GCM wrapping key from
`AndroidKeyStore`, stores the authenticated data-key envelope under `noBackupFilesDir`, binds the alias and format as
AAD, and publishes the envelope without replacing an existing file. A corrupt or swapped envelope and an unavailable
wrapping key fail closed. `IosKeychainDatabaseKeyProvider` and `MacOsKeychainDatabaseKeyProvider` store the 256-bit
data key as a non-synchronizing generic-password item with `WhenUnlockedThisDeviceOnly`; the macOS implementation opts
into the data-protection Keychain. Both reject unexpected status/type/size and resolve concurrent first creation
through Keychain's duplicate-item result without replacing the winner.

`JvmAtomicProtectedLedgerStore`, `AndroidAtomicProtectedLedgerStore`, and `IosAtomicProtectedLedgerStore` provide bounded
atomic envelope replacement. Desktop additionally has a tested Room migration gate. It snapshots Room twice, verifies
the authenticated target, atomically renames the main SQLite file to a retirement marker, removes WAL/SHM/journal
sidecars, and deletes the marker last. A crash therefore leaves either a readable source database or an identifiable
cleanup-only state. Divergent plaintext and encrypted snapshots fail closed and retain the plaintext source.

Opening a new protected ledger first checks for an existing platform key. If the envelope and migration source are both
missing while a key still exists, opening fails closed instead of creating an empty ledger that could hide file loss or
trigger demo reseeding. A crash after first key provisioning but before the initial envelope commit also requires an
explicit recovery/reset path; a durable initialization journal remains a pre-Beta recovery improvement.

The Desktop, Android, and iOS application composition roots select their protected factories and fail closed when a platform
key, authenticated envelope, or legacy migration cannot be opened. The iOS factory automatically detects the legacy
Room database, snapshots it twice, and uses a retirement marker before removing SQLite sidecars. Its encrypted file is
set to `NSFileProtectionComplete` and excluded from system backup after every atomic replacement so a device-only
Keychain item is not separated from a restored envelope. Android retirement, encrypted-store performance on
representative devices, Android device execution, and Apple
Keychain/file-coordination runtime validation remain required. The Apple implementations currently have cross-
compilation and release-shrinking evidence, not a Keychain round trip or migration run on signed Apple hosts.

## Verification

From the repository root:

```text
./gradlew :modules:core-data:desktopTest --configure-on-demand --no-configuration-cache
./gradlew :modules:core-data:testAndroidHostTest --configure-on-demand
./gradlew :modules:core-data:compileAndroidMain :modules:core-data:compileIosMainKotlinMetadata --configure-on-demand
```

Android host tests exercise the vault lifecycle with an injected authenticated protector; a device/emulator is still
required to exercise the real `AndroidKeyStore` provider. iOS/macOS Keychain runtime tests require a macOS host and an
appropriately signed app/test host.
