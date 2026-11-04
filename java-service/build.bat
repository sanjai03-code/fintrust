@echo off
echo ===================================================
echo  Building FinTrust Java Backend Microservice
echo ===================================================

cd /d "%~dp0"

if not exist bin mkdir bin

echo Compiling Java source files with JDK 21...
javac -d bin src/main/java/com/fintrust/config/*.java src/main/java/com/fintrust/model/*.java src/main/java/com/fintrust/service/*.java src/main/java/com/fintrust/server/*.java

if %ERRORLEVEL% EQU 0 (
    echo [OK] Java microservice compiled successfully to bin/
) else (
    echo [ERROR] Compilation failed with error code %ERRORLEVEL%
    exit /b %ERRORLEVEL%
)
