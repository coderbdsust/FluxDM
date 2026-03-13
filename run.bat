@echo off
REM FluxDM Launcher — Windows
SET "DIR=%~dp0"
SET "JAR=%DIR%FluxDM-2.0.0.jar"

WHERE java >nul 2>nul
IF %ERRORLEVEL% NEQ 0 (
    echo ERROR: Java not found. Install Java 11+ from https://adoptium.net
    pause
    EXIT /B 1
)

java -Xms64m -Xmx512m -jar "%JAR%" %*
