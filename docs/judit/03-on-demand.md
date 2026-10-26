# Judit — fase 2: On Demand, franquia e painel de consumo (plano)

Plano fechado em 2026-10-26, depois da reunião com a Judit. Continua a
[fase 1](02-implementacao.md); anatomia do payload em [01-mapa-de-campos.md](01-mapa-de-campos.md).
Branch sugerida: `feat/judit-on-demand`. Migration nova: V99.

## Decisões

| Tema | Decisão |
|---|---|
| Franquia | R$ 1.000/mês é o **piso** da conta, não um teto. O excedente é cobrado pela mesma tabela. |
| Trava | **R$ 1.500 no mês** no primeiro mês; depois avaliamos. Acima disso, só o admin libera — sempre um **valor a mais no mês corrente**, nunca consulta por consulta. |
| Consulta | **On Demand** (Data Lake + tribunais ao vivo, R$ 6,00 a cada 1.000) substitui a lista do Data Lake (R$ 1,50). Todos os tribunais. Uso: crédito para empresa nova (regra de uso, não de código). |
| Polo | Por padrão só os processos em que a empresa é **ré**. Botão à parte para buscar também como **autora**; a tela junta as duas. |
| Pré-consulta | O resumo (R$ 0,75) vira **pré-consulta opcional, recomendada para empresa grande**. Mostra quantos processos, o custo estimado do On Demand e pontos de atenção. Não usamos porte da Receita: a consulta não pode depender dessa base. |
| Tempo | On Demand é assíncrono: a pessoa pede, o portal acompanha e avisa pelo sino. O acompanhamento sobrevive a reinício do servidor. |
| Auditoria | Cada On Demand guarda qual pré-consulta veio antes (ou nenhuma). O painel mostra quem pulou a pré-consulta numa empresa que saiu cara. |

> **O que a trava não faz.** Ela confere antes de cobrar, com o custo **estimado**. O custo real de um
> On Demand só chega com a resposta. Um clique numa empresa com 50 mil processos como ré custa
> R$ 300 e passa pela trava; o que ela bloqueia são as consultas seguintes. A proteção desse clique é a
> pré-consulta recomendada e, se a Judit oferecer, um teto de processos por pedido (pergunta 3).

## Fase 0 — o que o teste real mostrou (2026-10-26)

Empresa de teste: CNPJ 24.163.223/0001-38 (Cisco Distribuidora de Alimentos). Respostas cruas em
`temp/judit/24163223000138-*` (gitignored).

| Chamada | Resultado |
|---|---|
| `/lawsuits/synthetic` (R$ 0,75) | 9 processos: 5 como ré, 4 como autora |
| `/requests` **sem** `on_demand` | `cached: true`, `search.on_demand: false`: a Judit respondeu do cache em 22 s, sem andamentos (`steps` vazio). **Não é o On Demand.** Custo a confirmar na fatura |
| `/requests` com `on_demand: true` e `side: Passive` | **Concluiu em 26 min** (1.560 s): os mesmos 5 processos, com `cached: false`, `crawler.updated_at` do dia e último andamento mais novo que o do cache (um processo: 19/10 no cache, 26/10 no tribunal). `steps` continua vazio: todas as movimentações só no Detalhar |

- **O On Demand exige `search.on_demand: true`.** Sem isso o `/requests` por documento é uma consulta
  ao cache. O `JuditClient.pedirOnDemand` sempre manda.
- **O filtro de polo funciona**: com `side: Passive` vieram exatamente os 5 processos como ré do resumo.
- **Formato**: cada item `lawsuit` do `page_data` traz em `response_data` o mesmo processo do Data Lake,
  com os mesmos 33 campos. A conclusão normaliza para `{"lawsuits": [...]}` e o resto do portal lê igual.
- **Tempo**: o On Demand por CNPJ leva bem mais que o detalhe (~50 s) — 26 min numa empresa de 5
  processos. Por isso o limite de acompanhamento do On Demand é de 360 min, e o acompanhamento lê do banco.
