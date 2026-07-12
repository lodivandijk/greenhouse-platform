@echo off
setlocal
set GRADLE_VERSION=9.2.1
set PROJECT_DIR=%~dp0
set WRAPPER_DIR=%USERPROFILE%\.gradle\greenhouse-wrapper
set GRADLE_HOME_DIR=%WRAPPER_DIR%\gradle-%GRADLE_VERSION%
set GRADLE_BIN=%GRADLE_HOME_DIR%\bin\gradle.bat
set ZIP_FILE=%WRAPPER_DIR%\gradle-%GRADLE_VERSION%-bin.zip

if not exist "%GRADLE_BIN%" (
  if not exist "%WRAPPER_DIR%" mkdir "%WRAPPER_DIR%"
  if not exist "%ZIP_FILE%" (
    echo Downloading Gradle %GRADLE_VERSION%...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%ZIP_FILE%'"
  )
  echo Installing Gradle %GRADLE_VERSION%...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%ZIP_FILE%' -DestinationPath '%WRAPPER_DIR%' -Force"
)

call "%GRADLE_BIN%" -p "%PROJECT_DIR%" %*
endlocal
