[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$MsiPath,
    [string]$OutputPath = "",
    [int]$StartupTimeoutSeconds = 30
)

$ErrorActionPreference = "Stop"

if (-not $IsWindows -and $PSVersionTable.PSEdition -eq "Core") {
    throw "Windows MSI verification can only run on Windows."
}
if ($StartupTimeoutSeconds -lt 5) {
    throw "StartupTimeoutSeconds must be at least 5."
}

$resolvedMsi = (Resolve-Path -LiteralPath $MsiPath).Path
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) "hengji-msi-smoke-$([guid]::NewGuid().ToString('N'))"
$extractRoot = Join-Path $tempRoot "extracted"
$dataRoot = Join-Path $tempRoot "isolated-data"
$logPath = Join-Path $tempRoot "administrative-extract.log"
$completed = $false
$result = $null
$previousDataRoot = [Environment]::GetEnvironmentVariable("HENGJI_DATA_DIR", "Process")

function Get-HengjiProcesses {
    param([Parameter(Mandatory = $true)][string]$AppRoot)

    $prefix = $AppRoot.TrimEnd("\") + "\"
    return @(
        Get-Process -ErrorAction SilentlyContinue | ForEach-Object {
            try {
                if ($_.Path -and $_.Path.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
                    [pscustomobject]@{
                        Id = $_.Id
                        ProcessName = $_.ProcessName
                        Path = $_.Path
                    }
                }
            }
            catch {
                # Some protected system processes do not expose Path.
            }
        }
    )
}

function Stop-HengjiProcesses {
    param([Parameter(Mandatory = $true)][string]$AppRoot)

    $running = @(Get-HengjiProcesses -AppRoot $AppRoot)
    foreach ($item in $running) {
        Stop-Process -Id $item.Id -Force -ErrorAction SilentlyContinue
    }
    if ($running.Count -gt 0) {
        Start-Sleep -Seconds 2
    }
    return $running
}

function Wait-HengjiLaunch {
    param(
        [Parameter(Mandatory = $true)][string]$AppRoot,
        [Parameter(Mandatory = $true)][string]$LedgerRoot,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds,
        [Parameter(Mandatory = $true)][bool]$RequireNewLedger
    )

    Start-Sleep -Seconds 5
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $running = @(Get-HengjiProcesses -AppRoot $AppRoot)
        $ledger = Get-ChildItem -LiteralPath $LedgerRoot -Recurse -File -Filter "hengji.ledger.hjenc" `
            -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($running.Count -gt 0 -and ($ledger -or -not $RequireNewLedger)) {
            return [pscustomobject]@{
                Processes = $running
                Ledger = $ledger
            }
        }
        Start-Sleep -Milliseconds 250
    } while ([DateTime]::UtcNow -lt $deadline)

    throw "Hengji did not remain running with the expected protected ledger within $TimeoutSeconds seconds."
}

New-Item -ItemType Directory -Force -Path $extractRoot, $dataRoot | Out-Null

try {
    $msiArguments = @(
        "/a",
        "`"$resolvedMsi`"",
        "/qn",
        "TARGETDIR=`"$extractRoot`"",
        "/L*v",
        "`"$logPath`""
    )
    $extractProcess = Start-Process -FilePath "msiexec.exe" -ArgumentList $msiArguments -Wait -PassThru
    if ($extractProcess.ExitCode -ne 0) {
        throw "Windows Installer administrative extraction failed with exit code $($extractProcess.ExitCode)."
    }

    $executable = Get-ChildItem -LiteralPath $extractRoot -Recurse -File -Filter "Hengji.exe" |
        Sort-Object FullName |
        Select-Object -First 1
    if (-not $executable) {
        throw "Administrative extraction did not produce Hengji.exe."
    }
    $appRoot = $executable.Directory.FullName
    [Environment]::SetEnvironmentVariable("HENGJI_DATA_DIR", $dataRoot, "Process")

    $firstStartedAt = [DateTime]::UtcNow
    $firstProcess = Start-Process -FilePath $executable.FullName -WorkingDirectory $appRoot -WindowStyle Hidden -PassThru
    $firstLaunch = Wait-HengjiLaunch `
        -AppRoot $appRoot `
        -LedgerRoot $dataRoot `
        -TimeoutSeconds $StartupTimeoutSeconds `
        -RequireNewLedger $true
    $firstStopped = @(Stop-HengjiProcesses -AppRoot $appRoot)

    $ledger = Get-Item -LiteralPath $firstLaunch.Ledger.FullName
    $firstLedgerHash = (Get-FileHash -LiteralPath $ledger.FullName -Algorithm SHA256).Hash
    $firstLedgerLength = $ledger.Length
    $firstLedgerWriteTicks = $ledger.LastWriteTimeUtc.Ticks
    $firstLedgerWriteTime = $ledger.LastWriteTimeUtc.ToString("O")

    $dpapiFiles = @(Get-ChildItem -LiteralPath $dataRoot -Recurse -File -Filter "*.dpapi")
    if ($dpapiFiles.Count -eq 0) {
        throw "The extracted application did not create DPAPI-protected key material."
    }
    $plaintextDatabases = @(Get-ChildItem -LiteralPath $dataRoot -Recurse -File -Filter "hengji.db")
    if ($plaintextDatabases.Count -ne 0) {
        throw "The extracted application created a plaintext hengji.db file."
    }
    $ledgerText = [System.Text.Encoding]::UTF8.GetString([System.IO.File]::ReadAllBytes($ledger.FullName))
    if ($ledgerText.Contains("asset-headphones")) {
        throw "The protected ledger exposes the sample asset sentinel in plaintext."
    }

    $secondStartedAt = [DateTime]::UtcNow
    $secondProcess = Start-Process -FilePath $executable.FullName -WorkingDirectory $appRoot -WindowStyle Hidden -PassThru
    $secondLaunch = Wait-HengjiLaunch `
        -AppRoot $appRoot `
        -LedgerRoot $dataRoot `
        -TimeoutSeconds $StartupTimeoutSeconds `
        -RequireNewLedger $false
    $secondStopped = @(Stop-HengjiProcesses -AppRoot $appRoot)

    $ledger = Get-Item -LiteralPath $ledger.FullName
    $secondLedgerHash = (Get-FileHash -LiteralPath $ledger.FullName -Algorithm SHA256).Hash
    $ledgerUnchanged = (
        $firstLedgerHash -eq $secondLedgerHash -and
        $firstLedgerLength -eq $ledger.Length -and
        $firstLedgerWriteTicks -eq $ledger.LastWriteTimeUtc.Ticks
    )
    if (-not $ledgerUnchanged) {
        throw "Reopening the extracted application rewrote the protected ledger."
    }

    $plaintextDatabases = @(Get-ChildItem -LiteralPath $dataRoot -Recurse -File -Filter "hengji.db")
    if ($plaintextDatabases.Count -ne 0) {
        throw "Reopening the extracted application created a plaintext hengji.db file."
    }

    $result = [ordered]@{
        schemaVersion = 1
        gate = "windows-msi-administrative-extract-smoke"
        status = "passed"
        msi = [ordered]@{
            path = $resolvedMsi
            bytes = (Get-Item -LiteralPath $resolvedMsi).Length
            sha256 = (Get-FileHash -LiteralPath $resolvedMsi -Algorithm SHA256).Hash
            authenticode = (Get-AuthenticodeSignature -FilePath $resolvedMsi).Status.ToString()
        }
        administrativeExtract = [ordered]@{
            exitCode = $extractProcess.ExitCode
            executableRelativePath = $executable.FullName.Substring($extractRoot.Length).TrimStart("\")
        }
        executable = [ordered]@{
            bytes = $executable.Length
            sha256 = (Get-FileHash -LiteralPath $executable.FullName -Algorithm SHA256).Hash
            authenticode = (Get-AuthenticodeSignature -FilePath $executable.FullName).Status.ToString()
        }
        firstLaunch = [ordered]@{
            startedAtUtc = $firstStartedAt.ToString("O")
            launcherProcessId = $firstProcess.Id
            aliveProcessCount = $firstLaunch.Processes.Count
            stoppedProcessCount = $firstStopped.Count
        }
        reopen = [ordered]@{
            startedAtUtc = $secondStartedAt.ToString("O")
            launcherProcessId = $secondProcess.Id
            aliveProcessCount = $secondLaunch.Processes.Count
            stoppedProcessCount = $secondStopped.Count
        }
        protectedStorage = [ordered]@{
            ledgerBytes = $ledger.Length
            ledgerSha256 = $secondLedgerHash
            firstWriteTimeUtc = $firstLedgerWriteTime
            unchangedAcrossRestart = $ledgerUnchanged
            dpapiFileCount = $dpapiFiles.Count
            plaintextDatabaseCount = $plaintextDatabases.Count
            sampleSentinelVisibleInCiphertext = $false
        }
        limitations = @(
            "Administrative extraction is not a machine-wide installation.",
            "The package and executable are not required to be signed by this development gate.",
            "Upgrade, uninstall, SmartScreen reputation, and production signing remain unverified."
        )
    }
    $completed = $true
}
finally {
    if (Test-Path -LiteralPath $extractRoot) {
        try {
            $executable = Get-ChildItem -LiteralPath $extractRoot -Recurse -File -Filter "Hengji.exe" `
                -ErrorAction SilentlyContinue | Select-Object -First 1
            if ($executable) {
                Stop-HengjiProcesses -AppRoot $executable.Directory.FullName | Out-Null
            }
        }
        catch {
            Write-Warning "Failed to stop one or more extracted Hengji processes: $_"
        }
    }
    [Environment]::SetEnvironmentVariable("HENGJI_DATA_DIR", $previousDataRoot, "Process")

    if ($completed) {
        $resolvedTemp = [System.IO.Path]::GetFullPath($tempRoot)
        $expectedPrefix = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
        if (
            $resolvedTemp.StartsWith($expectedPrefix, [System.StringComparison]::OrdinalIgnoreCase) -and
            (Split-Path -Leaf $resolvedTemp).StartsWith("hengji-msi-smoke-")
        ) {
            Remove-Item -LiteralPath $resolvedTemp -Recurse -Force
        }
    }
    else {
        Write-Warning "MSI verification files were preserved for diagnosis at $tempRoot"
    }
}

$json = $result | ConvertTo-Json -Depth 8
if ($OutputPath) {
    if ([System.IO.Path]::IsPathRooted($OutputPath)) {
        $resolvedOutput = [System.IO.Path]::GetFullPath($OutputPath)
    }
    else {
        $resolvedOutput = [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $OutputPath))
    }
    $outputDirectory = Split-Path -Parent $resolvedOutput
    New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($resolvedOutput, $json + [Environment]::NewLine, $utf8NoBom)
}
Write-Output $json