- **Ponta a ponta**: o pedido real foi gravado PENDENTE no banco local e o job do backend o concluiu
  sozinho 1 min depois da Judit (custo real R$ 6,00, 5 registros), com o aviso no sino e o link da página.

## Fluxo na Visão 360º

```
[Consultar processos]
   │
   ▼
Popup ─ On Demand · todos os tribunais · como ré · R$ 6,00 a cada 1.000
        "Empresa grande? Faça antes a pré-consulta (R$ 0,75)."
        [Pré-consulta R$ 0,75]          [Consultar direto ~R$ 6,00]
   │ pré-consulta: síncrona, ~2 s
   ▼
Resultado ─ N como ré · M no total · custo estimado R$ X (k blocos)
            pontos de atenção
            [Cancelar]                  [Consultar On Demand R$ X]
   │
   ▼
Card: "Consulta em andamento desde 14:32 — avisaremos pelo sino"   [Verificar agora]
   │ (sino)
   ▼
Card com os totais + [Ver os N processos] + [Buscar também como autora ~R$ 6,00]
```

- **Pré-consulta feita nos últimos 30 dias**: o popup já abre no resultado, sem cobrar de novo.
- **Pontos de atenção e custo aparecem sempre**, não só acima de 1.000 — a informação já foi paga.
  Acima de 1.000 como ré, o custo fica em destaque (laranja).
- **Pré-consulta com zero**: "Nada na base histórica. O On Demand ainda pode achar processos recentes."
- **Estimativa perto da virada de bloco** (acima de 900 no bloco): "R$ 6,00, pode chegar a R$ 12,00" —
  o On Demand vai ao tribunal e pode achar mais que a base histórica.
- **Confirmação sempre mostra a faixa do mês**: dentro da franquia, ou "entra no excedente (R$ 1.012 no mês)".

### Estimativa

```
com pré-consulta:  blocos = max(1, ceil(como_réu / 1000))     (ou como_autor, no botão de autora)
sem pré-consulta:  blocos = 1, rotulado "sem pré-consulta"
custo              = blocos × judit.custo-on-demand-por-mil
```

O custo real, gravado na conclusão, usa o `all_count` do `/responses`, com mínimo de um bloco até a
Judit dizer o contrário (pergunta 3).

### Pontos de atenção (da pré-consulta)

Calculados no backend (`JuditLeitura`), a partir dos agregados do resumo:

| Ponto | De onde sai |
|---|---|
| Criminal | área "DIREITO PENAL", ou classe/assunto penal |
| Execução fiscal | classe ou assunto de execução fiscal |
| Recuperação judicial / falência | assunto |
| Volume trabalhista | justiça "JUSTIÇA DO TRABALHO" (contagem) |
| Título contestado | assuntos que já casam com `ASSUNTO_TITULO_CONTESTADO`, 3 ou mais |

Ressalvas na tela: o resumo **não cruza** área com polo ("3 na área penal" pode ser a empresa como
vítima), e área costuma vir "NÃO INFORMADO" (20 de 54 na I G Labegalini) — por isso classe e assunto
entram junto.

## Franquia, trava e liberação

| Faixa no mês | Cor | Comportamento |
|---|---|---|
| R$ 0 – 1.000 | verde Judit `#15BE62` | dentro da franquia |
| R$ 1.000 – limite | laranja `#D1732C` | excedente; a confirmação avisa |
| limite | vermelho | trava; mensagem "peça ao admin para liberar" |

`limite efetivo do mês = judit.limite-mensal + soma das liberações do mês`. Mês = calendário (dia 1 até
hoje), nunca o filtro de 7–90 dias do painel. A trava do Postgres (`travarLimite`) continua serializando
as consultas cobradas.

## Backend

### V99 — `judit_consulta` e `judit_liberacao`

