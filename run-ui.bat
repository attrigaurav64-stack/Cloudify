@echo off
title Cloudify Web
cls
echo ================================================================
echo                          CLOUDIFY WEB
echo ================================================================
echo.
echo [*] Compiling Java classes...
javac -cp "lib/*" -d bin src/chat_app/web/StandaloneUiServer.java src/chat_app/Client.java src/main/java/com/chatapp/client/TerminalClient.java
if errorlevel 1 (
    echo [!] Compilation failed. Please verify that JDK is configured in PATH.
    pause
    exit /b 1
)

echo [*] Launching Cloudify at http://localhost:8080 ...
start http://localhost:8080

echo [*] Server is LIVE at http://localhost:8080 (Press Ctrl+C to stop)
echo.
java -cp "bin;lib/*" chat_app.web.StandaloneUiServer
pause
