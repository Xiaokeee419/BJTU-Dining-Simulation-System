$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path $PSScriptRoot -Parent
$runtimeDir = Join-Path $projectRoot '.dev'
$processFile = Join-Path $runtimeDir 'processes.json'

if (-not (Test-Path $processFile)) {
    Write-Host 'No development process record was found.'
    exit 0
}

$processes = Get-Content $processFile -Raw | ConvertFrom-Json
foreach ($targetPid in @(
    $processes.backendPid,
    $processes.frontendPid,
    $processes.backendLauncherPid,
    $processes.frontendLauncherPid
)) {
    if (-not $targetPid) {
        continue
    }
    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$targetPid" -ErrorAction SilentlyContinue
    if ($process -and $process.CommandLine -like "*$projectRoot*") {
        Stop-Process -Id $targetPid -Force
        Write-Host "Stopped process $targetPid."
    }
}

Remove-Item -LiteralPath $processFile -Force
