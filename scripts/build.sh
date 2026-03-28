#!/bin/bash

set -e

echo "Building Calmara application..."

cd "$(dirname "$0")/.."

mvn clean package -DskipTests

echo "Build completed!"
echo "JAR file: Calmara-web/target/*.jar"
