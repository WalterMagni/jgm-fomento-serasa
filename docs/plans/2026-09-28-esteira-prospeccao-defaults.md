# Esteira de prospecção — defaults adotados e reconciliação das specs

Data: 2026-09-28
Status: decidido, pronto para implementar
Substitui: [`esteira-visao-cedente.md`](../esteira-visao-cedente.md) (2026-08-20) e
[`2026-09-20-esteira-prospeccao-design.md`](./2026-09-20-esteira-prospeccao-design.md)

## Por que este documento existe

Havia duas specs do mesmo assunto, escritas com cinco semanas de distância e sem
saber uma da outra. Concordavam no essencial — escopo, não importar a planilha,
checklist de 13 + 5 tirado da aba `DOC'S PENDENTES` — e divergiam em seis decisões.

Além disso, as dez perguntas levantadas para o time não foram respondidas. Em vez de
travar, cada uma recebeu um **default proposto**, marcado como tal. Nenhum default é
chute: ou sai de evidência na planilha, ou do que o código já faz.

Este documento é a fonte única. Os dois anteriores ficam como histórico.

---

## Parte 1 — Reconciliação

| Ponto | Spec de agosto | Design de 20/set | Decidido | Por quê |
|---|---|---|---|---|
| Quem acessa | só analistas; comercial é texto livre | 4 papéis com login | **4 papéis** | decisão do Walter em 20/set, mais recente e explícita |
| Entrada | automática, `visaoCedente = SIM` | manual, comercial digita CNPJ | **as duas** | não são exclusivas; ambas caem em `TRIAGEM` |
| CNPJ | nullable, esteira antes do cadastro | obrigatório | **obrigatório** | é o nome da pasta no compartilhamento; sem ele não há upload |
| Documentos | facultativos, não travam | obrigatórios travam avanço | **travam, com escape** | trava vale, mas com `NAO_APLICAVEL` e `DISPENSADO` |
| Arquivos | compartilhamento de rede já montado | diretório próprio novo | **compartilhamento** | agosto tem razão: já montado, já no backup, é onde a equipe trabalha |
| Transição | livre, inclusive pra trás | máquina de estados rígida | **híbrido** | valida o avanço, permite o retrocesso, sempre com evento |

Três coisas da spec de agosto que o design de 20/set não tinha e entram inteiras:

- **`motivo_recusa` como enum**, com os valores que já se repetem na planilha, mais
  observação livre ao lado. Texto puro nunca vira métrica.
- **`usuario_nome` denormalizado** no evento — a timeline sobrevive a usuário
  removido.
- **Remoção lógica de arquivo.** O compartilhamento é usado por outras equipes;
  apagar byte de lá é destrutivo e irreversível. O registro sai da tela, o arquivo
  fica.

### Dois avisos de produção herdados de agosto

Valem para qualquer deploy, não só para esta entrega.

1. **`V50` está permanentemente queimado.** Não existe arquivo nem linha em
   `flyway_schema_history`. Como `spring.flyway.out-of-order` é `false`, criar um
   `V50__*.sql` derruba o boot. Nunca preencher a lacuna. As migrations desta
   entrega começam em **V56** (V52–V55 já foram usadas por sócios, partes ligadas e
   cheques).

2. **Conferir o checksum do V49 antes do próximo deploy.** O arquivo atual é uma
   recriação; se produção ainda tiver a linha de 2026-08-10, o deploy falha com
   `Migration checksum mismatch for migration version 49`.

   ```sql
   SELECT version, checksum, installed_on FROM flyway_schema_history WHERE version = '49';
   ```

   Se `installed_on` for 2026-08-10, rodar `flyway repair` antes de subir.

---

## Parte 2 — Os dez defaults

Cada um traz a base, o risco se estiver errado e o custo de mudar depois. Os quatro
marcados **caro** são os que exigem migration para corrigir; os demais são
configuração ou uma linha de regra.

### 1. Quem pega a análise — *o analista puxa da fila*

Sem distribuidor. A transição `TRIAGEM → EM_ANALISE` grava `analista_id` com update
condicional (`WHERE analista_id IS NULL`); a segunda analista que tentar recebe 409
com o nome de quem pegou.

- **Base:** a planilha tem cinco analistas e nenhuma coluna de quem distribui. A
  coluna `ANALISTA` é preenchida depois do fato.
- **Risco:** se houver distribuição informal, a fila vira corrida.
- **Custo de mudar:** baixo — `analista_id` já é editável por gestor.

### 2. Prazos — *1 / 2 / 10 dias úteis*

| Etapa | Prazo |
|---|---|
| Triagem até um analista pegar | 1 dia útil |
| Análise até aprovar ou reprovar | 2 dias úteis |
| Aprovado até documentação completa | 10 dias úteis |

Dias úteis, com feriado nacional em lista fixa. Amarelo a partir de 70% do prazo.

