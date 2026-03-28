@echo off
SETLOCAL

echo "=========================================="
echo "   Calmara Deployment Script (Windows)"
echo "=========================================="
echo.

REM Check if Docker is installed
docker --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Docker is not installed
    echo Please install Docker Desktop from https://www.docker.com/products/docker-desktop
    exit /b 1
)

echo [OK] Docker is installed
echo.

REM Check if Docker Compose is installed
docker-compose --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Docker Compose is not installed
    echo Please install Docker Compose
    exit /b 1
)

echo [OK] Docker Compose is installed
echo.

REM Check if .env file exists
if not exist .env (
    echo Creating .env file from .env.example...
    copy .env.example .env
    echo [OK] .env file created
    echo.
    echo [IMPORTANT] Please edit .env file and configure:
    echo   - JWT_SECRET: JWT secret key (at least 64 characters^)
    echo   - DB_PASSWORD: Database password
    echo.
    echo After configuration, run this script again.
    pause
)

echo [OK] Environment file exists
echo.

REM Parse command line arguments
set MODE=chroma
if "%1"=="milvus" (
    set MODE=milvus
)

echo Deployment mode: %MODE%
echo.

echo Starting services...
echo.

if "%MODE%"=="milvus" (
    echo Starting Milvus stack...
    docker-compose --profile milvus up -d mysql redis etcd minio milvus bge-m3 ollama
    timeout /t 60 >nul
    echo.
    echo Starting Calmara application...
    docker-compose --profile milvus up -d calmara-app
) else (
    echo Starting Chroma stack...
    docker-compose up -d mysql redis chroma bge-m3 ollama
    timeout /t 30 >nul
    echo.
    echo Starting Calmara application...
    docker-compose up -d calmara-app
)

echo.
echo ==========================================
echo   Deployment Complete!
echo ==========================================
echo.
echo Services:
echo   - Calmara API: http://localhost:8080
echo   - Chroma: http://localhost:8000
if "%MODE%"=="milvus" (
    echo   - Milvus: localhost:19530
)
echo   - BGE-M3: http://localhost:33330
echo   - Ollama: http://localhost:11434
echo.
echo View logs: docker-compose logs -f calmara-app
