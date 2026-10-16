# Judit — anatomia do payload da consulta por CNPJ

Baseado em **payload real**, não na documentação. Amostra: 1 CNPJ, 74 registros, `POST /lawsuits` (cache síncrono, sem monitoramento).

> Documento de trabalho da fase exploratória. Nada disso está integrado ao Portal.

---

## Como a consulta funciona

Uma chamada, síncrona, milissegundos. Sem polling, sem webhook.

```bash
curl -X POST 'https://lawsuits.production.judit.io/lawsuits' \
  -H "api-key: $JUDIT_API_KEY" \
  -H 'Content-Type: application/json' \
  -d '{"search":{"search_type":"cnpj","search_key":"35782346000173"}}'
```

O corpo é **sempre** embrulhado em `search`. Filtros opcionais vão em `search.search_params.filter`.

Três rotas úteis, em ordem de custo:

| Rota | Devolve | Quando usar |
|---|---|---|
| `POST /lawsuits/count` | `{"total": 74}` | *gate* barato: decide se vale consultar o resto |
| `POST /lawsuits/synthetic` | contagens agregadas por tribunal/área/classe/assunto | dashboard numa chamada, sem trazer processo |
| `POST /lawsuits` | os processos inteiros | quando precisa da lista |

`/synthetic` é subestimado: devolve o perfil de risco agregado **sem** trazer 230 KB de processo.

### O envelope

```json
{ "has_lawsuits": true, "request_id": "...", "lawsuits": [ ... ] }
```

⚠️ A doc diz que a lista vem em `response_data`. **Mentira** — nesta rota vem em `lawsuits`. Qualquer parser tem de aceitar as duas chaves.

⚠️ `page_size` na query string **foi ignorado**: pedi 10, vieram os 74.

⚠️⚠️ **A rota corta em 1.000 processos, em silêncio.** Confirmado numa segunda empresa da amostra:

| | `/lawsuits/count` | `/lawsuits` devolveu |
|---|---|---|
| MEBRAS METAIS | **1.199** | **1.000** |

Nenhum aviso, nenhum campo de "tem mais", nenhum cursor. **199 processos sumiram** e só dá para perceber comparando com o `count`.

Consequência prática: `POST /lawsuits/count` não é só um *gate* de custo — é a **única forma de saber se o resultado veio completo**. Sempre chamar os dois e comparar. Acima de 1.000, é obrigatório fatiar a busca por `search_params` (por ano de distribuição, por tribunal) e juntar os pedaços.

---

## As 4 armadilhas (leia antes de modelar tabela)

### 1. `"NÃO INFORMADO"` é string, não `null`

| Campo | Ocorrências com a sentinela |
|---|---|
| `situation` | **74/74** |
| `tribunal_url` | **74/74** |
| `judge` | 70/74 |
| `system` | 60/74 |

Um teste ingênuo de `!= null` marca esses campos como preenchidos — e eles não têm **nenhuma** informação. `situation` em particular é 100% inútil nesta amostra: **não dá para saber se o processo está ativo ou encerrado** por este campo.

Regra: normalizar `"NÃO INFORMADO"` → `null` na entrada. Sempre.

### 2. A lista tem duplicatas por instância

**74 registros, mas 66 números CNJ únicos.** 8 processos aparecem duas vezes — uma linha por instância (`instance: 1` e `instance: 2`).

Contar `lawsuits.length` infla a exposição em 12%. A chave real é `(code, instance)`; para contar processos, `DISTINCT code`.

Curiosamente `/lawsuits/count` e `/synthetic` também devolveram 74, então **os agregados da própria Judit contam a instância, não o processo**.

### 3. `amount: 0` não quer dizer causa sem valor

3 de 74 zerados, todos em classes onde o valor não se aplica (Juizado Especial, Carta Precatória, Apelação). É ausência de dado, não valor zero. Nunca somar cegamente.

### 4. Razão social não identifica ninguém

O mesmo CNPJ aparece com **6 grafias**:

```
31x  D F DOS SANTOS INDUSTRIA DE TUBOS
17x  D & M INDUSTRIA DE TUBOS E CONEXOES LTDA
16x  D & M INDUSTRIA DE TUBOS E CONEXOES LTDA.
 5x  D M INDUSTRIA DE TUBOS E CONEXOES LTDA
 4x  DIVANEIDE FERREIRA DOS SANTOS          ← nome de pessoa física
 1x  D. F. DOS SANTOS INDUSTRIA DE TUBOS LTDA.
```

