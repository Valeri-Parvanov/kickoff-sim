# Reliable local start: stops the Docker app container (frees port 8080),
# ensures MySQL is up, then runs the app locally via the Maven wrapper.
#
# Note: docker writes progress to stderr, so we do NOT set
# $ErrorActionPreference='Stop' and do NOT redirect stderr — in Windows
# PowerShell that would turn docker's normal output into a fatal error.

Set-Location $PSScriptRoot

Write-Host "==> Stopping Docker app container (if running) to free port 8080..." -ForegroundColor Cyan
docker compose stop app

Write-Host "==> Starting MySQL container..." -ForegroundColor Cyan
docker compose up -d mysql

$busy = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($busy) {
    $pid8080 = $busy[0].OwningProcess
    $proc = (Get-Process -Id $pid8080 -ErrorAction SilentlyContinue).ProcessName
    Write-Host "Port 8080 is still in use by PID $pid8080 ($proc). Stop it and retry." -ForegroundColor Red
    exit 1
}

Write-Host "==> Starting the application on http://localhost:8080 ..." -ForegroundColor Green
& .\mvnw.cmd spring-boot:run
