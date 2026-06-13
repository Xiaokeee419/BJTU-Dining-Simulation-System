$mavenRoot = Join-Path $HOME '.m2\wrapper\dists'

if (-not (Test-Path $mavenRoot)) {
  throw "Maven wrapper directory not found: $mavenRoot"
}

$mvnCommand = Get-ChildItem -Path $mavenRoot -Recurse -Filter mvn.cmd -File -ErrorAction SilentlyContinue |
  Sort-Object LastWriteTime -Descending |
  Select-Object -First 1

if (-not $mvnCommand) {
  throw "mvn.cmd was not found under $mavenRoot"
}

$mavenBin = Split-Path $mvnCommand.FullName -Parent
$pathEntries = $env:Path -split ';'

if ($pathEntries -notcontains $mavenBin) {
  $env:Path = "$mavenBin;$env:Path"
}

Write-Host "Maven enabled for this PowerShell session:" -ForegroundColor Green
Write-Host "  $($mvnCommand.FullName)"
& $mvnCommand.FullName -v
