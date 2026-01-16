@echo off
REM SJPO Startup Script for Windows

echo ================================================================
echo   Simple Java Process Orchestrator (SJPO)
echo ================================================================
echo.

REM Check if Java is installed
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Java is not installed or not in PATH
    echo Please install Java 25 or higher
    pause
    exit /b 1
)

REM Check if JAR exists
set JAR_FILE=target\SJPO.jar
if not exist "%JAR_FILE%" (
    echo ERROR: JAR file not found: %JAR_FILE%
    echo Please build the project first: mvn clean package
    pause
    exit /b 1
)

echo Starting SJPO...
echo.

REM Run SJPO
java -jar "%JAR_FILE%" %*

echo.
echo SJPO has been shut down.
pause