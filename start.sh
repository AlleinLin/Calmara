#!/bin/bash

echo ""
echo "╔════════════════════════════════════════════════════════════╗"
echo "║       Calmara 心理健康智能体系统                            ║"
echo "║       一键启动脚本                                          ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""

export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}
export PATH=$JAVA_HOME/bin:$PATH

echo "[1/4] 检查Java环境..."
if ! command -v java &> /dev/null; then
    echo "[错误] Java未安装"
    echo "请安装JDK 17: sudo apt install openjdk-17-jdk"
    exit 1
fi
echo "      Java环境正常: $(java -version 2>&1 | head -n 1)"

echo ""
echo "[2/4] 检查Docker环境..."
if ! command -v docker &> /dev/null; then
    echo "[警告] Docker未安装，将跳过服务自动启动"
    echo "       系统将以降级模式运行"
    DOCKER_AVAILABLE=false
else
    echo "      Docker环境正常"
    DOCKER_AVAILABLE=true
fi

echo ""
echo "[3/4] 启动依赖服务..."
if [ "$DOCKER_AVAILABLE" = true ]; then
    echo "      正在启动MySQL..."
    docker start calmara-mysql 2>/dev/null || {
        echo "      创建并启动MySQL容器..."
        docker run -d --name calmara-mysql \
            -p 3306:3306 \
            -e MYSQL_ROOT_PASSWORD=calmara123 \
            -e MYSQL_DATABASE=calmara \
            -v calmara-mysql-data:/var/lib/mysql \
            mysql:8.0 2>/dev/null
    }

    echo "      正在启动Redis..."
    docker start calmara-redis 2>/dev/null || {
        echo "      创建并启动Redis容器..."
        docker run -d --name calmara-redis \
            -p 6379:6379 \
            -v calmara-redis-data:/data \
            redis:7-alpine 2>/dev/null
    }

    echo "      正在启动Chroma..."
    docker start calmara-chroma 2>/dev/null || {
        echo "      创建并启动Chroma容器..."
        docker run -d --name calmara-chroma \
            -p 8000:8000 \
            -v calmara-chroma-data:/chroma/chroma \
            chromadb/chroma:latest 2>/dev/null
    }

    echo "      等待服务启动..."
    sleep 10
fi

echo ""
echo "[4/4] 启动Calmara应用..."
echo ""

cd "$(dirname "$0")"

if [ -f "target/calmara-web-1.0.0.jar" ]; then
    echo "      使用已编译的JAR文件启动..."
    java -jar target/calmara-web-1.0.0.jar
else
    echo "      使用Maven启动..."
    if [ -f "./mvnw" ]; then
        ./mvnw spring-boot:run
    else
        mvn spring-boot:run
    fi
fi
