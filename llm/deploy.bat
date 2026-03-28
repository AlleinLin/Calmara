@echo off
chcp 65001 >nul
echo ============================================================
echo Calmara - Ollama 部署脚本
echo ============================================================
echo.

REM 检查Ollama是否安装
echo [1/3] 检查Ollama安装状态...
where ollama >nul 2>&1
if %errorlevel% neq 0 (
    echo 错误: Ollama未安装
    echo 请先安装 Ollama: https://ollama.com/download
    pause
    exit /b 1
)
echo   Ollama已安装

REM 检查Ollama服务是否运行
echo.
echo [2/3] 检查Ollama服务状态...
curl -s http://localhost:11434/api/tags >nul 2>&1
if %errorlevel% neq 0 (
    echo   Ollama服务未运行，正在启动...
    start "" ollama serve
    timeout /t 5 /nobreak >nul
) else (
    echo   Ollama服务正在运行
)

REM 创建Modelfile
echo.
echo [3/3] 创建Calmara模型...
cd /d "%~dp0"

if exist "output\calmara-qwen2.5-7b.gguf" (
    echo   使用本地微调模型...
    copy /Y output\Modelfile . 2>nul
) else (
    echo   使用基础Qwen2.5-7B模型...
    echo   注意: 请先完成模型微调训练
)

REM 创建Ollama模型
echo.
echo 创建Ollama模型定义...
(
echo # Calara Qwen2.5-7B 心理对话模型
echo FROM qwen2.5:7b-chat
echo.
echo PARAMETER temperature 0.7
echo PARAMETER top_p 0.9
echo PARAMETER num_ctx 4096
echo.
echo SYSTEM """你是一个温暖、专业、富有同理心的校园心理咨询助手，名为Calmara。
你的职责是提供专业、温暖的心理支持，识别高风险情况并提供危机干预资源。
如果用户有自杀或自伤倾向，请立即提供心理援助热线。"""
) > Modelfile.calmara

echo.
echo ============================================================
echo 部署完成！
echo ============================================================
echo.
echo 可用命令:
echo   ollama run calmara     - 运行Calmara对话
echo   ollama list           - 查看已加载的模型
echo   curl http://localhost:11434 - 检查服务状态
echo.
echo API调用示例:
echo   curl -X POST http://localhost:11434/api/generate ^
echo     -d "{\"model\": \"calmara\", \"prompt\": \"我最近很焦虑\"}"
echo.
pause
