# Esteira de Visão Cedente — Portal Serasa

Data: 2026-08-20
Status: spec escrita, **nada implementado**
Relacionado: [`integracao-portal-comercial.md`](./integracao-portal-comercial.md) (em segundo plano)

## Por que existe

Hoje o fluxo pós-prospecção é controlado numa planilha
(`CONTROLE DE EMPRESAS - 2025-2026`), com seis abas encadeadas à mão. A aba
`VISÃO CEDENTE` tem 114 linhas, 80 aprovadas / 34 reprovadas, mantidas por cinco
analistas.

O que a planilha não consegue fazer, e é o motivo desta esteira existir:

| Coluna da planilha | Preenchimento real | O que o sistema resolve |
|---|---|---|
| `QTDE DE DIAS PARA RETORNO` | **0 de 114** | calculado a partir da timeline |
| `DATA DA HABILITAÇÃO` | **4 de 114** | consequência de mudar de estágio |
| `ANALISTA` | texto livre (`"NICOLE "` com espaço) | usuário real do sistema |
| `MOTIVO DA RECUSA` | texto livre | motivo estruturado + observação |
| handoff entre pessoas | escondido em `"REPASSADO PARA ELCIO"` | evento na timeline |

Uma linha com razão social vazia e status `REPROVADO` mostra o problema de fundo:
não há nada impedindo perda silenciosa de dado.

**A planilha não será importada.** Serviu como levantamento de requisito — o
template de documentos abaixo saiu dela. A esteira nasce vazia.

## Decisões tomadas

| Decisão | Escolha |
|---|---|
| Escopo | Visão cedente + controle documental (`DOC'S PENDENTES`) |
| Histórico da planilha | não importar |
| Acesso | **só analistas.** Comercial não entra, não vê, não é notificado |
| Entrada na esteira | **automática** quando a análise resulta `visaoCedente = SIM` |
| Documentos | **facultativos** — checklist orienta, não trava avanço |
| Arquivos | **storage interno**, no compartilhamento de rede que o backend já monta |
| Compartilhar com o Comercial | fase 2, pela rota de integração — não bloqueia esta entrega |

### Sobre "só analistas"

Diferente do Portal Comercial, onde o comercial acompanha a requisição em tempo
real, aqui **não existe esteira visível para o comercial**. O nome do comercial é
campo de texto informativo (quem pediu), não um usuário com login. Isso simplifica
bastante: sem RLS por papel, sem notificação, sem contraproposta.

### Sobre a entrada automática

`credit_analysis.visao_cedente` (V17/V18) é **calculado** a partir dos dados do
Serasa — não é um ato humano de aprovação. Hoje: 14 análises `SIM`, 10 `NAO`.

O volume é pequeno porque `credit_analysis` só nasce quando alguém **consulta o
Serasa**, que é consulta paga. Não há risco de inundar a fila.

Modelo adotado, que reconcilia o automático com o veredito humano:

- `visaoCedente = SIM` cria o item no **primeiro estágio** (`TRIAGEM`)
- O analista então **aprova ou reprova** — e essa decisão é o que a aba
  `VISÃO CEDENTE` da planilha registrava
- O sinal calculado e o veredito humano ficam **lado a lado**, nunca um
  sobrescreve o outro

Mesmo padrão já validado na praça de pagamento: o automático sugere, o humano
decide, e a divergência entre os dois vira métrica de qualidade do motor.

**Backfill:** as análises que já existem **não** entram retroativamente. Só
análises criadas a partir do deploy. Um botão manual de "puxar visão cedente
existentes" fica disponível em `/esteira`, para a analista decidir se quer.

## Modelo

Migrations a partir de **V52**. Estado em 2026-08-20: a última aplicada é **V51**
(`create_company_partners`); **V49** é `company_details_data_source`.

