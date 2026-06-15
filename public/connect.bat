:; curl -sSfL https://raw.githubusercontent.com/barho/minestaller/main/public/connect.sh | bash; exit
@echo off
powershell -ExecutionPolicy Bypass -Command "Invoke-RestMethod -Uri 'https://raw.githubusercontent.com/barho/minestaller/main/public/connect.ps1' | Invoke-Expression"
