@echo off
REM Reset script for Nurgling cave connections testing
REM Deletes log files and connection data for clean testing

echo ========================================
echo Nurgling Test Reset
echo ========================================

cd /d "%~dp0"

echo.
echo Deleting log files...

if exist "nurgling_markers.log" (
    del /q "nurgling_markers.log"
    echo   [OK] nurgling_markers.log deleted
) else (
    echo   [--] nurgling_markers.log not found
)

if exist "nurgling_markobjs.log" (
    del /q "nurgling_markobjs.log"
    echo   [OK] nurgling_markobjs.log deleted
) else (
    echo   [--] nurgling_markobjs.log not found
)

if exist "cave_connections.json" (
    del /q "cave_connections.json"
    echo   [OK] cave_connections.json deleted
) else (
    echo   [--] cave_connections.json not found
)

echo.
echo ========================================
echo Reset complete!
echo ========================================
echo.
echo Starting game...
echo.

@echo off
setlocal

REM Use Java 21+ for running the game (required for --add-exports)
REM Check common installation locations
set JAVA_CMD=

if exist "C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot\bin\java.exe" (
    set JAVA_CMD=C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot\bin\java.exe
) else if exist "C:\Program Files\Eclipse Adoptium\jdk-21*\bin\java.exe" (
    for %%i in ("C:\Program Files\Eclipse Adoptium\jdk-21*\bin\java.exe") do set JAVA_CMD=%%i
) else if exist "C:\Program Files\Java\jdk-21*\bin\java.exe" (
    for %%i in ("C:\Program Files\Java\jdk-21*\bin\java.exe") do set JAVA_CMD=%%i
) else if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" (
    "%JAVA_HOME%\bin\java.exe" --version >nul 2>&1
    if not errorlevel 1 (
        for /f "tokens=3" %%v in ('"%JAVA_HOME%\bin\java.exe" -version 2^>^&1 ^| findstr /i "version"') do (
            set "JAVA_VER=%%v"
        )
        if "!JAVA_VER!" geq "21" set JAVA_CMD=%JAVA_HOME%\bin\java.exe
    )
)

if not defined JAVA_CMD (
    echo Java 21+ not found! Please install Java 21 or later.
    echo Download from: https://adoptium.net/
    pause
    exit /b 1
)

echo Using Java: %JAVA_CMD%
"%JAVA_CMD%" --version

cd /d "%~dp0"
cd bin

REM Run the game with Java 21+ settings
"%JAVA_CMD%" ^
    -Dsun.java2d.uiScale.enabled=false ^
    -Dsun.java2d.win.uiScaleX=1.0 ^
    -Dsun.java2d.win.uiScaleY=1.0 ^
    -Xss8m ^
    -Xms1024m ^
    -Xmx4096m ^
    --add-exports java.base/java.lang=ALL-UNNAMED ^
    --add-exports java.desktop/sun.awt=ALL-UNNAMED ^
    --add-exports java.desktop/sun.java2d=ALL-UNNAMED ^
    --enable-native-access=ALL-UNNAMED ^
    -DrunningThroughSteam=false ^
    -jar hafen.jar

pause

