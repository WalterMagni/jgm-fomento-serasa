# Integração Portal Comercial → Portal Serasa (pré-análise)

Data: 2026-08-14
Status: spec escrita, **nada implementado no Serasa ainda**
Contraparte: `PORTAL-COMERCIAL`, fase F14 — já implementada e esperando esta rota

## Por que existe

Quando o comercial abre uma prospecção no Portal Comercial, o sistema precisa
criar a ficha da empresa aqui, rodar a pré-análise e devolver o resultado —
incluindo o sinal de **visão cedente** — sem que ninguém troque de sistema.
Meta de negócio: velocidade de prospecção.

O Portal Comercial roda em container no mesmo servidor, na rede Docker
`portal-serasa-net`. A chamada é HTTP interno de container para container:
sem túnel, sem TLS, sem token trafegando na internet.

**Nada do que já existe no Serasa muda.** Esta integração acrescenta uma rota de
orquestração e um modo de autenticação. Controllers, services e o motor de
crédito atuais ficam como estão.

## O que o Comercial já faz (pronto, em `main`)

- Tabela `pre_analise_serasa` — uma linha por requisição, com `status`
  (`PENDENTE` / `OK` / `ERRO`), `visao_cedente boolean`, `payload jsonb`, `erro`
- Ao abrir a requisição: grava `PENDENTE` e chama esta rota fora do caminho da
  resposta (`after()` do Next), guardando o resultado quando volta
- Botão de reprocessar quando dá erro
- Timeout de 90s, e qualquer falha vira `status = ERRO` com a mensagem gravada

Ou seja: **enquanto esta rota não existir, toda pré-análise cai em `ERRO`** — que
é o comportamento esperado por enquanto, não um bug a investigar.

---

## O que falta implementar aqui

### 1. `POST /api/v1/integracao/pre-analise`

Orquestra o que hoje são quatro chamadas separadas. O Comercial chama uma vez e
recebe tudo.

**Request:**

```json
{ "cnpj": "12345678000199", "razaoSocial": "Empresa Exemplo LTDA" }
```

`cnpj` vem com 14 dígitos, sem máscara. `razaoSocial` é usada só na criação da
ficha, quando o cliente ainda não existe.

**Response 200:**

```json
{
  "cnpj": "12345678000199",
  "razaoSocial": "EMPRESA EXEMPLO LTDA",
  "visaoCedente": "SIM",
  "decision": { }
}
```

- `visaoCedente` — o valor de `CreditAnalysis.visaoCedente`, exatamente como
  está no banco: `"SIM"` / `"NAO"` / `"PENDENTE"`, ou `null`. O Comercial
  converte `SIM`→`true`, `NAO`→`false`, resto→`null`.
