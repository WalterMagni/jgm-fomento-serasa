# Esteira de prospecção — design

Data: 2026-09-20
Status: validado, pronto para plano de implementação

## Problema

O comercial controla a prospecção numa planilha (`CONTROLE DE EMPRESAS - 2025-2026`).
Dez abas, preenchimento manual, sem dono claro e sem relógio. Consequências
observadas nos próprios dados:

- `REMOVIDAS DO RADAR` registra perdas por silêncio ("após diversas cobranças,
  deixamos de lado", "análise há mais de 3 meses e cobranças"). Ninguém é avisado
  antes de virar perda.
- `DOC'S PENDENTES` guarda a data da cobrança por e-mail e por WhatsApp em colunas
  separadas, sem histórico: só sobrevive a última.
- `MOTIVO DA RECUSA` é texto livre, nunca tabulado.
- A página `/reports/visao-cedente` do portal existe, é funcional e não tem uso,
  porque mostra estado estático (`visaoCedente = SIM`) e nenhum dado de processo.

## Escopo

Cobrir da solicitação da análise até a documentação completa — as abas
`VISÃO CEDENTE` e `DOC'S PENDENTES`. A `ESTEIRA DA HABILITAÇÃO` (dossiê, comitê,
contrato, SEC, QITech) fica fora desta fase e continua na planilha; o card sai da
esteira em `PRONTO_HABILITACAO`.

Fora de escopo nesta fase: portal externo para o cliente subir documento, envio de
e-mail e WhatsApp automáticos, gráficos de funil no BI, e importação da planilha —
a equipe preenche à mão a partir da virada.

## Decisões

| Decisão | Escolha | Porquê |
|---|---|---|
| Entrada do card | CNPJ obrigatório | amarra esteira, análise de crédito, partes ligadas e filiais na mesma chave; evita o card órfão da planilha |
| Estágios | 6 + 2 terminais | espelha as duas abas sem inventar etapa |
| SLA | prazo por estágio, em dias úteis | é a coluna `QTDE DE DIAS PARA RETORNO` que já existe |
| Notificação | só dentro do portal | `EmailService` já existe e pode ser ligado depois sem retrabalho |
| Arquivos | upload interno | sem superfície pública nova |
| Checklist | catálogo editável pelo admin | a exigência muda sem deploy |
| Layout | kanban | o funil precisa ser lido de relance |
| Rota | `/prospeccao`, separada do BI | audiência e cadência diferentes |

## Estágios

```
SOLICITADA ──> EM_ANALISE ──┬──> APROVADO ──> DOCS_PENDENTES ⇄ cobrança
                            │                      │
                            │                      v
                            │                 DOCS_COMPLETOS
                            │                      │
                            │                      v
                            │              PRONTO_HABILITACAO  (saída)
                            │
                            └──> REPROVADO  (terminal, motivo obrigatório)

REMOVIDO_RADAR: terminal a partir de qualquer estágio, motivo obrigatório
```

Regras da máquina de estados:

- transição fora da tabela → HTTP 409;
- `APROVADO` materializa o checklist e avança sozinho para `DOCS_PENDENTES`;
- `DOCS_COMPLETOS` exige todo documento obrigatório em `RECEBIDO`; senão 422
  listando o que falta;
- `REPROVADO` e `REMOVIDO_RADAR` exigem motivo não vazio;
- toda transição grava um evento na mesma transação. A timeline não tem outra
  porta de escrita.

## Modelo de dados

### `prospeccao`

```
id uuid pk
cnpj varchar(14) not null
razao_social text not null
estagio varchar(24) not null
comercial_id uuid fk users
analista_id uuid fk users null
estagio_desde timestamp not null
prazo_estagio_dias int not null      -- snapshot da transição
motivo text null
created_at, updated_at, closed_at

unique (cnpj) where estagio not in ('REPROVADO','REMOVIDO_RADAR','PRONTO_HABILITACAO')
index (estagio, comercial_id), index (estagio_desde)
```

`prazo_estagio_dias` é copiado na transição, não lido da configuração na hora de
exibir: mudar o SLA não pode deixar card antigo estourado retroativamente.

### `prospeccao_evento` (append-only)

```
id, prospeccao_id fk
tipo    TRANSICAO | COBRANCA | NOTA | DOC_RECEBIDO | DOC_REJEITADO
canal   EMAIL | WHATSAPP | LIGACAO | REUNIAO | SISTEMA   -- null em TRANSICAO
autor_id fk users, texto text
estagio_de, estagio_para varchar(24) null
criado_em timestamp
```

Substitui as colunas `SOLICITAÇÃO POR EMAIL` / `SOLICITAÇÃO POR WHATS`, que hoje
guardam só a última cobrança.

### `documento_tipo` (catálogo do admin)

```
id, nome, escopo EMPRESA|SOCIO, obrigatorio bool, ativo bool, ordem int
```

O escopo é a descoberta que a planilha escondia: o checklist embutido na aba
`DOC'S PENDENTES` se repete idêntico em 8 empresas e tem dois níveis — 13 itens da
empresa e 5 de cada sócio.

Seed, item a item.

Empresa:
1. Certidão simplificada (retirar na JUCESP)
2. Receita Federal
3. Contrato / última alteração consolidada
4. Faturamento atualizado — datado e assinado pelo sócio ou contador (últimos 12 meses)
5. Declaração com instituições (anexo, preencher e assinar)
6. Comprovante de endereço — emissão máxima de 90 dias (água, luz ou telefone)
7. Endividamento
8. Curva ABC
9. Autorização SCR
10. Balanço patrimonial e DRE
11. Possui filiais? Vai operar pelas filiais?
12. Dados para informação do contrato
13. Relatório de visita comercial

Sócio:
1. RG/CPF ou CNH
2. Comprovante de endereço (conta de consumo, últimos 60 dias)
3. IRPF
4. Certidão de casamento (se houver) — não obrigatório
5. RG/CPF ou CNH do cônjuge — não obrigatório, só se assinar como avalista

### `prospeccao_documento`

```
id, prospeccao_id fk, documento_tipo_id fk
nome_snapshot text, obrigatorio_snapshot bool, escopo varchar(8)
socio_nome text null, socio_cpf varchar(11) null     -- preenchidos quando escopo = SOCIO
status  PENDENTE | RECEBIDO | REJEITADO
arquivo_path, arquivo_nome, arquivo_tamanho, content_type
recebido_em, recebido_por
```

Ao materializar o checklist, os itens de escopo `SOCIO` são multiplicados por cada
sócio do QSA da empresa (`company_shareholder`), já com nome e CPF preenchidos. Se
não houver QSA carregado, cria-se uma linha por item sem sócio, editável à mão.

O snapshot de nome e obrigatoriedade preserva o histórico quando o admin mexe no
catálogo.

### Migrations

- **V56** — tabelas `prospeccao`, `prospeccao_evento`, `documento_tipo`,
  `prospeccao_documento`, `prospeccao_alerta` e índices.
- **V57** — seed do catálogo (13 + 5) e expansão de papéis:
  `ROLE_COMERCIAL`, `ROLE_ANALISTA`, `ROLE_BACKOFFICE`, `ROLE_GESTOR`.
  `ROLE_ADMIN` continua irrestrito; `ROLE_USER` existente migra para
  `ROLE_COMERCIAL`.

## Backend

Pacote `application/service/ProspeccaoService`, controller
`api/rest/controller/ProspeccaoController`, base `/api/v1/prospeccao`.

### Endpoints

| Método | Rota | Papel |
|---|---|---|
| GET | `/prospeccao` | filtros: estágio, dono, SLA, busca |
| POST | `/prospeccao` | comercial: CNPJ + razão social |
| GET | `/prospeccao/{id}` | card com checklist e timeline |
| PATCH | `/prospeccao/{id}/estagio` | transição, com motivo |
| POST | `/prospeccao/{id}/eventos` | cobrança ou nota |
| POST | `/prospeccao/{id}/documentos/{docId}` | upload |
| GET | `/prospeccao/{id}/documentos/{docId}/arquivo` | download |
| DELETE | `/prospeccao/{id}/documentos/{docId}/arquivo` | remove arquivo, volta a PENDENTE |
| GET | `/prospeccao/alertas` | badge de SLA |
| CRUD | `/prospeccao/documento-tipos` | só admin |

Autorização por `@PreAuthorize`: comercial cria, cobra e enxerga só a carteira
dele; analista decide; backoffice mexe em documento; gestor e admin leem tudo.

### Armazenamento de arquivo

Diretório próprio `${PROSPECCAO_DOCS_DIR}/<cnpj>/<uuid>_<nome-sanitizado>`, servido
apenas por endpoint autenticado — nunca como caminho estático.

`CompanyDocumentService` não serve aqui: ele exige `ClientEntity` com pasta raiz
mapeada, e o prospect ainda não é cliente. Na transição para
`PRONTO_HABILITACAO`, os arquivos são copiados para a pasta da empresa via
`CompanyDocumentService` quando houver cliente mapeado; sem esse passo o documento
ficaria preso na esteira.

Validação no upload: allow-list de extensão (pdf, jpg, png, xlsx, docx), limite de
15 MB (o mesmo do multipart já configurado), nome sanitizado, e caminho resolvido e
conferido contra o root — o mesmo guard de `resolveInsideRoot`.

### SLA

`@Scheduled(cron = "0 0 8 * * MON-FRI")` — `@EnableScheduling` ainda não existe no
projeto e entra agora. O job varre cards abertos, calcula dias úteis desde
`estagio_desde` e grava `prospeccao_alerta` com nível `ATENCAO` ou `ESTOURADO`.

O cálculo de dias úteis fica num utilitário puro, com feriado nacional em lista
fixa. A tela não recalcula nada: lê o alerta.

Prazos iniciais, configuráveis: análise 2 dias, documentação 7 dias, demais 3 dias.
`ATENCAO` a partir de 70% do prazo.

## Frontend

Rota nova `/prospeccao`, no menu lateral. A página `/reports/visao-cedente` fica
intacta nesta fase.

```
hooks/useProspeccao.ts          lista, card, mutations, alertas
components/prospeccao/
  ProspeccaoBoard.tsx           colunas, scroll horizontal
  ProspeccaoColumn.tsx          droppable, contador
  ProspeccaoCard.tsx            nome, CNPJ, dono, semáforo, 4/7 docs
  ProspeccaoModal.tsx           detalhe (padrão da praça de pagamento)
  DocumentChecklist.tsx         agrupado por empresa e por sócio
  ProspeccaoTimeline.tsx        eventos e formulário de cobrança
  NovaProspeccaoDialog.tsx      CNPJ -> CNPJ Já -> confirma
```

Dependência nova: `@dnd-kit/core` (~12 kB, acessível por teclado). O projeto só tem
`@tanstack/react-virtual`.

Semáforo direto do alerta do backend: cinza dentro do prazo, âmbar em `ATENCAO`
(`#D1732C`), vermelho em `ESTOURADO`.

Drag otimista com rollback: o card pula de coluna na hora e dispara
`PATCH /estagio`; 409 ou 422 devolve o card à origem e mostra o motivo.

Badge de alertas no menu, polling de 60 s. Clicar abre o board filtrado por SLA
estourado.

Filtros persistidos em `localStorage`. Comercial abre em "só meus"; gestor em
"todos".

## Rotas e o BI

| Rota | O que é | Quem usa |
|---|---|---|
| `/prospeccao` | kanban da esteira | comercial, analista, backoffice |
| `/prospeccao/{id}` | deep link do card | todos |
| `/reports/visao-cedente` | BI da carteira, como é hoje | gestor |

A esteira e o BI são rotas irmãs, não pai e filha. A esteira é operacional, aberta
muitas vezes por dia e com escrita; o BI é leitura semanal. Empilhar as duas faria o
comercial carregar heatmap e análise de IA para chegar ao trabalho dele. O nome
também colide: na planilha, "visão cedente" é a etapa de análise de crédito, não um
painel.

O ganho indireto é o que resolve a página parada: com `prospeccao_evento`
acumulando, o BI ganha dado de processo e passa a responder o que hoje não
responde — funil de conversão por mês e por comercial, SLA médio real por estágio,
taxa de reprovação por motivo, documento que mais emperra, e perda por silêncio
contra perda por decisão. Esses gráficos entram numa fase posterior, quando houver
evento acumulado.

## Fases

1. **Modelo e API** — migrations V56 e V57, entidades, `ProspeccaoService` com a
   máquina de estados, endpoints, testes da máquina de estados e do cálculo de dias
   úteis.
2. **Board** — rota `/prospeccao`, kanban, modal, criação por CNPJ. Já utilizável:
   substitui a aba `VISÃO CEDENTE`.
3. **Documentos** — catálogo, checklist por empresa e por sócio, upload e download,
   tela de admin. Substitui a aba `DOC'S PENDENTES`.
4. **SLA e alertas** — job agendado, `prospeccao_alerta`, badge, filtro de
   estourados.
5. **Depois** — gráficos de funil no BI, e-mail de cobrança reaproveitando
   `EmailService`, e a esteira de habilitação.

Cada fase é entregável sozinha. Parar na 2 já tira a aba `VISÃO CEDENTE` da
planilha.

## Carga inicial

Não há importação da planilha. A equipe passa a preencher à mão a partir da
entrada em produção, e a planilha vira arquivo morto para consulta de histórico.

Isso dispensa o importador, a fila de conciliação de CNPJ e a normalização de nomes
de analista — os três só existiam para tratar a qualidade do dado antigo (113 linhas
de `VISÃO CEDENTE`, das quais 37 sem CNPJ no texto; analistas grafados como `BIANCA`
e `Bianca`).

O que entra à mão na virada são apenas os cards em aberto — cerca de 7 empresas em
documentação pendente. Volume de uma tarde.

A planilha continua sendo a fonte do desenho: dela vêm os estágios, o checklist de
13 + 5 itens, a prática de contar dias até o retorno e a evidência de que a perda
por silêncio é o problema central. Essas conclusões não dependem de migrar dado
nenhum.

## Perguntas a fechar antes da fase 1

A planilha registra o resultado do processo, não o combinado entre as pessoas.
Quatro pontos não são dedutíveis dela e são baratos de mudar agora, caros depois da
V56:

1. **Atribuição da análise.** O analista puxa da fila ou um gestor distribui? Define
   se `analista_id` é auto-atribuição na transição para `EM_ANALISE` ou campo
   editável por gestor.
2. **Prazos.** Os 2 dias de análise e 7 de documentação foram inferidos das datas da
   planilha. O time tem o número que considera atraso.
3. **Visibilidade entre comerciais.** O desenho assume que o comercial vê só a
   carteira dele. Numa equipe pequena isso pode atrapalhar mais do que ajuda.
4. **Reanálise.** Há empresa reprovada na planilha que depois foi habilitada. Hoje
   `REPROVADO` é terminal; se reanálise for comum, falta uma transição de reabertura
   para `SOLICITADA`, preservando o motivo anterior na timeline.

Validar com uma analista e um comercial antes de escrever a migration.

## Riscos

| Risco | Mitigação |
|---|---|
| Comercial não adota e volta pra planilha | fase 2 entrega valor sozinha; criar card exige só o CNPJ |
| Desenho tirado só da planilha, sem ouvir o time | validar as quatro perguntas abertas antes da V56 |
| Papéis novos quebram login existente | V57 migra `ROLE_USER` para `ROLE_COMERCIAL`; `ROLE_ADMIN` inalterado |
| Upload como vetor de arquivo malicioso | allow-list, sanitização de nome, guard de path, download só autenticado |
| Alerta vira ruído e é ignorado | só dois níveis, um card por linha, job diário e não a cada hora |
