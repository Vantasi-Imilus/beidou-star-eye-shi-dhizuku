@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

set "VERSION=0.3.0"

echo ==========================================
echo   Beidou Star Eye - One-Click Build
echo ==========================================

rem ---- Locate JDK: JAVA_HOME first, then common install locations, finally PATH ----
set "JDK="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JDK=%JAVA_HOME%"
if not defined JDK if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe" set "JDK=%ProgramFiles%\Android\Android Studio\jbr"
if not defined JDK if exist "%ProgramFiles%\Java\jdk-21\bin\java.exe" set "JDK=%ProgramFiles%\Java\jdk-21"
if not defined JDK if exist "%ProgramFiles%\Eclipse Adoptium\jdk-21.0.2-hotspot\bin\java.exe" set "JDK=%ProgramFiles%\Eclipse Adoptium\jdk-21.0.2-hotspot"
if not defined JDK if exist "%LOCALAPPDATA%\Programs\Eclipse Adoptium\jdk-21.0.2-hotspot\bin\java.exe" set "JDK=%LOCALAPPDATA%\Programs\Eclipse Adoptium\jdk-21.0.2-hotspot"

if defined JDK (
    set "JAVA_HOME=!JDK!"
    echo [INFO] Using JDK: !JAVA_HOME!
) else (
    where java >nul 2>nul
    if errorlevel 1 (
        echo [ERROR] No JDK found. Install JDK 21 or set JAVA_HOME.
        pause
        exit /b 1
    )
    echo [INFO] No standalone JDK found, using java from PATH ^(JDK 17+ required, 21 recommended^)
)

rem ---- Build (debug for device testing; release reuses debug signing, no R8) ----
echo [INFO] Building debug + release APK ...
call "%~dp0gradlew.bat" :app:assembleDebug :app:assembleRelease --console=plain
if errorlevel 1 (
    echo.
    echo [FAIL] Build failed, see log above.
    pause
    exit /b 1
)

rem ---- Copy APK ----
if not exist dist mkdir dist
if exist "app\build\outputs\apk\debug\app-debug.apk" (
    copy /y "app\build\outputs\apk\debug\app-debug.apk" "dist\BeidouStarEye-v%VERSION%-debug.apk" >nul
    echo [OK] APK(debug):   dist\BeidouStarEye-v%VERSION%-debug.apk
)
if exist "app\build\outputs\apk\release\app-release.apk" (
    copy /y "app\build\outputs\apk\release\app-release.apk" "dist\BeidouStarEye-v%VERSION%-release.apk" >nul
    echo [OK] APK(release): dist\BeidouStarEye-v%VERSION%-release.apk
)

rem ---- Package source ----
echo [INFO] Packaging source ...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0package-source.ps1" -Version %VERSION%
if errorlevel 1 (
    echo [FAIL] Source packaging failed.
    pause
    exit /b 1
)
echo [OK] Source: dist\BeidouStarEye-src-v%VERSION%.zip

echo.
echo ============ Done ============
pause
