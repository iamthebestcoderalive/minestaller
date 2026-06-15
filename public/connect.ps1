# connect.ps1
$ErrorActionPreference = "Stop"
Write-Host "=== Minestaller Agent Connection Setup ===" -ForegroundColor Cyan

$agentDir = "$env:USERPROFILE\minestaller-agent"

# Check if agent is already installed — skip download if it is
$isInstalled = (Test-Path "$agentDir\server.js") -and (Test-Path "$agentDir\package.json")

if ($isInstalled) {
    Write-Host "Minestaller agent already installed. Skipping download." -ForegroundColor Green
} else {
    if (!(Test-Path $agentDir)) {
        New-Item -ItemType Directory -Path $agentDir | Out-Null
    }
    Set-Location $agentDir

    Write-Host "Downloading Minestaller companion files from GitHub..." -ForegroundColor Yellow

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

    Write-Host "Download complete." -ForegroundColor Green
}

Set-Location $agentDir

if (Get-Command node -ErrorAction SilentlyContinue) {
    Write-Host "Starting Minestaller agent..." -ForegroundColor Yellow
    Start-Process cmd -ArgumentList "/c npm install && node server.js && pause"
    Write-Host "Agent launched! Open http://localhost:5000 in your browser." -ForegroundColor Green
    Start-Sleep -Seconds 3
} else {
    Write-Host "Node.js not detected." -ForegroundColor Red
    Write-Host "Please install Node.js from https://nodejs.org then run this command again." -ForegroundColor Yellow
    Start-Sleep -Seconds 10
}
