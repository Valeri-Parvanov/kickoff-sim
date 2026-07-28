# Reliable local start: stops the Docker app container (frees port 8080),
# brings up the notification service, then runs the app locally via the Maven wrapper.
#
# MySQL is expected to run on the host (both the local run and the containers
# connect to localhost:3306 / host.docker.internal:3306), so there is no MySQL
# service to start here.
#
# Note: docker writes progress to stderr, so we do NOT set
# $ErrorActionPreference='Stop' and do NOT redirect stderr — in Windows
# PowerShell that would turn docker's normal output into a fatal error.

Set-Location $PSScriptRoot

Write-Host "==> Stopping Docker app container (if running) to free port 8080..." -ForegroundColor Cyan
docker compose stop app

Write-Host "==> Starting the notification service on http://localhost:8081 ..." -ForegroundColor Cyan
docker compose up -d notifications

& "$PSScriptRoot\free-port-8080.ps1"

Write-Host "==> Starting the application on http://localhost:8080 (dev profile) ..." -ForegroundColor Green
& .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
