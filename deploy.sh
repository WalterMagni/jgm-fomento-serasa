#!/bin/bash
set -e

echo "=== Portal Serasa — Deploy ==="

# Verifica se .env existe
if [ ! -f .env ]; then
  echo "ERRO: arquivo .env não encontrado."
  echo "Copie o .env.example e preencha as variáveis:"
  echo "  cp .env.example .env && nano .env"
  exit 1
fi

# Verifica variáveis obrigatórias
source .env
for VAR in POSTGRES_PASSWORD JWT_SECRET NEXT_PUBLIC_API_URL; do
  if [ -z "${!VAR}" ]; then
    echo "ERRO: variável $VAR não definida no .env"
    exit 1
  fi
done

echo "→ Build e subida dos containers..."
docker compose pull postgres 2>/dev/null || true
docker compose build --no-cache
docker compose up -d

echo ""
echo "✓ Containers rodando:"
docker compose ps

echo ""
echo "✓ Portal disponível em: $NEXT_PUBLIC_API_URL"
echo "  Frontend: http://$(hostname -I | awk '{print $1}'):3001"
echo "  Backend:  http://$(hostname -I | awk '{print $1}'):8081"