- **Base:** os dois primeiros saem das datas da aba `VISÃO CEDENTE`. O terceiro é
  deliberadamente uma meta, não uma medição: na aba `DOC'S PENDENTES` a coleta real
  leva de três semanas a dois meses (Café Rei: início 26/06, última 15/07, cobrança
  11/08). Colocar o prazo no tempo observado tornaria o vermelho inútil.
- **Risco:** 10 dias úteis pode pintar quase tudo de vermelho no começo.
- **Custo de mudar:** baixo — é configuração, e `prazo_estagio_dias` é snapshot por
  card, então mexer no valor não reescreve o passado.

### 3. Visibilidade — *comercial vê tudo, mexe só no dele*

Analista, backoffice e gestor veem e mexem em tudo.

- **Base:** equipe pequena (Gustavo, Tiago, Clóvis, Andréia, além de "DIRETO") e
  cobertura mútua em férias é o normal nesse tamanho.
- **Risco:** baixo. O oposto — esconder — é que costuma gerar pedido de exceção.
- **Custo de mudar:** baixo, é filtro na consulta.

### 4. Reprovada pode voltar — *sim, reabre para TRIAGEM*

`REPROVADO` deixa de ser terminal absoluto: gestor ou analista reabre, o card volta
para `TRIAGEM` e o motivo anterior fica na timeline com um marcador de reanálise.

- **Base:** a planilha tem empresa reprovada que depois foi habilitada.
- **Risco:** baixo; sem reabertura o time criaria card duplicado, que é pior.
- **Custo de mudar:** baixo.

### 5. Checklist — *obrigatórios com duas saídas*

