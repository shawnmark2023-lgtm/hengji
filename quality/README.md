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
python scripts/quality/run_quality.py --gates architecture release-guards
```

Run the real Kotlin malformed-import and 100,000-row ledger harnesses:

```powershell
python scripts/quality/run_quality.py --gates malformed-import large-ledger
```

The runner uses the checked Gradle Wrapper. If a controlled build environment already provides the
same verified Gradle distribution, `HENGJI_GRADLE` may point to that executable; the exact override
path is preserved in evidence.

Each command writes machine-readable evidence under `quality/evidence/` by default. CI writes the
same records to an artifact directory. A workflow definition is recorded as `configured`; only a
successful workflow run may produce `passed` evidence. Packaging evidence proves only that the
unsigned package was produced and hashed, not signing, notarization, installation, upgrade, or
store approval.

## Gate ownership

- `architecture-policy.json`: dependency direction rules for domain and common UI sources.
- `ci-gates.json`: configured CI jobs and their current non-passed repository state.
- `harness/`: isolated Kotlin executable that consumes the real project modules through a Gradle
  composite build.
- `scripts/quality/`: cross-platform checks, runners, and evidence generation.

The large-ledger harness is a deterministic developer/CI baseline. It is not representative-device
evidence and cannot by itself close the Beta performance gate.
