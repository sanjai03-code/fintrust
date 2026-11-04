@echo off
title FinTrust Microservices Launcher
echo ==============================================================================
echo   FinTrust - Financial Transaction and Risk Management System
echo ==============================================================================
echo.

cd /d "%~dp0"

echo 1. Checking Database and running migrations...
php database/migrate.php

echo.
echo 2. Building Java Microservice...
cd java-service
call build.bat
cd ..

echo.
echo 3. Launching Java Microservice (Port 8080)...
start "FinTrust - Java Microservice (Port 8080)" cmd /k "cd java-service && run.bat"

echo.
echo 4. Launching PHP API Gateway and Frontend (Port 8000)...
start "FinTrust - Web & PHP Gateway (Port 8000)" cmd /k "php -S 127.0.0.1:8000 php-service/public/index.php"

echo.
echo ==============================================================================
echo   All FinTrust services are launching!
echo   Web Portal URL: http://127.0.0.1:8000
echo   Java Service:   http://127.0.0.1:8080
echo ==============================================================================
echo Opening browser in 3 seconds...
timeout /t 3 >nul
start http://127.0.0.1:8000
