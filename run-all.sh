#!/bin/bash

# Este script orquestra toda a stack da aplicação "Portal Serasa"
# 1. Inicia o banco (Docker)
# 2. Inicia o Back-End (Spring Boot)
# 3. Inicia o Front-End (Next.js)

set -e

# Configura as cores para o output do terminal
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Definir o diretório raiz
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo -e "${BLUE}=== Inicializando Stack Portal Serasa ===${NC}"

cleanup() {
  if [ ! -z "${BACKEND_PID:-}" ]; then
    kill -TERM "$BACKEND_PID" 2>/dev/null || true
  fi
}

trap cleanup EXIT

# ----------
# 1. BANCO DE DADOS (DOCKER)
# ----------
echo -e "${GREEN}[1/3] Verificando Banco de Dados PostgreSQL (Docker Compose)...${NC}"
if ! docker info > /dev/null 2>&1; then
  echo -e "${RED}Erro: O Docker não parece estar rodando. Por favor, inicie o Docker Desktop.${NC}"
  exit 1
fi
docker compose up -d postgres

# Espera o banco ficar saudável
echo "Aguardando PostgreSQL ficar pronto..."
for i in {1..30}; do
  DB_STATUS=$(docker inspect -f '{{.State.Health.Status}}' portal-serasa-db 2>/dev/null || echo "starting")
  if [ "$DB_STATUS" = "healthy" ]; then
    break
  fi
  sleep 2
done

if [ "$DB_STATUS" != "healthy" ]; then
  echo -e "${RED}Erro: PostgreSQL não ficou saudável a tempo.${NC}"
  docker compose logs --tail=60 postgres || true
  exit 1
fi

# ----------
# 2. BACKEND API (SPRING BOOT) NO BACKGROUND
# ----------
echo -e "${GREEN}[2/3] Compilando e iniciando API Backend (Spring Boot)...${NC}"
# Mata qualquer processo anterior rodando na 8080 localmente
PORT_PID=$(lsof -t -i:8080 || true)
if [ ! -z "$PORT_PID" ]; then
    echo "Limpando porta 8080..."
    kill -9 $PORT_PID 2>/dev/null || true
fi

if [ -f "./mvnw" ]; then
    chmod +x ./mvnw 2>/dev/null || true
    MVN_CMD="./mvnw"
else
    MVN_CMD="mvn"
fi

# Carrega variáveis do .env se existir
if [ -f ".env" ]; then
    echo "Carregando variáveis de .env..."
    set -a && source .env && set +a
fi

# Em dev local o postgres roda na porta 5433 (mapeada no docker-compose.yml)
# O Spring Boot precisa saber disso via variável de ambiente
export DB_HOST=localhost
export DB_PORT=5433
export NEXT_PUBLIC_API_URL=http://localhost:8080/api/v1

# Prepara e compila primeiro para falhar cedo se houver erro
mkdir -p temp
BACKEND_LOG="temp/backend.log"
rm -f "$BACKEND_LOG"

echo "Compilando backend..."
$MVN_CMD -q clean compile

echo "Subindo backend na porta 8080..."
$MVN_CMD spring-boot:run -Dspring-boot.run.profiles=dev > "$BACKEND_LOG" 2>&1 &
BACKEND_PID=$!
echo -e "Backend Java subiu (PID $BACKEND_PID). Os logs estão em $BACKEND_LOG."

echo "Aguardando backend responder em http://localhost:8080 ..."
for i in {1..30}; do
  if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
    echo -e "${RED}Erro: o backend encerrou durante a inicialização.${NC}"
    tail -n 120 "$BACKEND_LOG" || true
    exit 1
  fi

  if lsof -t -i:8080 >/dev/null 2>&1; then
    break
  fi

  sleep 2
done

if ! lsof -t -i:8080 >/dev/null 2>&1; then
  echo -e "${RED}Erro: backend não abriu a porta 8080 a tempo.${NC}"
  tail -n 120 "$BACKEND_LOG" || true
  exit 1
fi

# ----------
# 3. FRONTEND (NEXT.JS)
# ----------
echo -e "${GREEN}[3/3] Instalando dependências e iniciando Frontend Next.js...${NC}"

# Mata qualquer processo rodando na 3000
FRONT_PID=$(lsof -t -i:3000 || true)
if [ ! -z "$FRONT_PID" ]; then
    echo "Limpando porta 3000..."
    kill -9 $FRONT_PID 2>/dev/null || true
fi

cd frontend

# Caso não tenha npm install, rodamos
if [ ! -d "node_modules" ]; then
  echo "Instalando módulos npm (isso só acontece na primeira vez)..."
  npm install
fi

echo -e "${BLUE}>>> Aplicação completa rodando! <<<${NC}"
echo -e "- Frontend em: http://localhost:3000"
echo -e "- Backend em:  http://localhost:8080"
echo -e "- Tente logar com admin@jgm.com.br / admin123"
echo -e "- Logs do backend: $BACKEND_LOG"
echo -e ""
echo -e "${RED}Para parar tudo (inclusive o backend), pressione [CTRL+C] aqui.${NC}"
npm run dev
