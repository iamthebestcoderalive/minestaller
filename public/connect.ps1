# connect.ps1
$ErrorActionPreference = "Stop"
Write-Host "=== Minestaller Agent Connection Setup ===" -ForegroundColor Cyan

$agentDir = "$env:USERPROFILE\minestaller-agent"
if (!(Test-Path $agentDir)) {
    New-Item -ItemType Directory -Path $agentDir | Out-Null
}
Set-Location $agentDir

Write-Host "Downloading agent files from GitHub..." -ForegroundColor Yellow
Invoke-WebRequest -Uri "https://raw.githubusercontent.com/barho/minestaller/main/server.js" -OutFile "server.js"
Invoke-WebRequest -Uri "https://raw.githubusercontent.com/barho/minestaller/main/package.json" -OutFile "package.json"

if (Get-Command node -ErrorAction SilentlyContinue) {
    Write-Host "Node.js detected. Installing dependencies..." -ForegroundColor Yellow
    # Launch agent in a new CMD window and keep it running
    Start-Process cmd -ArgumentList "/c npm install && node server.js"
    Write-Host "Agent successfully launched in a separate window!" -ForegroundColor Green
    Start-Sleep -Seconds 3
} else {
    Write-Host "Node.js not detected." -ForegroundColor Red
    Write-Host "Please install Node.js (https://nodejs.org) to run the Minestaller local helper agent." -ForegroundColor Yellow
    Start-Sleep -Seconds 10
}