⚠️ **`V50` está permanentemente queimado.** Não existe arquivo nem linha em
`flyway_schema_history` — a lacuna é histórica. Como o projeto não configura
`spring.flyway.out-of-order` (default `false`), criar um `V50__*.sql` agora
derruba o deploy com `Detected resolved migration not applied to database: 50`.
Nunca preencher essa lacuna.

⚠️ **Antes do próximo deploy em produção, conferir o checksum do V49.** O arquivo
atual é uma recriação: a migration original rodou em 2026-08-10 e o arquivo se
perdeu antes do commit. O arquivo recriado tem checksum diferente do que foi
gravado na época. Se produção ainda tiver a linha antiga, o deploy falha com
`Migration checksum mismatch for migration version 49`.

```sql
SELECT version, checksum, installed_on FROM flyway_schema_history WHERE version = '49';
```

Se `installed_on` for 2026-08-10, rodar `flyway repair` **antes** de subir — ele
reescreve o checksum sem tocar no schema.

### V52 — `esteira_items`

```
id                    uuid pk
razao_social          varchar(500)  NOT NULL
document_number       varchar(14)   NULL      -- CNPJ, quando conhecido
client_id             uuid          NULL      -- FK clients, vínculo tardio
credit_analysis_id    uuid          NULL      -- origem, quando veio do automático
estagio               varchar(40)   NOT NULL
veredito              varchar(20)   NULL      -- APROVADO | REPROVADO
motivo_recusa         varchar(60)   NULL      -- enum, ver abaixo
observacao            text          NULL
comercial_nome        varchar(120)  NULL      -- texto livre, não é usuário
analista_id           uuid          NULL      -- FK users
entrou_estagio_em     timestamp     NOT NULL
created_at/updated_at timestamp     NOT NULL
```

`document_number` é **nullable de propósito**. A esteira fica *antes* do cadastro:
o item nasce só com o nome que o comercial mandou, e o CNPJ entra quando a
analista o descobre. Aí `enrichByCnpja` cria a ficha — fluxo que já existe.

Índices: `(estagio, entrou_estagio_em)`, `(document_number)`, `(analista_id)`.

### V53 — `esteira_eventos`

```
id            uuid pk
item_id       uuid NOT NULL  -- FK esteira_items ON DELETE CASCADE
tipo          varchar(40) NOT NULL
estagio_de    varchar(40) NULL
estagio_para  varchar(40) NULL
detalhe       text NULL
usuario_id    uuid NULL
usuario_nome  varchar(200) NULL   -- denormalizado, sobrevive a usuário removido
criado_em     timestamp NOT NULL
```

Toda troca de estágio grava um evento. É daqui que sai o SLA por estágio — a
métrica que a planilha queria (`QTDE DE DIAS PARA RETORNO`) e nunca teve.

Tipos: `item_criado`, `estagio_alterado`, `veredito_registrado`, `cnpj_vinculado`,
`documento_marcado`, `arquivo_enviado`, `arquivo_removido`, `analista_alterado`,
`observacao_alterada`, `arquivado`.

### V54 — `esteira_documentos`

```
id            uuid pk
item_id       uuid NOT NULL   -- FK esteira_items ON DELETE CASCADE
categoria     varchar(20) NOT NULL   -- EMPRESA | SOCIO
socio_nome    varchar(200) NULL      -- preenchido quando categoria = SOCIO
codigo        varchar(60) NOT NULL   -- item do template
titulo        varchar(200) NOT NULL
status        varchar(20) NOT NULL   -- PENDENTE | OK | FALTA | NAO_APLICAVEL
observacao    text NULL
atualizado_em timestamp NOT NULL
```

`observacao` importa mais do que parece. Na planilha ela carrega o dado real:
`"OK - CRC válido"`, `"OK - 7 Clientes"`, `"OK - Sofisa, Bradesco, Itaú"`,
`"Válida até 2032"`. Status estruturado **e** texto livre ao lado.

### V55 — `esteira_arquivos`

