@echo off
setlocal
if not exist "%~dp0artifacts\training\drinking-waste-v2-yolo11n\weights\best.pt" (
    echo Checkpoint training belum tersedia. Ikuti ai\docs\FINE_TUNING.md.
    exit /b 1
)
call "%~dp0demo-camera.bat" --device cpu --model "%~dp0artifacts\training\drinking-waste-v2-yolo11n\weights\best.pt" %*
exit /b %ERRORLEVEL%
