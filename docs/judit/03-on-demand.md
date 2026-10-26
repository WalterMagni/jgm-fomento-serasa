# Judit — fase 2: On Demand testado e descartado; franquia, trava e consumo

Continua a [fase 1](02-implementacao.md); anatomia do payload em [01-mapa-de-campos.md](01-mapa-de-campos.md).
Migration: V99.

## Decisão (2026-10-26)

Depois da reunião com a Judit, o On Demand (Data Lake + tribunais ao vivo, R$ 6,00 a cada 1.000) foi
implementado, testado com uma consulta real e **descartado no mesmo dia**: a consulta dos processos
continua a da fase 1, da base histórica — **resumo (R$ 0,75) e depois a lista (R$ 1,50 a cada 1.000)**.

Do trabalho da fase 2 ficou o que vale para qualquer consulta:

| O quê | Como ficou |
|---|---|
| Franquia | R$ 1.000/mês é o **piso** da conta, não o teto: paga-se usando ou não, e o excedente é cobrado pela mesma tabela. `JUDIT_FRANQUIA_MENSAL` (era `JUDIT_AVISO_MENSAL`) |
| Trava | **R$ 1.500 no mês** (`JUDIT_LIMITE_MENSAL`). Acima disso só consulta de novo se o admin liberar um **valor a mais no mês corrente** — nunca consulta por consulta. Tabela `judit_liberacao` |
| Cores | Selo do card e barra do painel: verde dentro da franquia, laranja no excedente, vermelho no limite. Mês do calendário, sempre |
| Painel | Configurações › **Consumo Judit**, no padrão do Consumo Serasa: barra do mês, custo por tipo (resumo, lista, detalhe), tabela de consultas e o botão de liberar (só admin) |
| Pontos de atenção | O resumo mostra o que olhar antes de buscar a lista: criminal, recuperação judicial ou falência, execução fiscal, título contestado, Justiça do Trabalho |
| Acompanhamento do detalhe | O pedido de detalhe é acompanhado por um job que lê os pendentes do banco: reiniciar o servidor não perde pedido |

## Franquia, trava e liberação

`limite do mês = judit.limite-mensal + soma das liberações do mês`. A trava confere antes de cada
chamada cobrada (resumo, lista, detalhe) com o custo da chamada; a trava do Postgres (`travarLimite`)
continua serializando as consultas. A mensagem de bloqueio manda pedir a liberação ao admin.

O admin que libera é o mesmo que marca a Judit nos usuários (`AdminAllowList`,
`APP_USER_MANAGEMENT_ALLOWED_EMAILS`), não o papel ADMIN dos catálogos da esteira.

| Rota | Quem |
|---|---|
| `GET /api/v1/judit/consumo?dias=30` | logado (mesma regra da aba Consumo Serasa) |
| `POST /api/v1/judit/liberacoes` `{valor, motivo}` | admin do portal; valor de R$ 0,01 a R$ 10.000 |

## Pontos de atenção

Calculados no backend (`JuditLeitura.pontosDeAtencao`), a partir dos agregados do resumo. Em cada
dimensão (área, classe, assunto, justiça) soma os valores que casam; entre dimensões vale o maior, para
não contar o mesmo processo duas vezes.

| Ponto | De onde sai |
|---|---|
| Criminal | área, classe ou assunto penal — "cláusula penal" (multa de contrato) não conta |
| Recuperação judicial / falência | classe ou assunto |
| Execução fiscal | classe ou assunto (inclui dívida ativa) |
| Título contestado | assuntos de protesto, negativação, duplicata |
| Justiça do Trabalho | justiça "JUSTIÇA DO TRABALHO" ou área "DIREITO DO TRABALHO" — não a árvore "processual civil e do trabalho" |

Ressalva sempre na tela: o resumo não cruza área com polo — "processo criminal" pode ser a empresa como
vítima. A lista mostra cada processo.

## V99

- `judit_consulta.nome_empresa`: nome no painel e no aviso do sino (preenchido com `company_details` nas antigas).
- `judit_consulta.verificado_em` e índice dos PENDENTE: acompanhamento do detalhe.
- `judit_liberacao`: valor a mais no limite, por mês.

## O teste do On Demand (registro, caso volte à mesa)

Empresa de teste: CNPJ 24.163.223/0001-38 (Cisco Distribuidora de Alimentos). Respostas cruas em
`temp/judit/24163223000138-*` (gitignored). Gasto real: R$ 0,75 (resumo) + R$ 6,00 (On Demand) + o
primeiro pedido, que respondeu do cache (custo na fatura).

| Chamada | Resultado |
|---|---|
| `/lawsuits/synthetic` | 9 processos: 5 como ré, 4 como autora |
| `/requests` **sem** `on_demand` | `cached: true`, `search.on_demand: false`: resposta do cache em 22 s, sem andamentos. Não é o On Demand |
| `/requests` com `search.on_demand: true` e `side: Passive` | 26 min. Os mesmos 5 processos, `cached: false`, `crawler.updated_at` do dia e último andamento mais novo que o do cache (um processo: 19/10 no cache, 26/10 no tribunal). `steps` vazio: todas as movimentações só no detalhe |

- O On Demand exige `search.on_demand: true`; sem isso o `/requests` por documento é consulta ao cache.
- O filtro `search_params.filter.side` funciona no `/requests`.
- Cada item `lawsuit` do `/responses` traz em `response_data` o mesmo processo do Data Lake (33 campos).
- A implementação completa (popup com pré-consulta, estimativa por blocos, pedido como autora,
  acompanhamento de até 6 h) está no commit `69dd51c` da branch `feat/judit-on-demand`.

## Perguntas para a Judit que continuam valendo

1. **Lista** (`/lawsuits`): "a cada 1.000 processos retornados" tem mínimo de um bloco por chamada?
   Lista vazia é cobrada?
2. **Resumo sem processo**: o `/lawsuits/synthetic` que devolve 404 é cobrado?
3. **Corte em 1.000** do `/lawsuits`: como paginar acima disso, e cada página é cobrada?
4. **`/lawsuits/count`**: qual o preço? Serve de checagem mais barata que o resumo?
5. **Primeiro pedido do teste** (`/requests` sem `on_demand`, respondido do cache): como foi cobrado?
