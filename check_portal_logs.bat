@echo off
REM Portal Marker Debug Log Analyzer
REM Run this after exiting a cave to check if markers were created correctly

echo ============================================
echo PORTAL MARKER SYSTEM - LOG ANALYSIS
echo ============================================
echo.

echo [1] CHECKING MAPFILE AVAILABILITY
echo -------------------------------------------
type logs\portal_marker_linker_debug.log 2>nul | findstr /C:"[getMapFile]"
if errorlevel 1 echo No MapFile entries found
echo.

echo [2] CHECKING LAYER TRANSITIONS (different segments)
echo -------------------------------------------
type logs\portal_marker_tracker_debug.log 2>nul | findstr /C:"[onGridChanged] fromGrid"
if errorlevel 1 echo No layer transitions detected
echo.

echo [3] CHECKING UID GENERATION
echo -------------------------------------------
type logs\portal_marker_linker_debug.log 2>nul | findstr /C:"UID generated"
if errorlevel 1 echo No UIDs generated
echo.

echo [4] CHECKING MARKER CREATION
echo -------------------------------------------
type logs\portal_marker_linker_debug.log 2>nul | findstr /C:"[createMarker]"
if errorlevel 1 echo No markers created
echo.

echo [5] CHECKING ERRORS
echo -------------------------------------------
type logs\portal_marker_tracker_debug.log 2>nul | findstr /C:"ERROR"
type logs\portal_marker_linker_debug.log 2>nul | findstr /C:"ERROR"
type logs\marker_events.log 2>nul | findstr /C:"MARKER_ERROR"
if errorlevel 1 echo No marker errors
echo.

echo [6] CHECKING PENDING TRANSITIONS (retry logic)
echo -------------------------------------------
type logs\portal_marker_tracker_debug.log 2>nul | findstr /C:"pending"
if errorlevel 1 echo No pending transitions
echo.

echo [7] LAST 10 MARKER EVENTS
echo -------------------------------------------
powershell -Command "Get-Content 'logs\marker_events.log' -Tail 10 2>$null"
if errorlevel 1 echo No marker events
echo.

echo [8] SUMMARY
echo -------------------------------------------
set /a transitions=0
set /a uids=0
set /a markers=0
set /a errors=0

for /f "delims=" %%a in ('type logs\portal_marker_tracker_debug.log 2^>nul ^| find /C "fromGrid"') do set transitions=%%a
for /f "delims=" %%a in ('type logs\portal_marker_linker_debug.log 2^>nul ^| find /C "UID generated"') do set uids=%%a
for /f "delims=" %%a in ('type logs\portal_marker_linker_debug.log 2^>nul ^| find /C "[createMarker]"') do set markers=%%a
for /f "delims=" %%a in ('type logs\portal_marker_tracker_debug.log logs\portal_marker_linker_debug.log logs\marker_events.log 2^>nul ^| find /C "ERROR"') do set errors=%%a

echo Transitions detected: %transitions%
echo UIDs generated: %uids%
echo Markers created: %markers%
echo Errors: %errors%
echo.
echo ============================================
