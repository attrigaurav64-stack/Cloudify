@echo off
title Cloudify Database Refresh
echo ==============================================================
echo  Cloudify - Refreshing Database and Clearing Old Chats
echo ==============================================================

if not exist bin mkdir bin

echo Compiling ResetDatabase.java...
javac -cp "lib/mysql-connector-j-8.4.0.jar" -d bin src/chat_app/ResetDatabase.java
if %errorlevel% neq 0 (
    echo [ERROR] Compilation failed.
    pause
    exit /b %errorlevel%
)

echo Connecting to database and clearing old messages...
java -cp "bin;lib/mysql-connector-j-8.4.0.jar" chat_app.ResetDatabase

echo.
echo Database refresh completed!
pause
