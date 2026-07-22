@ECHO OFF
SETLOCAL
SET DEST=%~dp0gradle\wrapper\gradle-wrapper.jar
SET URL=https://github.com/gradle/gradle/raw/refs/tags/v8.13.0/gradle/wrapper/gradle-wrapper.jar
IF NOT EXIST "%~dp0gradle\wrapper" MKDIR "%~dp0gradle\wrapper"
powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri '%URL%' -OutFile '%DEST%'"
IF ERRORLEVEL 1 (
  ECHO Failed to download the Gradle wrapper.
  EXIT /B 1
)
ECHO Gradle wrapper ready: %DEST%
ENDLOCAL
