param(
    [switch] $Reuse
)

$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path $PSScriptRoot -Parent
$runtimeDir = Join-Path $projectRoot '.dev'
$processFile = Join-Path $runtimeDir 'processes.json'
$backendDir = Join-Path $projectRoot 'backend'
$frontendDir = Join-Path $projectRoot 'frontend'

New-Item -ItemType Directory -Force -Path $runtimeDir | Out-Null
. (Join-Path $PSScriptRoot 'use-maven.ps1')

$maven = (Get-Command mvn.cmd -ErrorAction Stop).Source
$npm = (Get-Command npm.cmd -ErrorAction Stop).Source

function Get-ListeningProcessId([int] $Port) {
    foreach ($line in (& netstat -ano -p TCP)) {
        if ($line -match "^\s*TCP\s+\S+:$Port\s+\S+\s+LISTENING\s+(\d+)\s*$") {
            return [int] $Matches[1]
        }
    }
    return $null
}

function Assert-ProjectProcess([int] $ProcessId, [string] $Label) {
    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$ProcessId" -ErrorAction SilentlyContinue
    if (-not $process -or $process.CommandLine -notlike "*$projectRoot*") {
        throw "$Label port is already used by a process outside this project (PID $ProcessId)"
    }
}

function Wait-ForPort([int] $Port, [string] $Label) {
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        $client = New-Object System.Net.Sockets.TcpClient
        try {
            $connected = $client.ConnectAsync('127.0.0.1', $Port).Wait(250)
            if ($connected -and $client.Connected) {
                $listenerPid = Get-ListeningProcessId $Port
                Assert-ProjectProcess $listenerPid $Label
                return $listenerPid
            }
        } catch {
            # The service is still starting.
        } finally {
            $client.Dispose()
        }
        Start-Sleep -Milliseconds 250
    }
    throw "$Label did not start on port $Port. Check the logs under $runtimeDir"
}

function Stop-ProjectProcess([int] $ProcessId) {
    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$ProcessId" -ErrorAction SilentlyContinue
    if ($process -and $process.CommandLine -like "*$projectRoot*") {
        Stop-Process -Id $ProcessId -Force
        Write-Host "Stopped previous project process $ProcessId."
    }
}

if (-not $Reuse) {
    if (Test-Path $processFile) {
        $recordedProcesses = Get-Content $processFile -Raw | ConvertFrom-Json
        foreach ($recordedPid in @(
            $recordedProcesses.backendPid,
            $recordedProcesses.frontendPid,
            $recordedProcesses.backendLauncherPid,
            $recordedProcesses.frontendLauncherPid
        )) {
            if ($recordedPid) {
                Stop-ProjectProcess ([int] $recordedPid)
            }
        }
        Remove-Item -LiteralPath $processFile -Force
    }

    foreach ($port in @(8080, 5173)) {
        $listenerPid = Get-ListeningProcessId $port
        if ($listenerPid) {
            Assert-ProjectProcess $listenerPid "Development service"
            Stop-ProjectProcess $listenerPid
        }
    }

    Start-Sleep -Milliseconds 500
}

if (-not (Test-Path (Join-Path $frontendDir 'node_modules'))) {
    Write-Host 'Installing frontend dependencies...'
    & $npm install --prefix $frontendDir
    if ($LASTEXITCODE -ne 0) {
        throw 'npm install failed'
    }
}

$backendLauncher = $null
$backendPid = Get-ListeningProcessId 8080
if ($backendPid) {
    Assert-ProjectProcess $backendPid 'Backend'
} else {
    Set-Content -Path (Join-Path $runtimeDir 'backend.log') -Value ''
    Set-Content -Path (Join-Path $runtimeDir 'backend-error.log') -Value ''
    $backendLauncher = Start-Process `
        -FilePath $maven `
        -ArgumentList 'spring-boot:run' `
        -WorkingDirectory $backendDir `
        -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $runtimeDir 'backend.log') `
        -RedirectStandardError (Join-Path $runtimeDir 'backend-error.log') `
        -PassThru
    $backendPid = Wait-ForPort 8080 'Backend'
}

$frontendLauncher = $null
$frontendPid = Get-ListeningProcessId 5173
if ($frontendPid) {
    Assert-ProjectProcess $frontendPid 'Frontend'
} else {
    Set-Content -Path (Join-Path $runtimeDir 'frontend.log') -Value ''
    Set-Content -Path (Join-Path $runtimeDir 'frontend-error.log') -Value ''
    $frontendLauncher = Start-Process `
        -FilePath $npm `
        -ArgumentList 'run', 'dev', '--', '--host', '127.0.0.1', '--port', '5173', '--strictPort' `
        -WorkingDirectory $frontendDir `
        -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $runtimeDir 'frontend.log') `
        -RedirectStandardError (Join-Path $runtimeDir 'frontend-error.log') `
        -PassThru
    $frontendPid = Wait-ForPort 5173 'Frontend'
}

$backendLauncherPid = if ($backendLauncher) { $backendLauncher.Id } else { $null }
$frontendLauncherPid = if ($frontendLauncher) { $frontendLauncher.Id } else { $null }

@{
    backendPid = $backendPid
    frontendPid = $frontendPid
    backendLauncherPid = $backendLauncherPid
    frontendLauncherPid = $frontendLauncherPid
    projectRoot = $projectRoot
} | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $runtimeDir 'processes.json')

Write-Host ''
Write-Host 'Development services are starting:' -ForegroundColor Green
Write-Host '  Frontend: http://127.0.0.1:5173'
Write-Host '  Backend:  http://127.0.0.1:8080'
Write-Host "  Logs:     $runtimeDir"
Write-Host 'Stop them with: .\scripts\stop-dev.ps1'
