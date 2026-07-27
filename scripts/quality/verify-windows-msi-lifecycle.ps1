[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$BaselineMsiPath,
    [Parameter(Mandatory = $true)]
    [string]$UpgradeMsiPath,
    [string]$OutputPath = "",
    [string]$ExpectedInstallSubdirectory = "Programs\Hengji",
    [switch]$SkipExecutableLaunch,
    [int]$StartupTimeoutSeconds = 30
)

$ErrorActionPreference = "Stop"

if (-not $IsWindows -and $PSVersionTable.PSEdition -eq "Core") {
    throw "Windows MSI lifecycle verification can only run on Windows."
}
if ($StartupTimeoutSeconds -lt 5) {
    throw "StartupTimeoutSeconds must be at least 5."
}

$baselineMsi = (Resolve-Path -LiteralPath $BaselineMsiPath).Path
$upgradeMsi = (Resolve-Path -LiteralPath $UpgradeMsiPath).Path
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) "hengji-install-lifecycle-$([guid]::NewGuid().ToString('N'))"
$dataRoot = Join-Path $tempRoot "isolated-data"
$baselineLog = Join-Path $tempRoot "install-baseline.log"
$upgradeLog = Join-Path $tempRoot "install-upgrade.log"
$uninstallLog = Join-Path $tempRoot "uninstall-upgrade.log"
$previousDataRoot = [Environment]::GetEnvironmentVariable("HENGJI_DATA_DIR", "Process")
$baselineInstalledByTest = $false
$upgradeInstalledByTest = $false
$completed = $false
$result = $null

function Get-MsiMetadata {
    param([Parameter(Mandatory = $true)][string]$Path)

    $installer = New-Object -ComObject WindowsInstaller.Installer
    $database = $installer.GetType().InvokeMember(
        "OpenDatabase",
        "InvokeMethod",
        $null,
        $installer,
        @($Path, 0)
    )
    $view = $database.GetType().InvokeMember(
        "OpenView",
        "InvokeMethod",
        $null,
        $database,
        @("SELECT Property, Value FROM Property")
    )
    $view.GetType().InvokeMember("Execute", "InvokeMethod", $null, $view, $null) | Out-Null
    $properties = [ordered]@{}
    while ($true) {
        $record = $view.GetType().InvokeMember("Fetch", "InvokeMethod", $null, $view, $null)
        if (-not $record) {
            break
        }
        $name = $record.GetType().InvokeMember("StringData", "GetProperty", $null, $record, @(1))
        $value = $record.GetType().InvokeMember("StringData", "GetProperty", $null, $record, @(2))
        $properties[$name] = $value
    }
    $view.GetType().InvokeMember("Close", "InvokeMethod", $null, $view, $null) | Out-Null

    $perUserAuthored = (
        -not $properties.Contains("ALLUSERS") -or
        (
            $properties["ALLUSERS"] -eq "2" -and
            $properties["MSIINSTALLPERUSER"] -eq "1"
        )
    )
    return [pscustomobject]@{
        Path = $Path
        Bytes = (Get-Item -LiteralPath $Path).Length
        Sha256 = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash
        Authenticode = (Get-AuthenticodeSignature -FilePath $Path).Status.ToString()
        ProductName = $properties["ProductName"]
        ProductVersion = $properties["ProductVersion"]
        ProductCode = $properties["ProductCode"]
        UpgradeCode = $properties["UpgradeCode"]
        AllUsers = $properties["ALLUSERS"]
        MsiInstallPerUser = $properties["MSIINSTALLPERUSER"]
        PerUserAuthored = $perUserAuthored
    }
}

function Get-ProductRegistration {
    param([Parameter(Mandatory = $true)][string]$ProductCode)

    $roots = @(
        "HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall",
        "HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall",
        "HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall"
    )
    foreach ($root in $roots) {
        $path = Join-Path $root $ProductCode
        if (Test-Path -LiteralPath $path) {
            $item = Get-ItemProperty -LiteralPath $path
            return [pscustomobject]@{
                RegistryPath = $path
                DisplayName = $item.DisplayName
                DisplayVersion = $item.DisplayVersion
                InstallLocation = $item.InstallLocation
            }
        }
    }
    return $null
}

