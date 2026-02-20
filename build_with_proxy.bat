@echo off
setlocal

REM Build script with SOCKS proxy support
REM Uses proxy settings: SOCKS 127.0.0.1:9050

set ANT_OPTS=-DsocksProxyHost=127.0.0.1 -DsocksProxyPort=9050

echo Building with SOCKS proxy (127.0.0.1:9050)...
echo ANT_OPTS=%ANT_OPTS%
echo.

cd /d "%~dp0"
ant %*

endlocal