```
id                uuid pk
item_id           uuid NOT NULL
documento_id      uuid NULL          -- vincula a um item do checklist, opcional
caminho_relativo  text NOT NULL UNIQUE  -- relativo à base de documentos
nome_original     varchar(300) NOT NULL
mime_type         varchar(120) NOT NULL
tamanho_bytes     bigint NOT NULL
versao            integer NOT NULL
enviado_por       uuid NULL
enviado_em        timestamp NOT NULL
removido_em       timestamp NULL     -- remoção lógica; o arquivo em disco fica
```

Metadado no banco, **byte no compartilhamento de rede**. Campos espelham
`documento_arquivos` do Comercial (mesmo versionamento, mesmo teto de 10MB, mesma
allowlist de mime) para que a fase 2 de compartilhamento seja tradução direta,
sem remodelagem.

`caminho_relativo` é sempre **relativo à base**, nunca absoluto — a base é
configurável em runtime (`SystemSettingService.requireDocumentStorageBasePath`) e
guardar caminho absoluto quebraria a tabela inteira se ela mudar.

Remoção é **lógica**. Apagar arquivo de compartilhamento de rede compartilhado com
outras equipes é destrutivo e irreversível; o registro sai da tela, o byte fica.

## Estágios

Espelhados nos do Comercial (`status_requisicao`), podados do que não se aplica
(nada de aceite do cliente, contraproposta ou retomada — isso é vida do comercial):

```
TRIAGEM  →  EM_ANALISE  →  APROVADO_VISAO_CEDENTE  →  COLETA_DOCUMENTOS
         →  EM_DOSSIE  →  EM_COMITE  →  CONTRATO  →  HABILITACAO  →  HABILITADA
```

Terminais: `REPROVADO`, `REMOVIDO_DO_RADAR` (a aba homônima da planilha tem 193
linhas — é um desfecho real e frequente, não uma exceção).

**Transição livre.** O analista move para qualquer estágio, inclusive para trás.
Sem máquina de estados rígida: quem opera são cinco pessoas da mesma equipe, e
regra rígida aqui só geraria contorno. O que garante rastreabilidade é a timeline,
não a restrição.

Motivos de recusa, extraídos dos padrões que já se repetem na planilha:
`QUANTIDADE_DE_RESTRICOES`, `SEGMENTO`, `PEFIN_COM_FUNDO`, `PROCESSO_COM_FIDC`,
`RECUPERACAO_JUDICIAL`, `DECISAO_DIRETORIA`, `OUTRO` (+ observação livre).

## Template de documentos

Extraído literalmente da aba `DOC'S PENDENTES`, onde ele já é aplicado à mão,
empresa por empresa.

**Empresa (13 itens)**

1. Certidão simplificada (retirar na JUCESP)
2. Receita Federal
3. Contrato / última alteração consolidada
4. Faturamento atualizado — datado e assinado por sócio ou contador
5. Declaração com instituições
6. Comprovante de endereço — emissão ≤ 90 dias
7. Endividamento
8. Curva ABC
9. Autorização SCR
10. Balanço patrimonial e DRE
11. Possui filiais? Vai operar pelas filiais?
12. Dados para informação do contrato
13. Relatório de visita comercial

**Por sócio (5 itens)**

1. RG/CPF ou CNH
2. Comprovante de endereço — emissão ≤ 60 dias
3. IRPF
4. Certidão de casamento (se houver)
5. RG/CPF ou CNH do cônjuge — somente se for assinar como avalista

Detalhes que o template precisa respeitar, todos observados na planilha:

- **Item 1 é da JUCESP, ou seja, só São Paulo.** Clientes de MG e RS aparecem
  marcados como "Não tem, cliente de MG" / "Cliente é de RS". O sistema já tem a
  UF vinda do CNPJ Já: fora de SP, o item nasce `NAO_APLICAVEL`.
