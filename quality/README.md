# HENGJI quality gates

This directory contains executable Beta-quality checks that do not change application behavior.

## Local entry points

Windows PowerShell:

```powershell
.\scripts\quality\run-quality.ps1
```

Linux/macOS or CI Bash:

```bash
bash scripts/quality/run-quality.sh
```

Run only the fast static gates:

```powershell
python scripts/quality/run_quality.py --gates formatting architecture release-guards apple-readiness reproducibility supply-chain-inventory
```

Run the locked JaCoCo coverage threshold and the real Kotlin malformed-import and
100,000-row ledger harnesses:

```powershell
python scripts/quality/run_quality.py --gates coverage malformed-import large-ledger
```

Android Gradle Plugin rejects non-ASCII Windows project paths. In that case, expose the repository
through an ASCII junction whose final directory name is non-empty and pass the alias explicitly:

```powershell
.\scripts\quality\run-quality.ps1 -ProjectRoot C:\Temp\hengji-p0\project
```

A bare substituted drive root such as `T:\` is suitable for the main build but not for the composite
quality harness, which requires a non-empty project directory name. Use an ASCII directory or an
exact isolated source copy for `malformed-import` and `large-ledger`.

Safely normalize the shared Kotlin/Kotlin DSL, TypeScript, and Python whitespace contract:

```powershell
python scripts/quality/check_formatting.py --fix
```

Generate the deterministic Windows/Android CycloneDX 1.6 SBOM, then run the checksum-pinned
OSV Scanner against the exact runtime and build-tool inventory:

```powershell
python scripts/quality/install_osv_scanner.py --output .tools/osv-scanner.exe
python scripts/quality/verify_supply_chain.py `
  --scanner .tools/osv-scanner.exe `
  --output-dir quality/evidence/supply-chain
```

The inventory contains Windows and Android release runtime dependencies plus Node/Python build
tools. iOS/macOS are explicitly marked deferred. Google ML Kit artifacts whose registry metadata
reports a non-standard license are recorded with `LicenseRef-Google-ML-Kit-Terms`; their exact
package versions, official terms URL, and review expiry are policy-bound. A dependency upgrade or
expired exception requires a new review. The scanner binary version and SHA-256 are pinned, and all
GitHub Actions must use policy-approved full commit SHAs.

The connector package also owns the pinned strict Python type checker used by the price service:

```powershell
cd services\connector-gateway
npm ci --ignore-scripts
npm run typecheck
npm run typecheck:price
npm test
npm audit --audit-level=high
```

Build a source snapshot twice in an isolated temporary directory and require identical normalized
Windows or Android archive content:

```powershell
python scripts/quality/reproducible_build.py --target windows
python scripts/quality/reproducible_build.py --target android
```

On Windows, verify an MSI as a separate release stage: administrative extraction, two launches of
the extracted executable, DPAPI key material, ciphertext preservation across restart, and absence
of a plaintext `hengji.db`:

```powershell
.\scripts\quality\verify-windows-msi.ps1 `
  -MsiPath .\apps\client\build\compose\binaries\main\msi\Hengji-0.1.0.msi `
  -OutputPath .\quality\evidence\windows-msi-smoke.json
```

Verify a real per-user install, lower-to-higher package upgrade, installed executable launch,
uninstall cleanup, and user-data retention with two packages that share one upgrade UUID:

```powershell
.\scripts\quality\verify-windows-msi-lifecycle.ps1 `
  -BaselineMsiPath .\build\release-lifecycle\Hengji-0.0.9.msi `
  -UpgradeMsiPath .\build\release-lifecycle\Hengji-0.1.0.msi `
  -OutputPath .\quality\evidence\windows-msi-lifecycle.json
```

The lifecycle script refuses to run when any Hengji product, install directory, or Start Menu
shortcut already exists. Its default mode requires the installed executable to launch. The
`-SkipExecutableLaunch` switch is only for a host where Application Control blocks unsigned
executables; it still verifies install, upgrade, uninstall, shortcut cleanup, and an isolated data
retention probe, and records the skipped launch as a limitation.

The runner uses the checked Gradle Wrapper. If a controlled build environment already provides the
same verified Gradle distribution, `HENGJI_GRADLE` may point to that executable; the exact override
path is preserved in evidence.

Each command writes machine-readable evidence under `quality/evidence/` by default. CI writes the
same records to an artifact directory. A workflow definition is recorded as `configured`; only a
successful workflow run may produce `passed` evidence. Windows MSI smoke evidence additionally
proves administrative extraction and protected-ledger launch/reopen. Strict lifecycle evidence
proves per-user install, package upgrade, installed launch, uninstall cleanup, and data retention;
it does not prove signing, SmartScreen reputation, historical schema migration, or store approval.

## Gate ownership

- `architecture-policy.json`: dependency direction rules for domain and common UI sources.
- `coverage-policy.json`: per-module line and branch thresholds for shared Windows/Android logic.
- `coverage-tools/`: independently locked and SHA-256-verified JaCoCo agent and CLI.
- `reproducibility-policy.json`: pinned Wrapper, lockfile, verification metadata, and artifact targets.
- `supply-chain-policy.json`: release runtime scope, license policy and exceptions, pinned scanner,
  and immutable GitHub Action references.
- `ci-gates.json`: configured CI jobs and their current non-passed repository state.
- `harness/`: isolated Kotlin executable that consumes the real project modules through a Gradle
  composite build.
- `scripts/quality/`: cross-platform checks, runners, and evidence generation.

The large-ledger harness is a deterministic developer/CI baseline. It is not representative-device
evidence and cannot by itself close the Beta performance gate.

The reproducibility gate records a dependency-manifest hash. The two-build scripts compare archive
paths, sizes, permissions, and uncompressed entry content while ignoring ZIP timestamps. They also
retain the raw container SHA-256 as a diagnostic, but it is explicitly not the pass/fail criterion.
A local pass does not replace independent clean-runner CI evidence, and a configured CI job is
still not a passed job until a run publishes evidence.