Empresa: itens 1 a 10, 12 e 13 obrigatórios. O item 11 ("Possui filiais? Vai operar
pelas filiais?") **não é documento, é pergunta** — vira campo de resposta, não
bloqueia.

Sócio: RG/CPF ou CNH, comprovante de endereço e IRPF obrigatórios. Certidão de
casamento e documento do cônjuge ficam como "se houver".

Dois status de saída, e é o que reconcilia "trava" com "facultativo":

- **`NAO_APLICAVEL`** — automático. A certidão simplificada é da JUCESP, ou seja, só
  São Paulo; fora de SP o item já nasce assim. A UF vem do CNPJ Já. A planilha traz
  exatamente isso escrito à mão: *"Não tem, cliente de MG"*.
- **`DISPENSADO`** — manual, exige motivo e fica na timeline.

Um obrigatório em qualquer um dos dois não impede `DOCS_COMPLETOS`.

- **Custo de mudar:** baixo — o catálogo é editável pelo admin.

### 6. De quais sócios — *todos os sócios pessoa física do QSA* — **caro**

O bloco nasce de `company_shareholder`, um por sócio PF, com nome e CPF
preenchidos. Sócio pode ser marcado **inativo** com motivo, sem sumir da tela.

- **Base:** a planilha registra "vai sair da sociedade" e "Faleceu — recebemos
  certidão de óbito". Some sozinho seria perda de rastro.
- **Divergência com agosto:** a spec de agosto criava o bloco à mão e deixava o QSA
  para depois. Como `company_shareholder` já existe e está populado (V52/V53),
  automático agora custa quase nada.
- **Risco:** se na prática só quem assina precisa entregar documento, o checklist
  nasce inflado.
- **Custo de mudar:** **caro** — muda quantas linhas cada card gera.

### 7. O que conta como recebido — *chegou e foi conferido são estados diferentes* — **caro**

`PENDENTE → RECEBIDO → VALIDADO`, com `REJEITADO` (volta a pendente, com motivo),
mais `NAO_APLICAVEL` e `DISPENSADO`. Quem valida é o backoffice. Só `VALIDADO`,
`NAO_APLICAVEL` e `DISPENSADO` contam para fechar.

- **Base:** as observações da planilha são conferência de conteúdo, não recebimento:
  *"OK - CRC válido"*, *"OK - Sofisa, Bradesco, Itaú"*, *"OK - 7 Clientes"*. Alguém
  abre e lê.
- **Risco:** se ninguém confere de fato, `VALIDADO` vira clique vazio.
- **Custo de mudar:** **caro** — é o enum de status.

Cada item guarda **status estruturado e observação livre lado a lado**. É a
observação que carrega o dado real ("Válida até 2032").

### 8. Quem cobra — *o comercial dono do card; backoffice também pode*

Canais: e-mail, WhatsApp, ligação, reunião. Lembrete de recobrança a cada 3 dias
úteis sem evento.

- **Base:** a planilha tem colunas separadas de cobrança por e-mail e por WhatsApp,
  e a coluna `COMERCIAL` preenchida nas linhas ativas.
- **Custo de mudar:** baixo.

### 9. Quando desistir — *30 dias corridos sem resposta sugere remoção*

O sistema **sugere**, não remove. Some da fila ativa só por ato humano, com motivo.
Sair do radar não é definitivo: a empresa pode voltar em card novo, e o histórico
antigo fica linkado.

- **Base:** a aba `REMOVIDAS DO RADAR` tem 193 linhas — é desfecho frequente, não
  exceção. Os motivos citam "após diversas cobranças" e "análise há mais de 3
  meses".
- **Custo de mudar:** baixo.

### 10. Motivos de recusa — *enum fechado mais observação*

`QUANTIDADE_DE_RESTRICOES`, `SEGMENTO`, `PEFIN_COM_FUNDO`, `PROCESSO_COM_FIDC`,
`RECUPERACAO_JUDICIAL`, `DECISAO_DIRETORIA`, `OUTRO`.

- **Base:** extraídos dos padrões que já se repetem na coluna de motivo.
- **Custo de mudar:** baixo — acrescentar valor é trivial.

---

## Parte 3 — Modelo consolidado

### Estágios

```
TRIAGEM ──> EM_ANALISE ──┬──> APROVADO ──> DOCS_PENDENTES ⇄ cobrança
                         │                      │
                         │                      v
                         │                 DOCS_COMPLETOS ──> PRONTO_HABILITACAO
                         │
                         └──> REPROVADO ──(reabertura)──> TRIAGEM

REMOVIDO_RADAR: a partir de qualquer estágio, com motivo
```

Entram em `TRIAGEM` por dois caminhos: o comercial cria com o CNPJ, ou uma análise
de crédito resulta `visaoCedente = SIM`. O sinal calculado e o veredito humano ficam
lado a lado; um nunca sobrescreve o outro — mesmo padrão já validado na praça de
pagamento.

Sem backfill automático das análises antigas. Um botão manual "puxar visão cedente
existentes" fica na tela, para a analista decidir.

Regras de avanço: `DOCS_COMPLETOS` exige todo obrigatório resolvido;
`REPROVADO` e `REMOVIDO_RADAR` exigem motivo. Retrocesso é livre para analista e
gestor. Toda transição grava evento na mesma transação.

### Migrations

| Versão | Tabela |
|---|---|
| V56 | `prospeccao` — o card |
| V57 | `prospeccao_evento` — timeline append-only, com `usuario_nome` denormalizado |
| V58 | `documento_tipo` (catálogo) e `prospeccao_documento` (checklist do card) |
| V59 | `prospeccao_arquivo` — metadado do arquivo, com remoção lógica |

Papéis novos (`ROLE_COMERCIAL`, `ROLE_ANALISTA`, `ROLE_BACKOFFICE`, `ROLE_GESTOR`)
entram no V56. `ROLE_USER` existente migra para `ROLE_COMERCIAL`; `ROLE_ADMIN` fica
irrestrito.

### Arquivos

Byte no compartilhamento de rede que o backend já monta; metadado no banco.

```
{base}/{cnpj}/esteira/{categoria}/{codigo}/v{n}-{nome-sanitizado}
```

`categoria` é `empresa` ou `socio-{slug}`. Base resolvida em runtime por
`requireDocumentStorageBasePath`, nunca gravada absoluta. Teto de 10MB e allowlist
de mime iguais às do Portal Comercial. Download por endpoint autenticado com
`Content-Disposition`. A validação de travessia de caminho de
`CompanyDocumentService` é reaproveitada sem exceção.

Upload exige CNPJ vinculado — o CNPJ é o nome da pasta. Não atrapalha: coleta
acontece depois da aprovação.

Se o compartilhamento não estiver montado, a tela diz "compartilhamento
indisponível" em vez de estourar erro genérico. Escrita em SMB montado via WSL é
lenta e a trava de arquivo é fraca: upload precisa de barra de progresso e o backend
de timeout generoso.

---

## Parte 4 — Ordem de implementação

1. V56 a V59, entidades, repositórios, DTOs
2. `ProspeccaoService` — máquina de estados, veredito, evento
3. Catálogo de documentos, materialização do checklist, regra de UF da JUCESP,
   blocos de sócio a partir do QSA
4. `ProspeccaoArquivoService` sobre a base de documentos
5. `ProspeccaoController` e autorização por papel
6. Entrada automática por `visaoCedente = SIM` e botão de backfill
7. Página `/prospeccao` — kanban, painel lateral, timeline, checklist
8. SLA: job agendado, alertas, badge
9. Ponta a ponta com empresa real: entra, aprova, sobe arquivo, confere que o
   arquivo aparece na pasta `CLIENTES` do compartilhamento

---

## Parte 5 — O que ainda quer confirmação humana

Os defaults destravam a implementação. Dois continuam valendo a pergunta, porque
corrigir depois custa migration:

- **De quais sócios** exigir documento (default: todos os PF do QSA)
- **Se alguém confere** o documento recebido (default: sim, backoffice valida)

Os outros oito se ajustam por configuração.
