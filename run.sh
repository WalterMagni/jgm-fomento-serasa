#!/bin/bash

# Script para executar a aplicação Portal Serasa
# Uso: ./run.sh [comando]
# Comandos: run (default) | build | package

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [ -f "./mvnw" ]; then
    chmod +x ./mvnw 2>/dev/null || true
    MVN_CMD="./mvnw"
else
    MVN_CMD="mvn"
fi

CMD="${1:-run}"

case "$CMD" in
    run)
        echo "Iniciando Portal Serasa..."
        $MVN_CMD spring-boot:run
        ;;
    build)
        echo "Compilando projeto..."
        $MVN_CMD clean compile
        ;;
    package)
        echo "Empacotando projeto..."
        $MVN_CMD clean package -DskipTests
        ;;
    *)
        echo "Uso: $0 {run|build|package}"
        echo "  run     - Executa a aplicação (padrão)"
        echo "  build   - Compila o projeto"
        echo "  package - Gera o JAR do projeto"
        exit 1
        ;;
esac
