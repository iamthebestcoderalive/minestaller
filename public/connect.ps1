# connect.ps1
$ErrorActionPreference = "Stop"
Write-Host "=== Minestaller Agent Connection Setup ===" -ForegroundColor Cyan

$agentDir = "$env:USERPROFILE\minestaller-agent"
if (!(Test-Path $agentDir)) {
    New-Item -ItemType Directory -Path $agentDir | Out-Null
}
Set-Location $agentDir

Write-Host "Downloading lightweight companion files from GitHub..." -ForegroundColor Yellow

$baseUrl = "https://raw.githubusercontent.com/iamthebestcoderalive/minestaller/main"
$files = @(
    "server.js",
    "package.json",
    "public/index.html",
    "routes/config.js",
    "routes/instance.js",
    "routes/worlds.js",
    "routes/mods.js",
    "routes/migrater.js",
    "utils/nbt.js",
    "utils/file.js",
    "utils/minecraft.js",
    "utils/sys.js"
)

foreach ($f in $files) {
    $dest = Join-Path $agentDir $f
    $parent = Split-Path $dest -Parent
    if (!(Test-Path $parent)) {
        New-Item -ItemType Directory -Path $parent | Out-Null
    }
    Invoke-WebRequest -Uri "$baseUrl/$f" -OutFile $dest
}

if (Get-Command node -ErrorAction SilentlyContinue) {
    Write-Host "Node.js detected. Starting Minestaller companion agent..." -ForegroundColor Yellow
    # Launch agent in a new CMD window and keep it running
    Start-Process cmd -ArgumentList "/c npm install && node server.js"
    Write-Host "Agent successfully launched in a separate window!" -ForegroundColor Green
    Start-Sleep -Seconds 3
} else {
    Write-Host "Node.js not detected." -ForegroundColor Red
    Write-Host "Please install Node.js (https://nodejs.org) to run the Minestaller local helper agent." -ForegroundColor Yellow
    Start-Sleep -Seconds 10
}
