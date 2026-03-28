#!/bin/bash

set -e

echo "=========================================="
echo "   Calmara 部署脚本"
echo "=========================================="

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_ROOT"

check_requirements() {
    echo "检查依赖..."
    
    if ! command -v docker &> /dev/null; then
        echo "错误: Docker 未安装"
        echo "请访问 https://docs.docker.com/get-docker/ 安装 Docker"
        exit 1
    fi
    
    if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
        echo "错误: Docker Compose 未安装"
        echo "请访问 https://docs.docker.com/compose/install/ 安装 Docker Compose"
        exit 1
    fi
    
    echo "✓ Docker 已安装"
    echo "✓ Docker Compose 已安装"
}

check_env_file() {
    if [ ! -f ".env" ]; then
        echo "创建 .env 文件..."
        cp .env.example .env
        echo "✓ .env 文件已创建"
        echo ""
        echo "请编辑 .env 文件配置您的环境变量，然后重新运行此脚本"
        echo "重要配置项:"
        echo "  - JWT_SECRET: JWT密钥 (至少64字符)"
        echo "  - DB_PASSWORD: 数据库密码"
        echo "  - REDIS_PASSWORD: Redis密码"
        exit 0
    fi
    echo "✓ .env 文件存在"
}

pull_models() {
    echo ""
    echo "拉取 Ollama 模型..."
    
    if command -v ollama &> /dev/null; then
        echo "拉取 qwen2.5:7b-chat 模型 (这可能需要一些时间)..."
        ollama pull qwen2.5:7b-chat || echo "警告: 模型拉取失败，请手动拉取"
    else
        echo "Ollama 未在本地安装，跳过模型拉取"
    fi
}

deploy_chroma() {
    echo ""
    echo "=========================================="
    echo "   部署模式: Chroma (轻量级)"
    echo "=========================================="
    
    docker-compose up -d mysql redis chroma bge-m3 ollama
    
    echo ""
    echo "等待服务启动..."
    sleep 30
    
    echo ""
    echo "启动 Calmara 应用..."
    docker-compose up -d calmara-app
    
    echo ""
    echo "=========================================="
    echo "   部署完成!"
    echo "=========================================="
    echo ""
    echo "服务地址:"
    echo "  - Calmara API: http://localhost:8080"
    echo "  - Chroma: http://localhost:8000"
    echo "  - BGE-M3: http://localhost:33330"
    echo "  - Ollama: http://localhost:11434"
    echo ""
    echo "查看日志: docker-compose logs -f calmara-app"
}

deploy_milvus() {
    echo ""
    echo "=========================================="
    echo "   部署模式: Milvus (生产级)"
    echo "=========================================="
    
    docker-compose --profile milvus up -d mysql redis etcd minio milvus bge-m3 ollama
    
    echo ""
    echo "等待服务启动..."
    sleep 60
    
    echo ""
    echo "启动 Calmara 应用..."
    docker-compose --profile milvus up -d calmara-app
    
    echo ""
    echo "=========================================="
    echo "   部署完成!"
    echo "=========================================="
    echo ""
    echo "服务地址:"
    echo "  - Calmara API: http://localhost:8080"
    echo "  - Milvus: localhost:19530"
    echo "  - BGE-M3: http://localhost:33330"
    echo "  - Ollama: http://localhost:11434"
    echo ""
    echo "查看日志: docker-compose --profile milvus logs -f calmara-app"
}

show_help() {
    echo "用法: $0 [选项]"
    echo ""
    echo "选项:"
    echo "  chroma    使用 Chroma 部署 (轻量级，推荐开发/测试)"
    echo "  milvus    使用 Milvus 部署 (生产级，推荐生产环境)"
    echo "  stop      停止所有服务"
    echo "  restart   重启所有服务"
    echo "  logs      查看日志"
    echo "  clean     清理所有数据 (危险操作)"
    echo "  help      显示此帮助信息"
    echo ""
    echo "示例:"
    echo "  $0 chroma     # 使用 Chroma 部署"
    echo "  $0 milvus     # 使用 Milvus 部署"
}

stop_services() {
    echo "停止所有服务..."
    docker-compose --profile milvus down
    echo "✓ 服务已停止"
}

restart_services() {
    echo "重启所有服务..."
    docker-compose --profile milvus restart
    echo "✓ 服务已重启"
}

show_logs() {
    docker-compose --profile milvus logs -f
}

clean_all() {
    echo "警告: 这将删除所有数据!"
    read -p "确定要继续吗? (yes/no): " confirm
    
    if [ "$confirm" = "yes" ]; then
        docker-compose --profile milvus down -v
        echo "✓ 所有数据已清理"
    else
        echo "操作已取消"
    fi
}

main() {
    check_requirements
    check_env_file
    
    case "${1:-help}" in
        chroma)
            pull_models
            deploy_chroma
            ;;
        milvus)
            pull_models
            deploy_milvus
            ;;
        stop)
            stop_services
            ;;
        restart)
            restart_services
            ;;
        logs)
            show_logs
            ;;
        clean)
            clean_all
            ;;
        help|--help|-h)
            show_help
            ;;
        *)
            echo "未知选项: $1"
            echo ""
            show_help
            exit 1
            ;;
    esac
}

main "$@"
