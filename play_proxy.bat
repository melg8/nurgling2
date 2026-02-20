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

REM Run the game with Java 21+ settings and SOCKS proxy
"%JAVA_CMD%" ^
    -DsocksProxyHost=127.0.0.1 ^
    -DsocksProxyPort=2080 ^
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
