#!/bin/bash

# Script para parar a aplicação Portal Serasa
# Uso: ./stop.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "Parando Portal Serasa..."

# Encerra o processo Java do Spring Boot (PortalSerasaApplication / portal-serasa)
if pgrep -f "PortalSerasaApplication" >/dev/null 2>&1; then
    pkill -f "PortalSerasaApplication"
    echo "Aplicação encerrada."
elif pgrep -f "portal-serasa" >/dev/null 2>&1; then
    pkill -f "portal-serasa"
    echo "Aplicação encerrada."
else
    echo "Nenhuma instância do Portal Serasa em execução."
fi
