@echo off
chcp 65001 >nul
title Calmara 系统停止

echo.
echo ╔════════════════════════════════════════════════════════════╗
echo ║       Calmara 心理健康智能体系统                            ║
echo ║       停止脚本                                              ║
echo ╚════════════════════════════════════════════════════════════╝
echo.

echo [1/2] 停止Java应用...
for /f "tokens=2" %%i in ('tasklist ^| findstr /i "java.*calmara"') do (
    taskkill /pid %%i /f >nul 2>&1
)
echo       Java应用已停止

echo.
echo [2/2] 停止Docker服务...
docker stop calmara-mysql calmara-redis calmara-chroma >nul 2>&1
echo       Docker服务已停止

echo.
echo 所有服务已停止
pause
