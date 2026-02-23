@echo off
REM Portal Marker Debug Log Analyzer
REM Run this after exiting a cave to check if markers were created correctly

echo ============================================
echo PORTAL MARKER SYSTEM - LOG ANALYSIS
echo ============================================
echo.

echo [1] CHECKING MAPFILE AVAILABILITY
echo -------------------------------------------
findstr /C:"[getMapFile]" logs\portal_marker_linker_debug.log
if errorlevel 1 echo No MapFile entries found
echo.

echo [2] CHECKING LAYER TRANSITIONS (different segments)
echo -------------------------------------------
findstr /C:"[onGridChanged] fromGrid" logs\portal_marker_tracker_debug.log
if errorlevel 1 echo No layer transitions detected
echo.

echo [3] CHECKING UID GENERATION
echo -------------------------------------------
findstr /C:"UID generated" logs\portal_marker_linker_debug.log
if errorlevel 1 echo No UIDs generated
echo.

echo [4] CHECKING MARKER CREATION
echo -------------------------------------------
findstr /C:"[createMarker]" logs\portal_marker_linker_debug.log
if errorlevel 1 echo No markers created
echo.

echo [5] CHECKING ERRORS
echo -------------------------------------------
findstr /C:"ERROR" logs\portal_marker_tracker_debug.log
findstr /C:"ERROR" logs\portal_marker_linker_debug.log
findstr /C:"MARKER_ERROR" logs\marker_events.log
if errorlevel 1 echo No marker errors
echo.

echo [6] CHECKING PENDING TRANSITIONS (retry logic)
echo -------------------------------------------
findstr /C:"pending" logs\portal_marker_tracker_debug.log
if errorlevel 1 echo No pending transitions
echo.

echo [7] LAST 10 MARKER EVENTS
echo -------------------------------------------
powershell -Command "Get-Content 'logs\marker_events.log' -Tail 10 2>$null"
echo.

echo [8] SUMMARY
echo -------------------------------------------
setlocal enabledelayedexpansion
set transitions=0
set uids=0
set markers=0
set errors=0

for /f "tokens=*" %%a in ('findstr /C:"[onGridChanged] fromGrid" logs\portal_marker_tracker_debug.log 2^>nul') do set /a transitions+=1
for /f "tokens=*" %%a in ('findstr /C:"UID generated" logs\portal_marker_linker_debug.log 2^>nul') do set /a uids+=1
for /f "tokens=*" %%a in ('findstr /C:"[createMarker]" logs\portal_marker_linker_debug.log 2^>nul') do set /a markers+=1
for /f "tokens=*" %%a in ('findstr /C:"ERROR" logs\portal_marker_tracker_debug.log logs\portal_marker_linker_debug.log logs\marker_events.log 2^>nul') do set /a errors+=1

echo Transitions detected: %transitions%
echo UIDs generated: %uids%
echo Markers created: %markers%
echo Errors: %errors%
echo.
echo ============================================
endlocal
