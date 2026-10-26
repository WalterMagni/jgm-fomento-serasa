# Judit — implementação (fase 1)

> **Fase 2 em [03-on-demand.md](03-on-demand.md)** (2026-10-26): On Demand no lugar da lista do Data Lake,
> franquia como piso da conta, trava de R$ 1.500 com liberação do admin e acompanhamento que sobrevive a
> reinício. O que muda em relação a este documento está lá.

Consulta de processos judiciais da empresa pela Judit, no card **Processos judiciais** da Visão 360º
(borda e destaques no verde da Judit, `#15BE62`, com selo **Beta**). Branch `feat/judit`, migrations
V89–V91. Anatomia do payload em [01-mapa-de-campos.md](01-mapa-de-campos.md).

## Regra geral

Funciona como o Serasa e o CNPJ Já: um botão no card, sem ligar nada antes na empresa. Nada consulta
sozinho — abrir a página só lê o que já foi pago; cada botão que cobra mostra o preço e pede
confirmação. Antes de cada chamada o servidor confere, nesta ordem:

1. **Marca Judit no usuário** — Configurações › Usuários, só o admin marca. Exige Analista.
2. **Limite do mês** — soma de `judit_consulta.custo` desde o dia 1. Aviso no card a partir de
   R$ 900 (`JUDIT_AVISO_MENSAL`); em R$ 1.000 (`JUDIT_LIMITE_MENSAL`) o portal para de consultar.
   Uma trava no Postgres faz duas consultas simultâneas esperarem uma pela outra.

Resultado tem validade de 30 dias (`JUDIT_VALIDADE_DIAS`): depois disso o card mostra
"desatualizado", mas só consulta de novo se alguém clicar.

> **Decisão de 2026-10-15.** A primeira versão exigia ligar a Judit em cada empresa antes de consultar
> (V89, `judit_empresa`). O Walter tirou depois do primeiro teste: a consulta vira processo normal,
> como Serasa e CNPJ Já. Ligar por empresa volta com o **monitoramento**, que terá desenho próprio.
> A V90 apaga a tabela.

## O que o card faz

As duas consultas são da **base histórica da Judit (Data Lake)** — rápidas e baratas, mas o dado é
o da última raspagem deles (`crawler.updated_at`), não o do tribunal agora.

| Botão | Rota da Judit | Produto na tabela de preços | Custo (faixa R$ 1.000) |
|---|---|---|---|
| Consultar resumo | `POST /lawsuits/synthetic` | Consulta Histórica Sintética | R$ 0,75 |
| Buscar lista | `POST /lawsuits` | Consulta Histórica de Processos (Data Lake) | R$ 1,50 a cada 1.000 registros devolvidos |

- A lista só aparece depois do resumo, e só se o resumo achou processo.
- `/lawsuits/count` saiu do fluxo: o resumo já traz `lawsuits_count`.
- Empresa com mais de 1.000 no resumo: a lista pede só os processos em que ela é **ré**
  (`side: Passive`), porque a rota corta em 1.000 sem avisar.
- O card avisa quando a lista veio menor que o resumo.
- Alerta de **título contestado**: 3 ou mais processos como ré por protesto, negativação ou duplicata
  contestada — o padrão de duplicata sem lastro achado no teste de setembro.

### Card e página de processos

- **Card na Visão 360º** — só totais, alerta e assuntos mais comuns como ré; a distribuição do
  resumo fica recolhida. Com a lista buscada, o botão **Ver os N processos** abre a página.
- **Página `/clients/{cnpj}/processos`** — a lista inteira, 50 por página, com filtros de polo, foro
  (tribunal, vara/câmara, cidade, UF) e contraparte. **Exportar planilha** (XLSX, abas Processos e
  Resumo) e **Imprimir / PDF** levam só o que está filtrado. Nada ali chama a Judit.
- **Por que o resumo e a lista dão números diferentes** — a Judit conta um registro por instância
  (54 registros = 50 processos na I. G. Labegalini, 4 em 1ª e 2ª instância). No recurso a empresa
  pode trocar de polo: ré na 1ª, recorrente (polo ativo) na 2ª; a lista conta como ré. Com a lista
  buscada, o card mostra os números dela.
- **Contraparte sem advogado** — a Judit lista advogados, sociedades de advogados, peritos e
  testemunhas como partes (84 advogados só na lista da I. G. Labegalini). Saem pelo `person_type`
  ou pelo documento OAB.

### Detalhe de um processo (consulta processual + resumo de IA)

Botão **Detalhar** em cada linha da página de processos; abre uma gaveta e cobra só com confirmação
(R$ 0,35 = consulta processual R$ 0,25 + resumo de IA R$ 0,10). Traz direto do tribunal o que a base
histórica não tem: status, fase, juiz, todas as partes e **todas as movimentações** (259 no processo
de teste, contra só a última na lista), mais o resumo em português escrito pela IA da Judit (beta).

- **Assíncrono** (`POST /requests` com `search_type: lawsuit_cnj` e `judit_ia: ["summary"]`, sem
  anexos): a Judit levou ~50 s no teste. A linha nasce PENDENTE já com o custo; o `JuditAcompanhador`
  pergunta a situação a cada 5 s por até 10 min (perguntar não cobra), grava a resposta e **notifica
  quem pediu** pelo sino — toast e item no verde da Judit, com link que abre a gaveta do processo.
  Webhook não serve: o servidor de produção fica na rede interna.
- Servidor reiniciou no meio, ou passou de 10 min: fica PENDENTE e a gaveta oferece **Verificar
  agora** (sem custo). Pedir de novo enquanto pendente não cobra outra vez.
- O resumo vem em markdown com HTML do site da Judit (`<strong className=…>`); o backend converte para
  markdown puro e a tela mostra com react-markdown, que não executa HTML.