- **Sócios têm ciclo de vida.** A planilha registra "vai sair da sociedade" e
  "Faleceu - recebemos certidão de óbito". O bloco de sócio precisa poder ser
  marcado como inativo sem sumir.
- **Grupos econômicos compartilham sócio.** `IBL LOGISTICA` + `IBL VALORES`,
  `MOVITRA` + `S R ALVES` — duas empresas, os mesmos documentos de PF. Nesta
  primeira entrega **cada item é independente** (documento duplicado nos dois);
  agrupar fica para depois.
- Um bloco de sócio é criado **manualmente** pela analista. Popular a partir do
  QSA do CNPJ Já é melhoria óbvia, mas não entra agora.

## Arquivos — storage interno

### O que já existe (e por isso não precisa de bucket)

O backend **já monta o compartilhamento de rede real da empresa**
([`docker-compose.yml`](../docker-compose.yml)):

```yaml
- "${DOCUMENTS_BASE_HOST_PATH:-/mnt/jgm/FM/Cadastro e Comercial/CLIENTES}:/mnt/clientes"
```

É a pasta `CLIENTES` onde as analistas já guardam documento hoje. A base é
configurável em runtime por system setting
(`SystemSettingService.requireDocumentStorageBasePath`), e `CompanyDocumentService`
já resolve caminho, valida travessia (`realRoot.startsWith(baseRoot)`), lista, faz
upload, renomeia e cria pasta.

Guardar os documentos da esteira aí é **melhor que um bucket**, não apenas mais
simples: o arquivo cai onde a equipe já trabalha, aparece no Explorer, e entra no
backup que já existe do compartilhamento. Um bucket criaria um segundo lugar onde
documento mora — exatamente o problema que a esteira quer resolver.

### Layout

```
{base}/{cnpj}/esteira/{categoria}/{codigo}/v{n}-{nome-original}
```

`categoria` é `empresa` ou `socio-{slug do nome}`. `codigo` é o item do template
(ex.: `certidao-simplificada`). Nome original preservado no banco; no disco vai
sanitizado.

- Metadado no banco (`esteira_arquivos`), byte no compartilhamento
- Teto de **10MB** e allowlist de mime iguais às do Comercial — para a fase 2 ser
  tradução direta
- Download por endpoint autenticado do próprio Serasa, com `Content-Disposition`;
  sem signed URL, sem caminho absoluto exposto ao cliente
- Reaproveitar a validação de travessia de caminho que `CompanyDocumentService` já
  tem, **sem exceção** — é o que impede `../` de escapar da base

**O store atual fica intocado.** Ele resolve outro problema — navegar a pasta da
empresa — e continua servindo a página da empresa.

**Regra:** upload exige CNPJ vinculado ao item, porque o CNPJ é o nome da pasta.
Não atrapalha: coleta documental acontece depois da aprovação, quando o CNPJ já é
conhecido.

⚠️ Se o compartilhamento não estiver montado, upload e download falham. É o mesmo
risco operacional que já existe hoje na página da empresa, e o mesmo diagnóstico:
a tela precisa dizer "compartilhamento indisponível" em vez de estourar erro
genérico. Vale a mesma nota de troubleshooting que existe para o container de
filiais.

⚠️ Compartilhamento de rede (SMB, montado via WSL em produção) tem escrita mais
lenta e trava de arquivo mais fraca que disco local. Upload de 10MB não é
instantâneo — a tela precisa de estado de progresso, e o backend, de timeout
generoso.

### Compartilhar com o Portal Comercial — fase 2

Fora do escopo desta entrega. Quando entrar, o caminho já está preparado:

- Chave é **CNPJ**, e os dois lados já concordam nela: `empresas.cnpj` é `unique`
  no Comercial, o Serasa é todo indexado por CNPJ. Não precisa de tabela de-para
- Serasa expõe listagem e download por `/api/v1/integracao/**`, com o
  `X-Service-Token` já especificado no doc da integração
