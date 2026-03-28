@echo off
chcp 65001 >nul
title Calmara 心理健康智能体系统

echo.
echo ╔════════════════════════════════════════════════════════════╗
echo ║       Calmara 心理健康智能体系统                            ║
echo ║       一键启动脚本                                          ║
echo ╚════════════════════════════════════════════════════════════╝
echo.

set JAVA_HOME=D:\JDK17
set PATH=%JAVA_HOME%\bin;%PATH%

echo [1/4] 检查Java环境...
java -version >nul 2>&1
if errorlevel 1 (
    echo [错误] Java未安装或配置错误
    echo 请确保JDK 17已安装并配置JAVA_HOME环境变量
    pause
    exit /b 1
)
echo       Java环境正常

echo.
echo [2/4] 检查Docker环境...
docker --version >nul 2>&1
if errorlevel 1 (
    echo [警告] Docker未安装，将跳过服务自动启动
    echo        系统将以降级模式运行
    set DOCKER_AVAILABLE=false
) else (
    echo       Docker环境正常
    set DOCKER_AVAILABLE=true
)

echo.
echo [3/4] 启动依赖服务...
if "%DOCKER_AVAILABLE%"=="true" (
    echo       正在启动MySQL...
    docker start calmara-mysql >nul 2>&1 || (
        echo       创建并启动MySQL容器...
        docker run -d --name calmara-mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=calmara123 -e MYSQL_DATABASE=calmara -v calmara-mysql-data:/var/lib/mysql mysql:8.0 >nul 2>&1
    )

    echo       正在启动Redis...
    docker start calmara-redis >nul 2>&1 || (
        echo       创建并启动Redis容器...
        docker run -d --name calmara-redis -p 6379:6379 -v calmara-redis-data:/data redis:7-alpine >nul 2>&1
    )

    echo       正在启动Chroma...
    docker start calmara-chroma >nul 2>&1 || (
        echo       创建并启动Chroma容器...
        docker run -d --name calmara-chroma -p 8000:8000 -v calmara-chroma-data:/chroma/chroma chromadb/chroma:latest >nul 2>&1
    )

    echo       等待服务启动...
    timeout /t 10 /nobreak >nul
)

echo.
echo [4/4] 启动Calmara应用...
echo.

cd /d "%~dp0"

if exist "target\calmara-web-1.0.0.jar" (
    echo       使用已编译的JAR文件启动...
    java -jar target\calmara-web-1.0.0.jar
) else (
    echo       使用Maven启动...
    if exist "mvnw.cmd" (
        call mvnw.cmd spring-boot:run
    ) else (
        mvn spring-boot:run
    )
)

pause