O tribunal registra o nome vigente na época do ajuizamento — aqui dá para ver a empresa trocando de razão social, e em 4 casos o cartório lançou o nome da **sócia** no CNPJ da empresa.

Casar por nome é inviável. **`parties[].main_document` é a única âncora confiável.**

---

## Mapa de campos — `lawsuits[]`

Preenchimento sobre 74 registros, já descontada a sentinela `"NÃO INFORMADO"`.

### Identificação

| Campo | Tipo | Preench. | Pertinência | Nota |
|---|---|---|---|---|
| `code` | string | 74/74 | **chave** | CNJ. Não é único na lista — ver armadilha 2 |
| `instance` | number | 74/74 | **chave** | 1ª=64, 2ª=10. Compõe a chave com `code` |
| `name` | string | 74/74 | média | Autuação "X versus Y". Nomes não confiáveis |
| `secrecy_level` | number | 74/74 | **alta** | 0 aqui em todos. `>0` = segredo de justiça, esconder teor |

### Classificação — **o núcleo do valor**

| Campo | Tipo | Preench. | Pertinência | Nota |
|---|---|---|---|---|
| `subjects[]` | `{code,name}` | 74/74 | **crítica** | O assunto CNJ. É daqui que sai o sinal de risco |
| `classifications[]` | `{code,name}` | 74/74 | **alta** | Classe processual (execução, cumprimento, procedimento) |
| `area` | string | 74/74 | média | "DIREITO DO CONSUMIDOR", "DIREITO TRIBUTÁRIO" |

Ambos vêm com `code` numérico da tabela CNJ — **use o código, não o texto**, que varia por tribunal.

### Partes

| Campo | Preench. | Pertinência | Nota |
|---|---|---|---|
| `parties[].main_document` | 402/470 | **crítica** | Âncora de identidade. 68 partes sem documento |
| `parties[].side` | 470/470 | **crítica** | `Active` / `Passive` / `Interested`. Autor ≠ réu muda tudo |
| `parties[].name` | 470/470 | baixa | Ver armadilha 4 |
| `parties[].person_type` | 470/470 | baixa | Texto livre do cartório: 19 variantes distintas só aqui — `RÉU`, `REU/RE`, `RÉU/RÉ`, `REQUERIDO(A)`… Inutilizável sem normalizar. **Use `side`.** |
| `parties[].documents[]` | 441/470 | média | `{document, document_type}`. Redundante com `main_document` |
| `parties[].lawyers[]` | 59/470 | baixa | Quase sempre vazio |
| `parties[].was_inferred` | 49/470 | **alta** | Campo **ausente** nas outras 421 (não é `false`, é ausente). Todos `false` aqui |

Sobre `with_inferred_parties`: liguei a flag e **não veio nenhum processo novo**. Neste CNPJ não há vínculo inferido. O campo só merece atenção na busca por CPF de sócio, onde o risco de homônimo é real.

### Valor e datas

| Campo | Preench. | Pertinência | Nota |
|---|---|---|---|
| `amount` | 71/74 úteis | **alta** | Ver armadilha 3 |
| `distribution_date` | 74/74 | **crítica** | Ajuizamento. **A distribuição no tempo é o sinal mais forte** |
| `created_at` / `updated_at` | 74/74 | baixa | Timestamps da Judit, não do processo |

### Foro

| Campo | Preench. | Pertinência |
|---|---|---|
| `tribunal_acronym` | 74/74 | **alta** — TJPR, TJMG… |
| `state` / `city` / `county` | 74/74 | **alta** — dispersão geográfica |
| `courts[]` | 74/74 | média — `{code,name}` da vara |
| `justice` / `justice_description` | 74/74 | média — `"8"` / `"JUSTIÇA ESTADUAL"` |
| `tribunal` | 74/74 | baixa — código numérico, redundante |

⚠️ Vi um registro com `tribunal_acronym: TJPR`/`state: PR` cuja parte ativa é o **Tribunal de Justiça de Rondônia** — carta precatória. O `state` é o do juízo deprecado, não o da origem. Não tratar `state` como "onde a empresa é processada".

### Movimentação — **a grande lacuna**

| Campo | Preench. | Nota |
|---|---|---|
| `last_step` | 74/74 | Último andamento: `step_date`, `content`, `step_type`, `steps_count` |
| `steps[]` | **0/74** | ❌ **sempre vazio no cache** |
| `attachments[]` | **0/74** | ❌ **sempre vazio no cache** |
| `phase_history[]` | **0/74** | ❌ sempre vazio |
| `pipelines[]` | **0/74** | ❌ sempre vazio |

