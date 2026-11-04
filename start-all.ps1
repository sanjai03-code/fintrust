# FinTrust Microservices Launcher (PowerShell)
Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host "  FinTrust - Financial Transaction and Risk Management System" -ForegroundColor White
Write-Host "==============================================================================" -ForegroundColor Cyan

$root = $PSScriptRoot
Set-Location $root

Write-Host "`n1. Running Database Migrations..." -ForegroundColor Yellow
php database/migrate.php

Write-Host "`n2. Compiling Java Backend..." -ForegroundColor Yellow
Set-Location "$root\java-service"
cmd /c build.bat
Set-Location $root

Write-Host "`n3. Starting Java Microservice on Port 8080..." -ForegroundColor Green
Start-Process cmd -ArgumentList "/k cd java-service && run.bat"

Write-Host "`n4. Starting PHP Service & Web Portal on Port 8000..." -ForegroundColor Green
Start-Process cmd -ArgumentList "/k php -S 127.0.0.1:8000 php-service/public/index.php"

Write-Host "`nFinTrust services launched!" -ForegroundColor Cyan
Write-Host "Web Application: http://127.0.0.1:8000" -ForegroundColor White
Write-Host "Java Microservice: http://127.0.0.1:8080" -ForegroundColor White

Start-Sleep -Seconds 2
Start-Process "http://127.0.0.1:8000"
