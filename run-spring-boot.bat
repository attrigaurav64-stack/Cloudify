@echo off
title Cloudify Web (Spring Boot)
cls
echo ================================================================
echo                     CLOUDIFY WEB (SPRING BOOT)
echo ================================================================
echo.
echo [*] Launching Cloudify Backend...
echo [*] URL: http://localhost:8080
echo.

if exist mvnw.cmd (
    call mvnw.cmd spring-boot:run
) else (
    where mvn >nul 2>&1
    if %ERRORLEVEL% equ 0 (
        mvn spring-boot:run
    ) else (
        echo.
        echo [!] Maven is not installed in PATH.
        echo [*] Running instant launcher instead (run-ui.bat)...
        call run-ui.bat
    )
)

pause
