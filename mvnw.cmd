@echo off
@REM ----------------------------------------------------------------------------
@REM Maven Wrapper Script for Windows
@REM ----------------------------------------------------------------------------

setlocal

set "WRAPPER_DIR=%~dp0.mvn\wrapper"
set "WRAPPER_PROPERTIES=%WRAPPER_DIR%\maven-wrapper.properties"

@REM If system Maven is installed, prefer it
where mvn >nul 2>&1
if %ERRORLEVEL% equ 0 (
    mvn %*
    goto end
)

@REM Check if Maven is already downloaded in user profile
set "MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.6\apache-maven-3.9.6"
if exist "%MAVEN_HOME%\bin\mvn.cmd" (
    call "%MAVEN_HOME%\bin\mvn.cmd" %*
    goto end
)

echo [Maven Wrapper] System 'mvn' not detected in PATH.
echo [Maven Wrapper] Downloading portable Apache Maven 3.9.6...

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$distDir = Join-Path $env:USERPROFILE '.m2\wrapper\dists\apache-maven-3.9.6'; " ^
  "if (-not (Test-Path $distDir)) { New-Item -ItemType Directory -Path $distDir -Force | Out-Null }; " ^
  "$zipFile = Join-Path $distDir 'apache-maven-3.9.6-bin.zip'; " ^
  "Write-Host 'Downloading Maven from official Apache repository...'; " ^
  "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; " ^
  "Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip' -OutFile $zipFile; " ^
  "Write-Host 'Extracting Maven...'; " ^
  "Expand-Archive -Path $zipFile -DestinationPath $distDir -Force; " ^
  "Write-Host 'Maven installed successfully!'"

if exist "%MAVEN_HOME%\bin\mvn.cmd" (
    call "%MAVEN_HOME%\bin\mvn.cmd" %*
) else (
    echo [ERROR] Failed to run Maven. Please install Maven or check your internet connection.
    exit /b 1
)

:end
endlocal
