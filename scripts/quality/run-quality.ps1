[CmdletBinding()]
param(
    [string[]]$Gates = @(
        "formatting",
        "architecture",
        "release-guards",
        "reproducibility",
        "coverage",
        "malformed-import",
        "large-ledger"
    ),
    [int]$BenchmarkCount = 100000,
    [int]$MaxMillis = 20000,
    [int]$MaxMemoryMiB = 768,
    [string]$ProjectRoot = "",
    [string]$OutputDir = ""
)

$ErrorActionPreference = "Stop"
if (-not $ProjectRoot) {
    $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
}
$Arguments = @(
    (Join-Path $PSScriptRoot "run_quality.py"),
    "--project", $ProjectRoot,
    "--gates"
) + $Gates + @(
    "--benchmark-count", $BenchmarkCount,
    "--max-millis", $MaxMillis,
    "--max-memory-mib", $MaxMemoryMiB
)
if ($OutputDir) {
    $Arguments += @("--output-dir", $OutputDir)
}

Push-Location $ProjectRoot
try {
    & python @Arguments
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