function Get-AnyHengjiRegistration {
    $roots = @(
        "HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall",
        "HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall",
        "HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall"
    )
    return @(
        foreach ($root in $roots) {
            if (-not (Test-Path -LiteralPath $root)) {
                continue
            }
            Get-ChildItem -LiteralPath $root | ForEach-Object {
                $item = Get-ItemProperty -LiteralPath $_.PSPath
                if ($item.DisplayName -match "Hengji|恒迹") {
                    [pscustomobject]@{
                        RegistryPath = $_.Name
                        DisplayName = $item.DisplayName
                        DisplayVersion = $item.DisplayVersion
                    }
                }
            }
        }
    )
}

function Get-HengjiShortcuts {
    $programs = Join-Path $env:APPDATA "Microsoft\Windows\Start Menu\Programs"
    if (-not (Test-Path -LiteralPath $programs)) {
        return @()
    }
    return @(
        Get-ChildItem -LiteralPath $programs -Recurse -File -Filter "*.lnk" |
            Where-Object { $_.BaseName -match "Hengji|恒迹" } |
            Select-Object -ExpandProperty FullName
    )
}

function Get-NormalizedDirectoryPath {
    param([Parameter(Mandatory = $true)][string]$Path)

    return [System.IO.Path]::GetFullPath($Path).TrimEnd([char[]]"\/")
}

