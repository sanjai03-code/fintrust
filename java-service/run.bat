@echo off
echo ===================================================
echo  Starting FinTrust Java Microservice on Port 8080
echo ===================================================

cd /d "%~dp0"

if not exist bin (
    call build.bat
)

set JAVA_SERVICE_PORT=8080
java -cp "bin;lib/*;C:\xampp\tomcat\lib\*" com.fintrust.server.StandaloneServer
