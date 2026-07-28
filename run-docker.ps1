# Full Docker start: rebuilds both images from the current source, then starts
# the notification service and the app.
#
# The images bake the built jar, so a plain "docker compose up -d" would keep
# running whatever code was compiled the last time the image was built. This
# script always rebuilds, so what runs matches the working tree.
#
# MySQL is expected to run on the host; the containers reach it through
# host.docker.internal:3306.
#
# Note: docker writes progress to stderr, so we do NOT set
# $ErrorActionPreference='Stop' and do NOT redirect stderr — in Windows
# PowerShell that would turn docker's normal output into a fatal error.

Set-Location $PSScriptRoot

Write-Host "==> Rebuilding images and starting containers..." -ForegroundColor Cyan
docker compose up -d --build

Write-Host "==> App:           http://localhost:8080" -ForegroundColor Green
Write-Host "==> Notifications: http://localhost:8081" -ForegroundColor Green
Write-Host "==> Logs: docker compose logs -f app" -ForegroundColor DarkGray