function Invoke-Msi {
    param(
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][string]$Operation
    )

    $process = Start-Process -FilePath "msiexec.exe" -ArgumentList $Arguments -Wait -PassThru
    if ($process.ExitCode -notin @(0, 3010)) {
        throw "$Operation failed with Windows Installer exit code $($process.ExitCode)."
    }
    return $process.ExitCode
}

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
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds
    )

    Start-Sleep -Seconds 5
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $running = @(Get-HengjiProcesses -AppRoot $AppRoot)
        $ledger = Get-ChildItem -LiteralPath $LedgerRoot -Recurse -File -Filter "hengji.ledger.hjenc" `
            -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($running.Count -gt 0 -and $ledger) {
            return [pscustomobject]@{
                Processes = $running
                Ledger = $ledger
            }
        }
        Start-Sleep -Milliseconds 250
    } while ([DateTime]::UtcNow -lt $deadline)

    throw "Installed Hengji did not remain running with a protected ledger within $TimeoutSeconds seconds."
}

function Invoke-TestCleanup {
    param(
        [Parameter(Mandatory = $true)][string]$BaselineProductCode,
        [Parameter(Mandatory = $true)][string]$UpgradeProductCode
    )

    if ($upgradeInstalledByTest -and (Get-ProductRegistration -ProductCode $UpgradeProductCode)) {
        Start-Process -FilePath "msiexec.exe" `
            -ArgumentList @("/x", $UpgradeProductCode, "/qn", "/norestart") `
            -Wait | Out-Null
    }
    if ($baselineInstalledByTest -and (Get-ProductRegistration -ProductCode $BaselineProductCode)) {
        Start-Process -FilePath "msiexec.exe" `
            -ArgumentList @("/x", $BaselineProductCode, "/qn", "/norestart") `
            -Wait | Out-Null
    }
}

$baseline = Get-MsiMetadata -Path $baselineMsi
$upgrade = Get-MsiMetadata -Path $upgradeMsi
if ($baseline.ProductName -ne "Hengji" -or $upgrade.ProductName -ne "Hengji") {
    throw "Lifecycle packages must both have ProductName Hengji."
}
if (-not $baseline.PerUserAuthored -or -not $upgrade.PerUserAuthored) {
    throw "Lifecycle packages must both be authored for per-user installation."
}
if (-not $baseline.ProductCode -or -not $upgrade.ProductCode -or $baseline.ProductCode -eq $upgrade.ProductCode) {
    throw "Lifecycle packages must have distinct non-empty ProductCode values."
}
if (-not $baseline.UpgradeCode -or $baseline.UpgradeCode -ne $upgrade.UpgradeCode) {
    throw "Lifecycle packages must share one non-empty UpgradeCode."
}
if ([version]$baseline.ProductVersion -ge [version]$upgrade.ProductVersion) {
    throw "Baseline version must be lower than upgrade version."
}

$appRoot = Join-Path $env:LOCALAPPDATA $ExpectedInstallSubdirectory
$defaultDataRoot = Join-Path $env:LOCALAPPDATA $upgrade.ProductName
$resolvedAppRoot = Get-NormalizedDirectoryPath -Path $appRoot
$resolvedDefaultDataRoot = Get-NormalizedDirectoryPath -Path $defaultDataRoot
if ($resolvedAppRoot.Equals($resolvedDefaultDataRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Installer directory must not overlap the default protected-ledger data directory."
}
$executablePath = Join-Path $appRoot "$($upgrade.ProductName).exe"
$existingRegistrations = @(Get-AnyHengjiRegistration)
$existingShortcuts = @(Get-HengjiShortcuts)
if ($existingRegistrations.Count -gt 0) {
    throw "Refusing lifecycle test because a Hengji product is already registered."
}
if (Test-Path -LiteralPath $appRoot) {
    throw "Refusing lifecycle test because the expected install directory already exists: $appRoot"
}
if ($existingShortcuts.Count -gt 0) {
    throw "Refusing lifecycle test because a Hengji Start Menu shortcut already exists."
}

New-Item -ItemType Directory -Force -Path $dataRoot | Out-Null

try {
    $baselineInstallExit = Invoke-Msi `
        -Operation "Baseline installation" `
        -Arguments @(
            "/i",
            "`"$baselineMsi`"",
            "/qn",
            "/norestart",
            "/L*v",
            "`"$baselineLog`""
        )
    $baselineInstalledByTest = $true
    $baselineRegistration = Get-ProductRegistration -ProductCode $baseline.ProductCode
    if (-not $baselineRegistration) {
        throw "Baseline MSI did not create the expected product registration."
    }
    if (-not (Test-Path -LiteralPath $executablePath)) {
        throw "Baseline MSI did not install the expected executable: $executablePath"
    }
    if (
        $baselineRegistration.InstallLocation -and
        -not (Get-NormalizedDirectoryPath -Path $baselineRegistration.InstallLocation).Equals(
            $resolvedAppRoot,
            [System.StringComparison]::OrdinalIgnoreCase
        )
    ) {
        throw "Baseline MSI registered an unexpected installation location."
    }
    $baselineShortcuts = @(Get-HengjiShortcuts)
    if ($baselineShortcuts.Count -eq 0) {
        throw "Baseline MSI did not create a current-user Start Menu shortcut."
    }

    $baselineLauncherId = $null
    $baselineAliveProcessCount = 0
    $baselineStoppedProcessCount = 0
    $dpapiFiles = @()
    $plaintextDatabases = @()
    if ($SkipExecutableLaunch) {
        $probePath = Join-Path $dataRoot "installer-retention-probe.bin"
        $probeBytes = New-Object byte[] 64
        [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($probeBytes)
        [System.IO.File]::WriteAllBytes($probePath, $probeBytes)
        $ledger = Get-Item -LiteralPath $probePath
    }
    else {
        [Environment]::SetEnvironmentVariable("HENGJI_DATA_DIR", $dataRoot, "Process")
        $baselineLauncher = Start-Process `
            -FilePath $executablePath `
            -WorkingDirectory $appRoot `
            -WindowStyle Hidden `
            -PassThru
        $baselineLaunch = Wait-HengjiLaunch `
            -AppRoot $appRoot `
            -LedgerRoot $dataRoot `
            -TimeoutSeconds $StartupTimeoutSeconds
        $baselineStopped = @(Stop-HengjiProcesses -AppRoot $appRoot)
        $baselineLauncherId = $baselineLauncher.Id
        $baselineAliveProcessCount = $baselineLaunch.Processes.Count
        $baselineStoppedProcessCount = $baselineStopped.Count

        $ledger = Get-Item -LiteralPath $baselineLaunch.Ledger.FullName
        $dpapiFiles = @(Get-ChildItem -LiteralPath $dataRoot -Recurse -File -Filter "*.dpapi")
        if ($dpapiFiles.Count -eq 0) {
            throw "Baseline installation did not create DPAPI-protected key material."
        }
        $plaintextDatabases = @(Get-ChildItem -LiteralPath $dataRoot -Recurse -File -Filter "hengji.db")
        if ($plaintextDatabases.Count -ne 0) {
            throw "Baseline installation created a plaintext hengji.db file."
        }
        $ledgerText = [System.Text.Encoding]::UTF8.GetString([System.IO.File]::ReadAllBytes($ledger.FullName))
        if ($ledgerText.Contains("asset-headphones")) {
            throw "Baseline protected ledger exposes the sample asset sentinel in plaintext."
        }
    }
    $baselineLedgerHash = (Get-FileHash -LiteralPath $ledger.FullName -Algorithm SHA256).Hash
    $baselineLedgerLength = $ledger.Length
    $baselineLedgerWriteTicks = $ledger.LastWriteTimeUtc.Ticks

    $upgradeInstallExit = Invoke-Msi `
        -Operation "Package upgrade" `
        -Arguments @(
            "/i",
            "`"$upgradeMsi`"",
            "/qn",
            "/norestart",
            "/L*v",
            "`"$upgradeLog`""
        )
    $upgradeInstalledByTest = $true
    $baselineInstalledByTest = $false
    $oldRegistrationAfterUpgrade = Get-ProductRegistration -ProductCode $baseline.ProductCode
    $upgradeRegistration = Get-ProductRegistration -ProductCode $upgrade.ProductCode
    if ($oldRegistrationAfterUpgrade) {
        throw "Upgrade left the baseline ProductCode registered."
    }
    if (-not $upgradeRegistration -or $upgradeRegistration.DisplayVersion -ne $upgrade.ProductVersion) {
        throw "Upgrade MSI did not register the expected version."
    }
    if (-not (Test-Path -LiteralPath $executablePath)) {
        throw "Upgrade removed the installed executable."
    }
    if (
        $upgradeRegistration.InstallLocation -and
        -not (Get-NormalizedDirectoryPath -Path $upgradeRegistration.InstallLocation).Equals(
            $resolvedAppRoot,
            [System.StringComparison]::OrdinalIgnoreCase
        )
    ) {
        throw "Upgrade MSI registered an unexpected installation location."
    }

    $upgradeLauncherId = $null
    $upgradeAliveProcessCount = 0
    $upgradeStoppedProcessCount = 0
    if (-not $SkipExecutableLaunch) {
        $upgradeLauncher = Start-Process `
            -FilePath $executablePath `
            -WorkingDirectory $appRoot `
            -WindowStyle Hidden `
            -PassThru
        $upgradeLaunch = Wait-HengjiLaunch `
            -AppRoot $appRoot `
            -LedgerRoot $dataRoot `
            -TimeoutSeconds $StartupTimeoutSeconds
        $upgradeStopped = @(Stop-HengjiProcesses -AppRoot $appRoot)
        $upgradeLauncherId = $upgradeLauncher.Id
        $upgradeAliveProcessCount = $upgradeLaunch.Processes.Count
        $upgradeStoppedProcessCount = $upgradeStopped.Count
    }

    $ledger = Get-Item -LiteralPath $ledger.FullName
    $upgradeLedgerHash = (Get-FileHash -LiteralPath $ledger.FullName -Algorithm SHA256).Hash
    $ledgerUnchanged = (
        $baselineLedgerHash -eq $upgradeLedgerHash -and
        $baselineLedgerLength -eq $ledger.Length -and
        $baselineLedgerWriteTicks -eq $ledger.LastWriteTimeUtc.Ticks
    )
    if (-not $ledgerUnchanged) {
        throw "Isolated user data changed during package upgrade."
    }
    if (-not $SkipExecutableLaunch) {
        $plaintextDatabases = @(Get-ChildItem -LiteralPath $dataRoot -Recurse -File -Filter "hengji.db")
        if ($plaintextDatabases.Count -ne 0) {
            throw "Upgrade created a plaintext hengji.db file."
        }
    }

    $uninstallExit = Invoke-Msi `
        -Operation "Upgrade uninstall" `
        -Arguments @(
            "/x",
            $upgrade.ProductCode,
            "/qn",
            "/norestart",
            "/L*v",
            "`"$uninstallLog`""
        )
    $upgradeInstalledByTest = $false
    if (Get-ProductRegistration -ProductCode $upgrade.ProductCode) {
        throw "Uninstall left the upgrade ProductCode registered."
    }
    if (Test-Path -LiteralPath $appRoot) {
        throw "Uninstall left the application installation directory."
    }
    if ((Get-HengjiShortcuts).Count -ne 0) {
        throw "Uninstall left a Hengji Start Menu shortcut."
    }
    if (-not (Test-Path -LiteralPath $ledger.FullName)) {
        throw "Uninstall removed the isolated user data probe."
    }
    $retainedLedgerHash = (Get-FileHash -LiteralPath $ledger.FullName -Algorithm SHA256).Hash
    if ($retainedLedgerHash -ne $upgradeLedgerHash) {
        throw "Uninstall modified the retained user data probe."
    }

    $limitations = @(
        "Both MSI packages contain the same application code; this proves installer version transition, not a historical application schema migration.",
        "The isolated retained data probe is deleted by the test harness after proving the installer did not remove it.",
        "MSI and executable signing, SmartScreen reputation, and production rollout remain unverified."
    )
    if ($SkipExecutableLaunch) {
        $limitations += "Installed executable launch was skipped; run without SkipExecutableLaunch on an independent runner."
    }
    $result = [ordered]@{
        schemaVersion = 1
        gate = "windows-msi-install-upgrade-uninstall"
        status = "passed"
        installContext = "per-user"
        installPath = $appRoot
        baseline = [ordered]@{
            msi = $baseline.Path
            version = $baseline.ProductVersion
            productCode = $baseline.ProductCode
            upgradeCode = $baseline.UpgradeCode
            bytes = $baseline.Bytes
            sha256 = $baseline.Sha256
            authenticode = $baseline.Authenticode
            installExitCode = $baselineInstallExit
            registrationPath = $baselineRegistration.RegistryPath
            launcherProcessId = $baselineLauncherId
            aliveProcessCount = $baselineAliveProcessCount
            stoppedProcessCount = $baselineStoppedProcessCount
            startMenuShortcutCount = $baselineShortcuts.Count
        }
        upgrade = [ordered]@{
            msi = $upgrade.Path
            version = $upgrade.ProductVersion
            productCode = $upgrade.ProductCode
            upgradeCode = $upgrade.UpgradeCode
            bytes = $upgrade.Bytes
            sha256 = $upgrade.Sha256
            authenticode = $upgrade.Authenticode
            installExitCode = $upgradeInstallExit
            registrationPath = $upgradeRegistration.RegistryPath
            baselineProductRemoved = ($null -eq $oldRegistrationAfterUpgrade)
            launcherProcessId = $upgradeLauncherId
            aliveProcessCount = $upgradeAliveProcessCount
            stoppedProcessCount = $upgradeStoppedProcessCount
        }
        uninstall = [ordered]@{
            exitCode = $uninstallExit
            productRegistrationRemoved = $true
            applicationDirectoryRemoved = $true
            startMenuShortcutRemoved = $true
            userDataProbeRetainedByInstaller = $true
        }
        installedExecutableLaunch = [ordered]@{
            status = if ($SkipExecutableLaunch) { "skipped" } else { "passed" }
            strictByDefault = $true
        }
        dataRetention = [ordered]@{
            probeKind = if ($SkipExecutableLaunch) { "random-binary-sentinel" } else { "protected-ledger" }
            bytes = $ledger.Length
            sha256 = $upgradeLedgerHash
            unchangedAcrossUpgrade = $ledgerUnchanged
            retainedAcrossUninstall = $true
        }
        protectedStorage = [ordered]@{
            status = if ($SkipExecutableLaunch) { "skipped" } else { "passed" }
            dpapiFileCount = $dpapiFiles.Count
            plaintextDatabaseCount = $plaintextDatabases.Count
            sampleSentinelVisibleInCiphertext = if ($SkipExecutableLaunch) { $null } else { $false }
        }
        limitations = $limitations
    }
    $completed = $true
}
finally {
    try {
        if (Test-Path -LiteralPath $appRoot) {
            Stop-HengjiProcesses -AppRoot $appRoot | Out-Null
        }
    }
    catch {
        Write-Warning "Failed to stop one or more installed Hengji processes: $_"
    }
    [Environment]::SetEnvironmentVariable("HENGJI_DATA_DIR", $previousDataRoot, "Process")
    Invoke-TestCleanup `
        -BaselineProductCode $baseline.ProductCode `
        -UpgradeProductCode $upgrade.ProductCode

    if ($completed) {
        $resolvedTemp = [System.IO.Path]::GetFullPath($tempRoot)
        $expectedPrefix = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
        if (
            $resolvedTemp.StartsWith($expectedPrefix, [System.StringComparison]::OrdinalIgnoreCase) -and
            (Split-Path -Leaf $resolvedTemp).StartsWith("hengji-install-lifecycle-")
        ) {
            Remove-Item -LiteralPath $resolvedTemp -Recurse -Force
        }
    }
    else {
        Write-Warning "MSI lifecycle logs were preserved for diagnosis at $tempRoot"
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
