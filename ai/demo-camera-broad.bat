@echo off
setlocal
if not exist "%~dp0artifacts\training\broad-waste-v2-yolo11s\weights\best.pt" (
    echo Checkpoint belum tersedia. Ikuti ai\docs\BROAD_WASTE_TRAINING.md.
    exit /b 1
)
call "%~dp0demo-camera.bat" --device cpu --input-shape square --model "%~dp0artifacts\training\broad-waste-v2-yolo11s\weights\best.pt" %*
exit /b %ERRORLEVEL%