```sql
ALTER TABLE judit_consulta DROP CONSTRAINT judit_consulta_servico_check;
ALTER TABLE judit_consulta ADD CONSTRAINT judit_consulta_servico_check
    CHECK (servico IN ('RESUMO', 'PROCESSOS', 'DETALHE', 'ON_DEMAND'));

-- Só no ON_DEMAND: qual polo foi pedido.
ALTER TABLE judit_consulta ADD COLUMN polo VARCHAR(7) CHECK (polo IN ('PASSIVO', 'ATIVO'));
-- A pré-consulta (RESUMO) usada na estimativa. Nula = pulou a pré-consulta.
ALTER TABLE judit_consulta ADD COLUMN pre_consulta_id UUID REFERENCES judit_consulta (id);
-- O que a tela prometeu; `custo` passa a ser o real quando a resposta chega.
ALTER TABLE judit_consulta ADD COLUMN custo_estimado NUMERIC(10, 2);
-- Nome para o painel de consumo; sacado nem sempre tem cadastro.
ALTER TABLE judit_consulta ADD COLUMN nome_empresa VARCHAR(255);
-- Última vez que o acompanhamento perguntou à Judit (intervalo crescente).
ALTER TABLE judit_consulta ADD COLUMN verificado_em TIMESTAMP;

CREATE INDEX idx_judit_consulta_pendente ON judit_consulta (criado_em) WHERE situacao = 'PENDENTE';

-- Nome das consultas antigas, de quem já tem cadastro.
UPDATE judit_consulta c SET nome_empresa = d.company_name
  FROM company_details d WHERE d.document_number = c.documento AND c.nome_empresa IS NULL;

-- Valor a mais no limite, liberado pelo admin, só no mês indicado.
CREATE TABLE judit_liberacao (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mes         DATE NOT NULL,              -- sempre o dia 1
    valor       NUMERIC(10, 2) NOT NULL CHECK (valor > 0),
    motivo      TEXT,
    admin_id    UUID,
    admin_nome  VARCHAR(255),
    criado_em   TIMESTAMP NOT NULL
);
CREATE INDEX idx_judit_liberacao_mes ON judit_liberacao (mes);
```

### Configuração (`application.yml`, `docker-compose.yml`, `.env`)

| Antes | Depois |
|---|---|
| `aviso-mensal: ${JUDIT_AVISO_MENSAL:900}` | `franquia-mensal: ${JUDIT_FRANQUIA_MENSAL:1000}` |
| `limite-mensal: ${JUDIT_LIMITE_MENSAL:1000}` | `limite-mensal: ${JUDIT_LIMITE_MENSAL:1500}` |
| — | `custo-on-demand-por-mil: ${JUDIT_CUSTO_ON_DEMAND_POR_MIL:6.00}` |
| `acompanhamento-limite-minutos: 10` (só detalhe) | mantém para o detalhe + `acompanhamento-on-demand-limite-minutos: ${JUDIT_ON_DEMAND_LIMITE:360}` |

Trocar `JUDIT_AVISO_MENSAL` por `JUDIT_FRANQUIA_MENSAL` no `docker-compose.yml` e no `.env` de produção.

### `JuditClient`

- `pedirOnDemand(documento, polo)`: `POST /requests` com
  `search: {search_type: "cnpj", search_key, on_demand: true, response_type: "lawsuits",
  search_params: {filter: {side: "Passive" | "Active"}}}`, `with_attachments: false`. Devolve o
  `request_id`. Sem `on_demand: true` a Judit responde do cache (fase 0).
- `respostasDoPedido` passa a **paginar**: busca até `all_pages_count` e junta o `page_data`. Hoje lê
  só a página 1 com 100, o que basta para o detalhe e não para o On Demand.

### `JuditService`

- `consultarOnDemand(cnpj, polo, nomeEmpresa, usuario)`: marca → chave → trava com a **estimativa**
  (pré-consulta ≤ 30 dias, se houver) → pede → grava `ON_DEMAND` PENDENTE com `custo = custo_estimado`,
  `pre_consulta_id`, `polo` e `nome_empresa` → publica o evento. Pedir de novo enquanto o mesmo polo
  está PENDENTE não cobra (mesmo padrão do detalhe).
