# Frees port 8080 by killing whatever process is currently listening on it.
# Intended to run automatically as an IntelliJ "Before Launch" step, so a
# forgotten/orphaned kickoff-sim process never blocks the next start.

$conns = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if (-not $conns) {
    Write-Host "Port 8080 is free."
    exit 0
}

foreach ($pidToKill in ($conns | Select-Object -ExpandProperty OwningProcess -Unique)) {
    $proc = Get-Process -Id $pidToKill -ErrorAction SilentlyContinue
    if ($proc) {
        Write-Host "Port 8080 was held by PID $pidToKill ($($proc.ProcessName)) - stopping it."
        Stop-Process -Id $pidToKill -Force -ErrorAction SilentlyContinue
    }
}

Start-Sleep -Milliseconds 300
Write-Host "Port 8080 is now free."