- `decision` — o `ResultadoAnaliseCredito` inteiro, do jeito que
  [`AnaliseCreditoController#analisarCredito`](../src/main/java/com/portal/serasa/api/rest/controller/AnaliseCreditoController.java#L43)
  já devolve. Não resumir: o Comercial guarda o objeto cru em `jsonb` porque as
  regras de negócio da pré-análise **ainda não foram definidas**, e a consulta é
  paga — reconsultar depois para pegar um campo que foi descartado custa dinheiro.

**Fluxo interno sugerido** — tudo já existe, é só encadear:

| Passo | O que chamar | Observação |
|---|---|---|
| 1 | `ClientService` — buscar por `documentNumber` | se existir, pula o passo 2 (idempotência) |
| 2 | criar cliente | mesma lógica de `POST /api/v1/clients` |
| 3 | `ClientProfileService.enrichByCnpja(cnpj)` | dados cadastrais |
| 4 | `ClientProfileService.enrichBySerasa(cnpj)` | **cria a `CreditAnalysis`** |
| 5 | `MotorCreditoService.analisar(analysis, companyOpt)` | monta o `decision` |

**O passo 4 é o que o plano original não tinha percebido.**
`GET /api/v1/credit-analysis/{cnpj}/decision` devolve **404** quando não existe
`CreditAnalysis` para o CNPJ, e criar o cliente **não** cria a análise —
`enrichBySerasa` é que cria. Chamar só `/decision` depois de criar a ficha
retorna 404 sempre.

**Reaproveitamento vs. reconsulta.** Cada `enrichBySerasa` é uma consulta paga na
Experian. Sugestão: se já existe `CreditAnalysis` para o CNPJ com menos de N dias
(N a definir com o negócio), reaproveitar em vez de reconsultar, e sinalizar isso
na resposta. O Comercial não precisa saber a diferença, mas o custo é real e
cresce com o volume de prospecção.

**Erros:**

| Situação | Status | Corpo |
|---|---|---|
| CNPJ inválido (≠ 14 dígitos) | 400 | `{ "erro": "..." }` |
| Serasa/CNPJa indisponível | 502 | `{ "erro": "..." }` |
| Token ausente ou inválido | 401 | — |

O Comercial trata qualquer não-2xx igual: grava `ERRO` com o corpo (truncado em
300 chars) e oferece reprocessar. Mensagem legível ajuda o suporte; o formato
exato não é crítico.

### 2. Consulta de visão cedente por CNPJ

Hoje [`VisaoCedenteController`](../src/main/java/com/portal/serasa/api/rest/controller/VisaoCedenteController.java)
só tem listagem (`GET /api/v1/reports/visao-cedente`) e contagem (`/summary`).
Se o passo 5 acima já devolver o `visaoCedente` da própria `CreditAnalysis`,
**este item deixa de ser necessário** — a listagem não é usada na integração.
Fica registrado porque o plano o listava como pendência.

### 3. Autenticação de serviço — `X-Service-Token`

Hoje [`SecurityFilter`](../src/main/java/com/portal/serasa/infrastructure/security/SecurityFilter.java)
só aceita `Authorization: Bearer <jwt>` de usuário, e
[`SecurityConfig`](../src/main/java/com/portal/serasa/infrastructure/security/SecurityConfig.java#L54)
exige `authenticated()` em tudo fora de `/api/auth/login`, `/api/auth/register`,
`/actuator/**` e o Swagger. Um serviço sem usuário não tem como chamar nada.

Proposta: aceitar `X-Service-Token` **apenas** em `/api/v1/integracao/**`.

- Valor vem de configuração (`app.integracao.service-token`), lido de variável de
  ambiente, nunca commitado
- Comparação em **tempo constante** (`MessageDigest.isEqual`), não `String.equals` —
  `equals` sai no primeiro byte diferente e vaza o prefixo do token por tempo
- Token ausente ou diferente → 401, sem detalhar qual dos dois
- Autenticação de serviço não vira `ROLE_ADMIN`: cria uma authority própria
  (`ROLE_SERVICE`) que só alcança `/api/v1/integracao/**`. Se o token vazar, o
  estrago fica contido nesta rota
- Gerar com `openssl rand -hex 32`. Um token por consumidor, para poder rotacionar
  o do Comercial sem derrubar outro

O mesmo valor vai no `.env` do Portal Comercial como `SERASA_SERVICE_TOKEN`.

⚠️ **Este endpoint nunca deve ser publicado no túnel Cloudflare.** A regra de
ingress do `cloudflared` expõe `serasa.<dominio>` apontando para o frontend; o
backend não tem hostname público. O modelo de segurança do `X-Service-Token`
depende disso: é um segredo estático, sem expiração e sem rotação automática,
adequado a uma rede fechada e inadequado à internet. Se algum dia o backend for
exposto, este esquema precisa ser trocado antes.

---

## Caminhos reais (o plano de migração estava desatualizado)

O documento
`PORTAL-COMERCIAL/docs/plans/2026-08-14-migracao-servidor-interno-e-integracao-serasa.md`
lista os endpoints sem o prefixo `/api/v1`. Os corretos, conferidos no código:

| No plano | Real |
|---|---|
| `POST /clients` | `POST /api/v1/clients` |
| `GET /clients/document/{cnpj}` | `GET /api/v1/clients/document/{documentNumber}` |
| `GET /analise-credito/{cnpj}/decision` | `GET /api/v1/credit-analysis/{cnpj}/decision` |
| `GET /visao-cedente` | `GET /api/v1/reports/visao-cedente` |
| — | `POST /api/v1/company/enrich/cnpja/{cnpj}` |
| — | `POST /api/v1/company/enrich/serasa/{cnpj}` |

## Rede e endereço

O Comercial chama `http://portal-serasa-backend:8080` — nome de container na rede
`portal-serasa-net`, declarada como `external` no compose do Comercial. O compose
do Serasa **não é alterado**: cada portal sobe e desce sem reiniciar o outro.

Configurável no Comercial por `SERASA_API_URL`, que só precisa mudar em
desenvolvimento fora do Docker.

## Latência

A cadeia Experian + CNPJa + Gemini leva de 10 a 60s. O Comercial usa timeout de
90s (`SERASA_TIMEOUT_MS`) e não segura o usuário: a tela abre mostrando
"consultando" e atualiza quando o resultado chega.

Se na prática passar de ~60s com frequência, o caminho é o endpoint responder
`202` com um id e o Comercial fazer polling — a tabela `pre_analise_serasa` já
nasce com `status`, então essa mudança não exige migration nova do lado de cá.

## Fora de escopo

**SSO.** Continuam dois mundos de autenticação: aqui `JwtUtil` + `UserEntity` com
`ROLE_ADMIN`/`ROLE_USER`, lá Supabase Auth com `papel`. `X-Service-Token` é
autenticação **de serviço**, não de usuário, e não encosta nesse assunto.
O plano de unificação (Supabase Auth como IdP, Spring Security OAuth2 Resource
Server contra o JWKS) está no documento de migração e não bloqueia esta fase.

## Checklist de implementação

- [ ] `IntegracaoController` com `POST /api/v1/integracao/pre-analise`
- [ ] Service de orquestração (cliente → CNPJa → Serasa → motor)
- [ ] Reaproveitar `CreditAnalysis` recente em vez de reconsultar (definir a janela)
- [ ] Filtro de `X-Service-Token` restrito a `/api/v1/integracao/**`,
      comparação em tempo constante, authority `ROLE_SERVICE`
- [ ] `app.integracao.service-token` via variável de ambiente
- [ ] Confirmar que `/api/v1/integracao/**` não entra no ingress do `cloudflared`
- [ ] Testar de dentro do container do Comercial:
      `curl -H "X-Service-Token: ..." -d '{"cnpj":"...","razaoSocial":"..."}' http://portal-serasa-backend:8080/api/v1/integracao/pre-analise`
- [ ] Medir o tempo real com um CNPJ de verdade — decide se fica síncrono