- `concluirOnDemand(id, respostas)`: normaliza para `{"lawsuits": [...], "_portal": {...}}` (assim
  `JuditLeitura.processos`, a página, o detalhar e a exportação continuam iguais), grava
  `total_processos = all_count` e **corrige `custo` para o real**.
- `processos(cnpj)`: junta o último ON_DEMAND concluído de cada polo dentro da validade; sem nenhum,
  cai para a última lista `PROCESSOS` antiga (Data Lake), que segue legível.
- `detalhar`: a checagem "está na lista" passa a usar a lista unificada.
- `consultarResumo`: devolve também `estimativa` e `pontosDeAtencao`.
- Trava: `prepararConsulta` compara com o **limite efetivo** (limite + liberações do mês).
- Sai: `consultarProcessos` (Data Lake) e a rota `POST …/judit/processos`.

### Acompanhamento persistente (`JuditAcompanhador`)

Hoje é um laço em memória disparado depois do commit: se o servidor reinicia, a consulta fica
PENDENTE até alguém clicar "Verificar agora". Passa a ser um job que **lê o banco**:

- `@EnableScheduling` + `@Scheduled(fixedDelay = 5 s)`: busca as linhas PENDENTE (DETALHE e
  ON_DEMAND) cuja próxima verificação já venceu.
- Intervalo crescente pela idade do pedido: até 2 min → 5 s; até 10 min → 30 s; depois → 2 min.
  Grava `verificado_em` a cada pergunta.
- Passou do limite (10 min no detalhe, 360 min no On Demand): para de perguntar; continua PENDENTE e
  a tela oferece "Verificar agora", como hoje.
- Conclusão e falha continuam travando a linha (`travar`), para o job e o "verificar agora" não
  concluírem duas vezes.
- Notificações novas em `TipoNotificacao`: `JUDIT_CONSULTA_PRONTA` e `JUDIT_CONSULTA_FALHOU`, com
  link para `/clients/{cnpj}/processos`. A coluna é `VARCHAR(30)` sem CHECK: não precisa de migration.

### Rotas

| Rota | Cobra | Quem |
|---|---|---|
| `POST /company/{cnpj}/judit/resumo` (pré-consulta) | R$ 0,75 | marca Judit |
| `POST /company/{cnpj}/judit/on-demand` `{polo, nomeEmpresa}` | estimativa → real | marca Judit |
| `POST /company/{cnpj}/judit/on-demand/verificar` `{polo}` | não | logado |
| `GET /judit/consumo?dias=30` | não | logado (mesma regra da aba Consumo Serasa) |
| `POST /judit/liberacoes` `{valor, motivo}` | não | admin do portal (`AdminAllowList`, o mesmo que marca a Judit nos usuários) |

`GET /judit/consumo` devolve o mês corrente (`gasto`, `franquia`, `limite`, `liberado`,
`limiteEfetivo`), as liberações do mês e as consultas do período (usuário, empresa, documento,
data, serviço, polo, situação, custo estimado, custo, pré-consulta sim/não).

## Frontend

- **`components/judit/ConsultaProcessosModal.tsx`** (novo): os dois passos do popup — escolha e
  resultado da pré-consulta — e a confirmação com preço e faixa do mês.
- **`JuditPanel.tsx`**: um botão só, "Consultar processos" (ou "Atualizar" quando desatualizado);
  estado pendente com hora do pedido e "Verificar agora"; depois de concluído, os totais de hoje e
  "Buscar também como autora". Sai o botão "Buscar lista".
- **`useJudit.ts`**: `useConsultarOnDemand`, `useVerificarOnDemand`; enquanto houver PENDENTE,
  `refetchInterval` de 15 s.
- **Notificações**: os dois tipos novos em `visual.ts`, `avisador.tsx` e `ToastJudit.tsx`.
- **Página `/clients/{cnpj}/processos`**: sem mudança de tela; recebe a lista unificada. O filtro de
  polo que já existe separa ré e autora.