- Alternativa mais barata a avaliar na hora: montar o **mesmo compartilhamento**
  no container do Comercial. Leitura sai de graça; escrita pelos dois lados
  precisaria de convenção de pastas para não haver conflito

## Endpoints

Todos sob `/api/v1/esteira`, autenticação de usuário normal (`Authorization:
Bearer`), sem relação com `X-Service-Token`.

| Verbo | Rota | O quê |
|---|---|---|
| GET | `/esteira` | lista, filtro por estágio/analista/busca |
| GET | `/esteira/resumo` | contadores por estágio + SLA médio |
| POST | `/esteira` | cria item manual |
| GET | `/esteira/{id}` | detalhe + timeline + checklist + arquivos |
| PATCH | `/esteira/{id}/estagio` | move de estágio (grava evento) |
| PATCH | `/esteira/{id}/veredito` | aprova/reprova + motivo |
| PATCH | `/esteira/{id}/cnpj` | vincula CNPJ e dispara `enrichByCnpja` |
| PATCH | `/esteira/{id}` | analista, comercial, observação |
| POST | `/esteira/{id}/socios` | cria bloco de documentos de um sócio |
| PATCH | `/esteira/documentos/{docId}` | status + observação do item |
| POST | `/esteira/{id}/arquivos` | upload (multipart) |
| GET | `/esteira/arquivos/{arqId}` | download autenticado (`Content-Disposition`) |
| DELETE | `/esteira/arquivos/{arqId}` | remoção **lógica** da versão |
| POST | `/esteira/backfill-visao-cedente` | puxa as análises `SIM` já existentes |

## Frontend

Rota `/esteira`, entrada em `NavItems.tsx` logo abaixo de "Visão Cedente".

- **Kanban** por estágio, card compacto: razão social, CNPJ (ou aviso de que
  falta), analista, dias parado no estágio, progresso do checklist (`8/13`)
- Mover por **drag-and-drop** e por menu no card — atalho de teclado, mesmo
  espírito da triagem de praça de pagamento
- **Painel lateral** ao abrir o card: identificação, veredito, checklist agrupado
  (Empresa / cada sócio), arquivos, timeline
- Card fica **âmbar** quando passa do SLA do estágio, mesmo tratamento visual do
  chip "sem código" na Gestão de Carteira
- Cores do tema: primary `#612035`, secondary `#D1732C`

## Fora de escopo

- Importar a planilha
- Acesso ou notificação para o comercial
- Agrupar empresas de um mesmo grupo econômico
- Popular sócios a partir do QSA automaticamente
- **Compartilhar arquivo com o Portal Comercial** — fase 2, ver seção de storage
- Consulta jurídica (processos, criminal) — frente seguinte, depois desta
- A rota `/api/v1/integracao/pre-analise` — continua em segundo plano

## Checklist de implementação

- [ ] V52 `esteira_items`
- [ ] V53 `esteira_eventos`
- [ ] V54 `esteira_documentos`
- [ ] V55 `esteira_arquivos`
- [ ] Entidades, repositórios, DTOs, mapper
- [ ] `EsteiraService` — CRUD, transição com evento, veredito, vínculo de CNPJ
- [ ] Hook automático: `visaoCedente = SIM` cria item em `TRIAGEM`
- [ ] Template de documentos + regra de UF para a certidão da JUCESP
- [ ] `EsteiraArquivoService` — upload/download/remoção lógica sobre a base de
      documentos, reaproveitando a validação de travessia de caminho
- [ ] `EsteiraController`
- [ ] Página `/esteira` (kanban + painel lateral) e entrada no `NavItems`
- [ ] Endpoint de backfill
- [ ] Degradação com mensagem clara quando o compartilhamento não estiver montado
- [ ] Testar com uma empresa real ponta a ponta: entra automático → aprova →
      coleta documento → sobe arquivo → confere que o arquivo aparece na pasta
      `CLIENTES` do compartilhamento
