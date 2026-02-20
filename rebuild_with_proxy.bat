@echo off
setlocal

REM Full rebuild script with SOCKS proxy support
REM Cleans all build artifacts and rebuilds from scratch
REM Uses proxy settings: SOCKS 127.0.0.1:9050

set ANT_OPTS=-DsocksProxyHost=127.0.0.1 -DsocksProxyPort=9050

echo Full rebuild with SOCKS proxy (127.0.0.1:9050)...
echo ANT_OPTS=%ANT_OPTS%
echo.

cd /d "%~dp0"

echo Cleaning build directories...
if exist "build" (
    echo   Removing build/
    rmdir /s /q build
)
if exist "bin\hafen.jar" (
    echo   Removing bin\hafen.jar
    del /q bin\hafen.jar
)
if exist "bin\*.res" (
    echo   Removing bin\*.res
    del /q bin\*.res
)
if exist "lib\ext" (
    echo   Removing lib\ext\ (downloaded dependencies)
    rmdir /s /q lib\ext
)
if exist "META-INF" (
    echo   Removing META-INF/
    rmdir /s /q META-INF
)
if exist "run_output.txt" (
    echo   Removing run_output.txt
    del /q run_output.txt
)

echo.
echo Building from scratch...
ant bin

echo.
echo Rebuild complete.

endlocal
