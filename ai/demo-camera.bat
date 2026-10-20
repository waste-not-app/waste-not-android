@echo off
setlocal
pushd "%~dp0.."
if not exist ".venv-ai\Scripts\python.exe" (
    echo Virtual environment belum tersedia. Ikuti instalasi di ai\README.md.
    popd
    pause
    exit /b 1
)
".venv-ai\Scripts\python.exe" -m wastenot_ai.cli demo --demo-guides %*
set "WASTENOT_DEMO_EXIT=%ERRORLEVEL%"
popd
if not "%WASTENOT_DEMO_EXIT%"=="0" pause
exit /b %WASTENOT_DEMO_EXIT%