A doc promete histórico e anexos. O cache síncrono **não entrega nenhum dos dois** — só o `last_step`. Para andamentos e documentos é preciso a rota assíncrona `/requests` (`with_attachments: true`), que debita crédito e leva de segundos a minutos.

Para nosso caso de uso, `last_step` provavelmente basta: interessa *o que existe contra a empresa*, não o acompanhamento processual.

### Metadados de coleta

| Campo | Nota |
|---|---|
| `crawler.source_name` | Origem: `"JTJ - BR - Document / Lawsuit - GOV.BR"` |
| `crawler.updated_at` | **Quando a Judit raspou o tribunal.** É a idade real do dado |
| `tags.dictionary_updated_at` | Veio com a data de hoje — a Judit reprocessou na nossa consulta |
| `free_justice` | boolean, gratuidade de justiça |

`crawler.updated_at` é o campo honesto sobre frescor. Guardar sempre.

---

## O que a amostra mostrou sobre esta empresa

Os números, sem interpretação:

- **74 registros / 66 processos** distintos
- **61 como `Passive`** (ré), 9 como `Active`, 4 `Interested`
- **R$ 7,27 mi** no polo passivo, R$ 13,39 mi somando tudo
- **70 dos 74 ajuizados em 2025–2026.** Antes disso: 1 em 2017, 1 em 2021, 1 em 2023, 1 em 2024

Assuntos em que a empresa é **ré**:

| Qtd | Assunto |
|---|---|
| 21 | PROTESTO INDEVIDO DE TÍTULO |
| 10 | INCLUSÃO INDEVIDA EM CADASTRO DE INADIMPLENTES |
| 9 | SUSTAÇÃO DE PROTESTO |
| 9 | INDENIZAÇÃO POR DANO MORAL |
| 3 | DÍVIDA ATIVA (EXECUÇÃO FISCAL) |

**40 dos 61 processos passivos giram em torno de protesto e negativação indevidos.**

Os autores são empresas pequenas e pessoas físicas espalhadas por MA, RO, PA, MG — perfil de sacado, não de concorrente ou de banco:

```
COMERCIAL ROFE LTDA            MA   R$ 66.911   PROTESTO INDEVIDO DE TÍTULO
BURG COMERCIO DE MADEIRAS      RO   R$ 35.276   PROTESTO INDEVIDO DE TÍTULO
M. L. DOS SANTOS ASSUNCAO ME   MA   R$ 10.752   PROTESTO INDEVIDO DE TÍTULO
IRMAOS BRITO FERREIRA EPP      PA   R$ 24.833   INCLUSÃO INDEVIDA EM CADASTRO
```

Dezenas de sacados dispersos pelo país processando a mesma empresa, no mesmo biênio, alegando que o título protestado contra eles não era devido — é a assinatura de **duplicata sem lastro**. Não é prova; é um padrão que pede verificação antes de operar com títulos dessa origem.

Nenhuma outra fonte que o Portal já consome mostra isso. Serasa dá o score e a negativação **da empresa**; CNPJ Já dá o cadastro. Nenhum dos dois diz que **os sacados dela estão processando-a por protesto indevido** — que é exatamente o risco de comprar esse recebível.

Esse é o argumento concreto a favor da Judit, saído de uma única consulta síncrona.

---

## Consequências para o desenho (quando for integrar)

1. Normalizar `"NÃO INFORMADO"` → `null` na entrada.
2. Deduplicar por `code`; guardar `instance` como atributo.
3. Persistir o payload cru (`text`) + colunas derivadas — padrão já usado em `CreditAnalysisEntity`.
4. Identidade por `main_document`, nunca por nome.
5. Filtrar via `search_params` desde a primeira chamada: sem paginação, o payload é o que for.
6. `/lawsuits/count` como *gate* antes da consulta cheia.
7. Guardar `crawler.updated_at` para saber a idade do dado.
8. Um score jurídico deve pesar **assunto × polo × recência**, não a contagem bruta. 61 processos passivos de protesto indevido em 18 meses ≠ 61 processos trabalhistas antigos.

---

## Lacunas desta amostra

Uma empresa só, e atípica. Falta verificar nas próximas cinco:

- empresa **limpa** — confirmar se dá `404` ou `total: 0` (o tratamento no código muda)
- como se comporta uma empresa com **execução fiscal** ou **recuperação judicial**
- se `steps[]`/`attachments[]` vêm vazios **sempre** ou só quando o tribunal não publicou
- busca por **CPF de sócio**, onde `was_inferred` passa a importar de verdade
- custo efetivo por consulta (a Judit não devolve custo no payload síncrono)