- **Configurações › aba "Consumo Judit"**, no padrão da Consumo Serasa (`components/judit/ConsumoJudit.tsx`,
  hooks `useJuditConsumo` e `useLiberarJudit` em `useJudit.ts`):
  - barra da franquia do **mês corrente**, com as três cores e as marcas de R$ 1.000 e do limite;
  - cards: custo do período, consultas, On Demand, pré-consultas;
  - tabela: usuário, empresa, documento, data/hora, tipo (On Demand ré/autora, Pré-consulta,
    Detalhe), situação, pré-consulta (sim/não), custo — com o estimado riscado quando o real diferir;
  - filtro "On Demand sem pré-consulta acima de R$ 6";
  - admin: botão "Liberar valor no mês" e a lista das liberações do mês.

## Ordem de entrega

| Fase | O quê | Situação |
|---|---|---|
| 0 | On Demand real na Cisco Distribuidora (24.163.223/0001-38) | feita — ver "Fase 0" acima |
| 1 | V99, propriedades, trava com liberação, `GET /judit/consumo`, `POST /judit/liberacoes` | backend feito |
| 2 | Acompanhamento persistente; o detalhe passa a usar o job | feito |
| 3 | On Demand no backend: cliente, serviço, normalização, lista unificada, custo real, pontos de atenção | backend feito |
| 4 | Tela: popup, card, pré-consulta com pontos de atenção, aba Consumo Judit; sai o botão e a rota da lista do Data Lake | feito |
| 5 | "Buscar também como autora" na tela | feito |

A lista do Data Lake saiu do portal (botão, rota, `JuditClient.processos` e o preço `custo-processos-por-mil`);
as listas já pagas continuam legíveis na página de processos, marcadas "base histórica da Judit (Data Lake)".

### Testes

- `JuditLeituraTest`: pontos de atenção a partir do resumo guardado da I G Labegalini; normalização do
  On Demand com a resposta da fase 0.
- `JuditServiceTest`: estimativa com e sem pré-consulta; trava com e sem liberação; custo real na
  conclusão; pedir de novo enquanto pendente não cobra; lista unificada dos dois polos sem repetir
  processo.
- `JuditAcompanhadorTest`: intervalo crescente; retoma pendentes lidos do banco (simula reinício);
  para no limite sem marcar falha.

## Pendências com a Judit (mensagem redigida em 2026-10-26)

| # | Pergunta | Se a resposta for… | O plano faz… |
|---|---|---|---|
| 1 | O que é `cached_response`; cache cobra menos? | cobra menos | estudar parâmetro de cache na fase 3 |
| 2 | On Demand aceita `side: Passive` e cobra só o filtrado? | aceita (fase 0); falta saber se **cobra** só o filtrado | se cobrar o total, Plano B: um pedido só, sem filtro; estimativa pelo **total** do resumo; botão "autora" some; a página separa pelo filtro de polo |
| 3 | Mínimo por pedido; pedido vazio cobra; teto de processos? | há teto | usar teto (ex.: 2.000 → no máximo R$ 12) e avisar na tela quando cortar |
| 4 | Preço do `/lawsuits/count`, aceita filtro de polo? | mais barato | troca o resumo pelo count na estimativa; pontos de atenção ficam opcionais |
| 5 | Tempo típico do On Demand por CNPJ | — | ajustar `JUDIT_ON_DEMAND_LIMITE` |

## Riscos

- **Empresa enorme**: a lista fica inteira num jsonb. Pela amostra (~3 KB por processo), 50 mil
  processos dão ~150 MB numa linha, e a página carrega a lista toda. Na fase 3, acima de 10 mil a tela
  avisa e mostra só os totais; guardar processo por linha fica para quando acontecer de verdade.
- **Formato do On Demand por CNPJ ainda não visto**: só temos resposta do On Demand por CNJ (detalhe).
  A fase 0 existe por isso.
- **Estimativa sem pré-consulta é sempre 1 bloco**: o painel e o filtro de auditoria são a resposta a
  isso, não o código.
