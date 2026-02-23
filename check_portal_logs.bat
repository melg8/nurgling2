@echo off
REM Portal Marker Debug Log Analyzer - v2
REM Automatically detects log location (logs\ or bin\logs\)

echo ============================================
echo PORTAL MARKER SYSTEM - LOG ANALYSIS v2
echo ============================================
echo.
echo Current directory: %CD%
echo.

REM Detect log directory
set LOG_DIR=logs
if not exist logs\portal_marker_tracker_debug.log (
    if exist bin\logs\portal_marker_tracker_debug.log (
        set LOG_DIR=bin\logs
    )
)

echo Using log directory: %LOG_DIR%
echo.

if not exist %LOG_DIR%\ (
    echo ERROR: No log directory found!
    echo.
    echo Searching for log files...
    dir /s /b portal_marker_*.log 2>nul
    goto :end
)

echo Log files found:
dir /b %LOG_DIR%\*.log 2>nul
echo.

echo [1] CHECKING MAPFILE AVAILABILITY
echo -------------------------------------------
findstr /C:"[getMapFile]" %LOG_DIR%\portal_marker_linker_debug.log 2>nul
if errorlevel 1 echo No MapFile entries found
echo.

echo [2] CHECKING LAYER TRANSITIONS (different segments)
echo -------------------------------------------
findstr /C:"[onGridChanged] fromGrid" %LOG_DIR%\portal_marker_tracker_debug.log 2>nul
if errorlevel 1 echo No layer transitions detected
echo.

echo [3] CHECKING DUPLICATE PREVENTION
echo -------------------------------------------
findstr /C:"same as pending" %LOG_DIR%\portal_marker_tracker_debug.log 2>nul
if errorlevel 1 echo No duplicate prevention triggered (good - no boundary issue)
echo.

echo [4] CHECKING UID GENERATION
echo -------------------------------------------
findstr /C:"UID generated" %LOG_DIR%\portal_marker_linker_debug.log 2>nul
if errorlevel 1 echo No UIDs generated
echo.

echo [5] CHECKING MARKER CREATION
echo -------------------------------------------
findstr /C:"[createMarker]" %LOG_DIR%\portal_marker_linker_debug.log 2>nul
if errorlevel 1 echo No markers created
echo.

echo [6] CHECKING ERRORS
echo -------------------------------------------
findstr /C:"ERROR" %LOG_DIR%\portal_marker_tracker_debug.log 2>nul
findstr /C:"ERROR" %LOG_DIR%\portal_marker_linker_debug.log 2>nul
findstr /C:"MARKER_ERROR" %LOG_DIR%\marker_events.log 2>nul
if errorlevel 1 echo No marker errors
echo.

echo [7] CHECKING PENDING TRANSITIONS (retry logic)
echo -------------------------------------------
findstr /C:"pending" %LOG_DIR%\portal_marker_tracker_debug.log 2>nul
if errorlevel 1 echo No pending transitions
echo.

echo [8] CHECKING LOADING EXCEPTIONS
echo -------------------------------------------
findstr /C:"Loading" %LOG_DIR%\portal_marker_tracker_debug.log 2>nul
findstr /C:"Loading" %LOG_DIR%\portal_marker_linker_debug.log 2>nul
if errorlevel 1 echo No Loading exceptions
echo.

echo [9] LAST 10 MARKER EVENTS
echo -------------------------------------------
powershell -Command "Get-Content '%LOG_DIR%\marker_events.log' -Tail 10 2>$null"
echo.

echo [10] SUMMARY
echo -------------------------------------------
setlocal enabledelayedexpansion
set transitions=0
set duplicates=0
set uids=0
set markers=0
set errors=0
set loading=0

for /f "tokens=*" %%a in ('findstr /C:"[onGridChanged] fromGrid" %LOG_DIR%\portal_marker_tracker_debug.log 2^>nul') do set /a transitions+=1
for /f "tokens=*" %%a in ('findstr /C:"same as pending" %LOG_DIR%\portal_marker_tracker_debug.log 2^>nul') do set /a duplicates+=1
for /f "tokens=*" %%a in ('findstr /C:"UID generated" %LOG_DIR%\portal_marker_linker_debug.log 2^>nul') do set /a uids+=1
for /f "tokens=*" %%a in ('findstr /C:"[createMarker] Marker added" %LOG_DIR%\portal_marker_linker_debug.log 2^>nul') do set /a markers+=1
for /f "tokens=*" %%a in ('findstr /C:"ERROR" %LOG_DIR%\portal_marker_tracker_debug.log %LOG_DIR%\portal_marker_linker_debug.log %LOG_DIR%\marker_events.log 2^>nul') do set /a errors+=1
for /f "tokens=*" %%a in ('findstr /C:"Loading" %LOG_DIR%\portal_marker_tracker_debug.log %LOG_DIR%\portal_marker_linker_debug.log 2^>nul') do set /a loading+=1

echo Transitions detected: %transitions%
echo Duplicates prevented: %duplicates%
echo UIDs generated: %uids%
echo Markers CREATED: %markers%
echo Errors: %errors%
echo Loading exceptions: %loading%
echo.

if %markers% GTR 0 (
    echo *** SUCCESS: Markers were created! ***
) else (
    echo *** ISSUE: No markers created yet ***
)
echo.
echo ============================================

:end
pause
