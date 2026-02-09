#!/bin/bash

# Script para executar a aplicação Portal Serasa
# Uso: ./run.sh [comando]
# Comandos: run (default) | build | package
#
# Antes de rodar: docker compose up -d postgres
# Configure CNPJA_API_KEY em .env para o endpoint /company/enrich

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Carrega variáveis do .env se existir
if [ -f .env ]; then
    set -a
    source .env
    set +a
fi

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
        if [ ! -d "target/classes" ] || [ ! -f "target/classes/com/portal/serasa/infrastructure/persistence/mapper/ClientEntityMapperImpl.class" ]; then
            echo "Compilando antes da primeira execução..."
            $MVN_CMD clean compile -q
        fi
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
