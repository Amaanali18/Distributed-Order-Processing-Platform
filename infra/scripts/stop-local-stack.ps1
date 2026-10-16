$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$pidFile = Join-Path $root 'logs\stack-pids.txt'

if (-not (Test-Path $pidFile)) {
    Write-Output 'no pid file; nothing to stop'
    exit 0
}

$ids = Get-Content $pidFile | Where-Object { $_ -match '^\d+$' }
foreach ($id in $ids) {
    Stop-Process -Id $id -Force -ErrorAction SilentlyContinue
    Write-Output "stopped pid=$id"
}
Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
