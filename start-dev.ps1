param(
    [switch]$Install
)

$ErrorActionPreference = 'Stop'

$repoRoot = $PSScriptRoot
$backendDir = Join-Path $repoRoot 'backend'
$frontendDir = Join-Path $repoRoot 'frontend'
$frontendNodeModules = Join-Path $frontendDir 'node_modules'

function Require-Command {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Missing required command: $Name"
    }
}

function Test-PortInUse {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Port
    )

    try {
        return [bool](Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop)
    } catch {
        return $false
    }
}

if (-not (Test-Path $backendDir)) {
    throw "Backend directory not found: $backendDir"
}

if (-not (Test-Path $frontendDir)) {
    throw "Frontend directory not found: $frontendDir"
}

Require-Command java
Require-Command mvn
Require-Command node
Require-Command npm

if ($Install -or -not (Test-Path $frontendNodeModules)) {
    Write-Host 'Installing frontend dependencies...'
    Push-Location $frontendDir
    try {
        npm install
    } finally {
        Pop-Location
    }
}

if (Test-PortInUse 8080) {
    Write-Warning 'Port 8080 is already in use. Backend may fail to start.'
}

if (Test-PortInUse 5173) {
    Write-Warning 'Port 5173 is already in use. Vite may switch to another port.'
}

$backendCommand = @"
`$host.UI.RawUI.WindowTitle = 'BJTU Backend'
Set-Location '$backendDir'
mvn spring-boot:run
"@.Trim()

$frontendCommand = @"
`$host.UI.RawUI.WindowTitle = 'BJTU Frontend'
Set-Location '$frontendDir'
npm run dev
"@.Trim()

Start-Process powershell -WorkingDirectory $backendDir -ArgumentList @(
    '-NoExit',
    '-Command',
    $backendCommand
)

Start-Sleep -Seconds 2

Start-Process powershell -WorkingDirectory $frontendDir -ArgumentList @(
    '-NoExit',
    '-Command',
    $frontendCommand
)

Write-Host 'Backend starting on http://localhost:8080'
Write-Host 'Frontend dev server starting on http://127.0.0.1:5173'
Write-Host 'If script execution is blocked, run:'
Write-Host 'powershell -ExecutionPolicy Bypass -File .\start-dev.ps1'
