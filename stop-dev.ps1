param(
    [int]$BackendPort = 8080,
    [int[]]$FrontendPorts = @(5173, 5174, 5175, 5176, 5177, 5178, 5179, 5180)
)

$ErrorActionPreference = 'Stop'

$repoRoot = $PSScriptRoot
$windowTitles = @('BJTU Backend', 'BJTU Frontend')

function Get-ProcessDetails {
    param(
        [Parameter(Mandatory = $true)]
        [int]$ProcessId
    )

    Get-CimInstance Win32_Process -Filter "ProcessId = $ProcessId" -ErrorAction SilentlyContinue
}

function Get-ChildProcessIds {
    param(
        [Parameter(Mandatory = $true)]
        [int]$ParentId
    )

    $children = @(Get-CimInstance Win32_Process -Filter "ParentProcessId = $ParentId" -ErrorAction SilentlyContinue)
    $ids = @()

    foreach ($child in $children) {
        $ids += $child.ProcessId
        $ids += Get-ChildProcessIds -ParentId $child.ProcessId
    }

    $ids | Select-Object -Unique
}

function Stop-ProcessTree {
    param(
        [Parameter(Mandatory = $true)]
        [int]$ProcessId
    )

    $descendants = @(Get-ChildProcessIds -ParentId $ProcessId)
    foreach ($childId in ($descendants | Sort-Object -Descending)) {
        Stop-Process -Id $childId -Force -ErrorAction SilentlyContinue
    }

    Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue
}

function Is-ProjectProcess {
    param(
        [Parameter(Mandatory = $true)]
        [int]$ProcessId
    )

    $details = Get-ProcessDetails -ProcessId $ProcessId
    if (-not $details) {
        return $false
    }

    $commandLine = $details.CommandLine
    if (-not $commandLine) {
        return $false
    }

    return $commandLine -like "*$repoRoot*" -or $commandLine -like '*com.bjtu.dining.DiningSimulationApplication*'
}

function Stop-WindowByTitle {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Title
    )

    $killed = @()
    $hosts = @(
        Get-Process powershell -ErrorAction SilentlyContinue
        Get-Process pwsh -ErrorAction SilentlyContinue
    ) | Where-Object { $_.MainWindowTitle -eq $Title }

    foreach ($host in $hosts) {
        $killed += $host.Id
        Stop-ProcessTree -ProcessId $host.Id
    }

    $killed | Select-Object -Unique
}

function Get-ListeningProcessIds {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Port
    )

    try {
        @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop |
            Select-Object -ExpandProperty OwningProcess -Unique)
    } catch {
        @()
    }
}

$stopped = @()

foreach ($title in $windowTitles) {
    $stopped += Stop-WindowByTitle -Title $title
}

$portsToCheck = @($BackendPort) + $FrontendPorts
foreach ($port in $portsToCheck) {
    $processIds = @(Get-ListeningProcessIds -Port $port)
    foreach ($processId in $processIds) {
        if ($stopped -contains $processId) {
            continue
        }

        if (Is-ProjectProcess -ProcessId $processId) {
            $stopped += $processId
            Stop-ProcessTree -ProcessId $processId
        }
    }
}

$stopped = $stopped | Select-Object -Unique

if ($stopped.Count -eq 0) {
    Write-Host 'No running BJTU backend/frontend processes were found.'
    exit 0
}

Write-Host ('Stopped processes: ' + ($stopped -join ', '))
