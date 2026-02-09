# Portal Serasa - Sistema de Análise de Crédito

Projeto Spring Boot 3.3+ (Java 17) para análise de crédito, seguindo Clean/Hexagonal Architecture.

## Stack Tecnológica

- **Linguagem:** Java 17
- **Framework:** Spring Boot 3.3.5
- **Build:** Maven
- **Banco de Dados:** PostgreSQL
- **Migrations:** Flyway
- **Cliente HTTP:** Spring RestClient
- **Utilitários:** Lombok, MapStruct
- **Containerização:** Docker e Docker Compose

## Estrutura do Projeto (Hexagonal Architecture)

```
com.portal.serasa/
├── domain/           # Domínio puro (entidades, exceções)
├── application/      # Casos de uso, ports
├── infrastructure/   # Persistência, integração Serasa
└── api/              # REST controllers, DTOs
```

## Como Executar

### Pré-requisitos

- Java 17+
- Maven 3.8+
- Docker e Docker Compose (opcional)

### 1. Subir o PostgreSQL

```bash
docker compose up -d postgres
```

### 2. Executar a aplicação

```bash
./run.sh
```

Ou via Maven:

```bash
mvn spring-boot:run
```

O script `run.sh` aceita: `run` (padrão), `build` ou `package`.

Perfil dev (SQL logado):

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 3. Subir tudo com Docker

```bash
docker compose up -d
```

A aplicação ficará disponível em `http://localhost:8080`.

## API REST

### Análise de Crédito

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | `/api/v1/credit-analysis/consultar/{cnpj}` | Consulta crédito na Serasa e persiste (CNPJ com 14 dígitos) |
| GET | `/api/v1/credit-analysis/{id}` | Busca análise por ID |
| GET | `/api/v1/credit-analysis/cnpj/{cnpj}` | Lista análises por CNPJ |

### Importação de Clientes (CSV)

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | `/api/v1/clients/import/upload` | Upload de CSV (MultipartFile) e importa clientes |
| POST | `/api/v1/clients/import/preview` | Upload de CSV e retorna preview sem persistir |
| GET | `/api/v1/clients/import/preview?limit=20` | Preview do `clientes.csv` na raiz do projeto |
| POST | `/api/v1/clients/import/from-file` | Importa do `clientes.csv` na raiz do projeto |

### CNPJ Já (Enriquecimento de Empresas)

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | `/api/v1/company/enrich/cnpja/{cnpj}` | Enriquece dados consultando API CNPJ Já e persiste |
| GET | `/api/v1/company` | Lista empresas (paginado) |
| GET | `/api/v1/company/{cnpj}` | Busca dados por CNPJ |
| POST | `/api/v1/company` | Cria empresa manualmente |
| PUT | `/api/v1/company/{cnpj}` | Atualiza empresa |
| DELETE | `/api/v1/company/{cnpj}` | Remove empresa |

**Requer** `CNPJA_API_KEY` para o endpoint de enriquecimento. Crie um arquivo `.env` na raiz (copie de `.env.example`) e defina a chave. O `run.sh` carrega o `.env` automaticamente.

### Clientes

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | `/api/v1/clients` | Cria cliente manualmente |
| GET | `/api/v1/clients/{id}` | Busca cliente por ID |
| GET | `/api/v1/clients/document/{documentNumber}` | Busca cliente por documento |
| PUT | `/api/v1/clients/{id}` | Atualiza cliente |
| DELETE | `/api/v1/clients/{id}` | Remove cliente (e dados relacionados em cascata) |

### Uso de APIs (contador para custos)

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | `/api/v1/admin/api-usage` | Retorna contagem de requisições por provider (CNPJA, SERASA) |

## Variáveis de Ambiente

| Variável | Default | Descrição |
|----------|---------|-----------|
| DB_HOST | localhost | Host do PostgreSQL |
| DB_PORT | 5432 | Porta do PostgreSQL |
| DB_NAME | portal_serasa | Nome do banco |
| DB_USER | serasa | Usuário |
| DB_PASSWORD | serasa123 | Senha |
| SERASA_API_URL | https://api.serasa.example.com | URL base da API Serasa |
| CLIENT_CSV_PATH | clientes.csv | Caminho do arquivo CSV para importação |
| CNPJA_API_KEY | - | Chave da API CNPJ Já (obrigatória para `/company/enrich`) |

Copie `.env.example` para `.env` e configure as variáveis. O arquivo `.env` não é versionado.
