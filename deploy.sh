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

# A cópia local do cadastro da Receita (filiais, quadro societário, alerta de partes ligadas)
# vive num compose separado — o cnpj-data-pipeline. Sobe primeiro para que a rede externa
# exista e o serviço cnpj-indexes consiga aplicar os índices logo em seguida.
if [ "${CNPJ_DB_ENABLED:-false}" = "true" ]; then
  if [ -d "${CNPJ_PIPELINE_DIR:-}" ]; then
    echo "→ Subindo o banco da Receita (cnpj-data-pipeline)..."
    docker compose -f "$CNPJ_PIPELINE_DIR/docker-compose.yml" up -d
  else
    # Sem o diretório da pipeline configurado, garante ao menos que o container do banco
    # (que pode ter ficado parado — reinício do host, conflito de porta, etc.) volte a subir.
    # Equivalente ao `docker start cnpj-pipeline-postgres` manual.
    CONTAINER="${CNPJ_DB_CONTAINER:-${CNPJ_DB_HOST:-cnpj-pipeline-postgres}}"
    echo "→ Garantindo que o container do banco da Receita (${CONTAINER}) está rodando..."
    if ! docker start "$CONTAINER" 2>/dev/null; then
      echo "AVISO: não consegui iniciar o container '${CONTAINER}' (não existe nesta máquina ou nome diferente)."
      echo "       Ajuste CNPJ_DB_CONTAINER no .env, ou defina CNPJ_PIPELINE_DIR pra subir o compose completo."
      echo "       Sem ele, filiais e quadro societário ficam 503."
    fi
  fi
fi

echo "→ Build e subida dos containers..."
docker compose pull postgres 2>/dev/null || true
docker compose build --no-cache
docker compose up -d

# One-shot: sai sozinho depois de aplicar. Mostrado à parte porque `compose ps` só lista
# os que continuam de pé.
echo ""
echo "→ Índices da base da Receita:"
docker compose logs --no-log-prefix cnpj-indexes 2>/dev/null | tail -5 || true

echo ""
echo "✓ Containers rodando:"
docker compose ps

echo ""
echo "✓ Portal disponível em: $NEXT_PUBLIC_API_URL"
echo "  Frontend: http://$(hostname -I | awk '{print $1}'):3001"
echo "  Backend:  http://$(hostname -I | awk '{print $1}'):8081"
