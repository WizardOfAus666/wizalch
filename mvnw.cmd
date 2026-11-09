@echo off
setlocal enabledelayedexpansion

REM AlchMate Pro Maven Wrapper (mvnw.cmd)
REM Uses system Maven (mvn) if installed on PATH; otherwise executes the Maven build lifecycle
REM (clean, compile, test, package, verify) directly using JDK 11+ and cached dependencies.

where mvn.cmd >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    call mvn.cmd %*
    exit /b %ERRORLEVEL%
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0maven-build.ps1" %*
exit /b %ERRORLEVEL%