- **Primeiro teste** (processo 1009479-30.2024.8.26.0011, 2026-10-15) foi carregado no banco local a
  partir da resposta salva, sem pagar de novo. É a JGM LP FIDC executando a I. G. Labegalini em
  R$ 200.485,00; SISBAJUD bloqueou só R$ 1.544,84; executados incluem três pessoas da família e duas
  empresas ligadas.

Preços em `application.yml` (`judit.custo-*`), tabela de abr/2026. São estimativa; a fatura da Judit
é a palavra final. O consumo da Judit **não** entra na aba Consumo Serasa.

## Onde fica cada coisa

| | |
|---|---|
| Tabela | `judit_consulta` (cada chamada, com a resposta crua em jsonb) |
| Backend | `JuditClient`, `JuditService`, `JuditLeitura` (leitura da resposta, testável sem banco), `JuditController` |
| Rotas do portal | Só leem: `GET …/judit` (card, sem a lista), `GET …/judit/processos` (lista inteira), `POST …/judit/exportar` (XLSX). Cobram: `POST …/judit/resumo`, `POST …/judit/processos` |
| Frontend | `clients/[cnpj]/JuditPanel.tsx` (card), `clients/[cnpj]/processos/page.tsx` (página), `components/judit/JuditPecas.tsx`, `hooks/useJudit.ts`, marca em `PapeisUsuario.tsx` |
| Variáveis | `JUDIT_API_KEY`, `JUDIT_FRANQUIA_MENSAL` (era `JUDIT_AVISO_MENSAL` até a fase 2), `JUDIT_LIMITE_MENSAL` — no `.env` e repassadas pelo `docker-compose.yml` |

A resposta crua fica inteira no banco: dá para mudar a leitura (`JuditLeitura`) sem pagar consulta nova.

## Fora desta fase

- **Selo "processo com a JGM"** — marcar quando a contraparte é empresa do grupo JGM. Falta a lista
  de CNPJs do grupo.
- **Consulta On Demand** — "Consulta Histórica de Processos (On Demand)", R$ 6,00 a cada 1.000:
  Data Lake + tribunais em tempo real, pela rota assíncrona `/requests` + `/responses`. Depois da
  reunião.
- **Sócios (CPF)** — mandados de prisão e execução criminal. A rota de mandado é assíncrona
  (`/requests` + `/responses`) e a de execução criminal não está na coleção. Depende da reunião.
- **Paginação** acima de 1.000 — depende de saber como a Judit cobra cada página.
- **Chips na esteira e na praça** com o resumo já guardado do sacado.
- **Monitoramento** — ligado por empresa (volta aqui o interruptor). Não cabe na franquia para a
  carteira toda: DJEN R$ 1,35/CNPJ/mês, novas ações R$ 15/CNPJ/mês.

## Perguntas para a Judit

1. **Franquia**: o que não usamos no mês acumula ou expira? O que passa de R$ 1.000 é cobrado pela
   mesma tabela, bloqueado, ou com outro preço?
2. **Cobrança da lista** (`/lawsuits`): "a cada 1.000 processos retornados" tem mínimo de um bloco
   por chamada? Uma consulta que devolve 57 custa R$ 1,50? Lista vazia é cobrada?
3. **Empresa sem processo**: o `/lawsuits/synthetic` devolve 404 ou `lawsuits_count: 0`? É cobrado?
   (Hoje contamos como cobrado, para o limite errar para o lado seguro.)
4. **Corte em 1.000**: um CNPJ com 1.199 no `/count` devolveu exatamente 1.000 no `/lawsuits`, sem
   indicação de continuação. Como buscar o resto? A paginação vai no corpo
   (`search_params.pagination`) ou fora de `search`, como na coleção do Postman? Cada página é cobrada?
5. **Contagem × lista**: um CNPJ com 57 no resumo trouxe 56 na lista. É esperado?
6. **Instâncias**: o resumo conta uma vez por instância (74 registros = 66 processos distintos). Há
   como pedir o total de processos distintos?
7. **Frescor**: com que frequência a base de vocês é atualizada a partir dos tribunais? O
   `crawler.updated_at` é a data da última raspagem daquele processo?
8. **Consumo**: há endpoint ou relatório para acompanhar o consumo do mês em tempo real, para
   conferir com a nossa estimativa?
9. **Sócios**: a consulta de mandados de prisão e de execuções criminais por CPF tem rota síncrona?
   Qual o formato e o preço na nossa faixa? Na busca por CPF, como tratar homônimos (`was_inferred`)?
10. **Chave**: a chave dos testes de setembro passou a responder `401 USER_NOT_FOUND`. A nova chave
    é por ambiente (teste/produção)?
11. **On Demand × Data Lake**: quando vale pagar o On Demand (R$ 6,00) em vez do Data Lake
    (R$ 1,50)? Qual a defasagem típica do Data Lake? O On Demand devolve andamentos (`steps`) e
    anexos, que no Data Lake vêm vazios?
12. **Monitoramento**: como funciona o aviso (webhook `callback_url`)? Monitorar um CNPJ cobre
    matriz e filiais? Dá para monitorar só processos novos como réu?
13. **Resumo de IA**: o `judit_ia: ["summary"]` cobra R$ 0,10 além da consulta processual (R$ 0,25)?
    O resumo vem com HTML do site de vocês misturado ao markdown — há como pedir só markdown? E o
    "Resumo de Entidade (IA)" (R$ 0,15) da tabela de preços: como se pede? Não está na documentação.
14. **Anexos**: o detalhe sem `with_attachments` ainda lista os anexos (19 no teste). Baixar cobra
    quanto, e por anexo ou por processo?
