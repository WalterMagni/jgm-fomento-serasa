# Esteira de prospecção unificada — design

Data: 2026-10-11
Status: validado com o Walter, seção a seção, em 2026-10-11. Fase A implementada (A1–A5) na
mesma data, migrations V74–V77; produção ainda não recebeu.
Branch: `feat/esteira-unificada`, criada a partir de `feat/esteira-liberacao` (`2436ee9`)
Meta: Fase A completa na terça, 2026-10-13

## Por que existe

O Portal Comercial (Next.js + Supabase, projeto irmão `PORTAL-COMERCIAL`) será
descontinuado. A esteira de prospecção do Portal Serasa passa a ser a única, e precisa dos
atributos que o Comercial tinha: dados comerciais, pendência de informação, decisão com
limite e condições por produto, aceite do cliente com contraproposta, comitê e contrato.

A esteira atual (V56–V62, em produção desde 2026-09-29) cobre bem a documentação, com
checklist pelo QSA, regra dos 15%, itens por UF e relógio de silêncio. A nova esteira junta
os dois fluxos.

O trabalho foi dividido em duas fases:

- **Fase A — o portal 100%.** A esteira completa, operada pela equipe interna. Este documento.
- **Fase B — o comercial externo.** Perfil `COMERCIAL`, cadastro por código, tela para
  celular e exposição por túnel. Terá um design próprio; as decisões já tomadas estão no
  fim deste documento.

## Premissas da Fase A

- **A prospecção nasce dentro do sistema.** Pode ser aberta pelo quadro, pela página da
  empresa ou pela entrada automática da visão cedente. O comercial é um dado opcional do
  card, e cliente direto não tem comercial.
- **A equipe interna é dona dos documentos.** Qualquer pessoa da equipe anexa; a analista
  valida. O checklist não depende do comercial.
- **Ações que no futuro serão do comercial** (responder pendência, registrar o aceite do
  cliente) são feitas pela analista e ficam gravadas como "em nome do comercial".
- **Os cards em produção serão revistos com o time.** A migração mapeia os estágios e marca
  esses cards como legado; o time completa o que faltar.

## 1. Máquina de estados

| Estágio | Quem age | Sai para | Prazo |
|---|---|---|---|
| **NOVA** | a analista **assume**; a segunda analista que tentar recebe 409 | EM_ANALISE | 12h úteis |
| **EM_ANALISE** | a analista dona | **aprovar** (limite, condições e parecer) leva a AGUARDANDO_ACEITE; **reprovar** (com motivo) leva a REPROVADO | 24h úteis, pausado enquanto houver pendência aberta |
| **AGUARDANDO_ACEITE** | a analista, em nome do comercial | **aceitou** leva a DOCUMENTACAO; **recusou** leva a RECUSADO_CLIENTE; **contraproposta** volta a EM_ANALISE | silêncio de 30/45 dias |
| **DOCUMENTACAO** | a equipe anexa, a analista valida | COMITE, desde que os obrigatórios estejam fechados (senão 422 com a lista do que falta) | silêncio de 30/45 dias |
| **COMITE** | os membros do comitê (`users.comite`) | **aprovar** leva a CONTRATO; **devolver** leva a EM_ANALISE; **reprovar** leva a REPROVADO; qualquer saída exige ao menos 1 parecer | — |
| **CONTRATO** | a analista dona | **concluir** leva a HABILITADO | — |
| HABILITADO, REPROVADO, RECUSADO_CLIENTE, INERTE | qualquer analista | **reabrir** leva a NOVA | — |

Retrocessos manuais permitidos: EM_ANALISE volta a NOVA, DOCUMENTACAO volta a EM_ANALISE e
CONTRATO volta a COMITE.

Decisões de desenho:

1. **A pendência é um objeto, não um estágio.** No Comercial, `PENDENTE_INFO` e
   `PENDENTE_DOC` eram status. Quando o último documento pendente era apagado em vez de
   aceito, a requisição ficava presa, sem saída. Aqui o card continua em EM_ANALISE com o
   selo "aguardando comercial" e o relógio pausado.
2. **O checklist nasce na abertura.** O QSA existe desde o CNPJ, então a equipe pode anexar
   documentos desde o primeiro dia. A exigência só trava a passagem de DOCUMENTACAO para
   COMITE.
3. **A decisão é versionada.** No Comercial, a contraproposta somava as condições novas às
   antigas. Aqui cada decisão gera uma linha nova: a vigente fica em destaque e as
   anteriores, no histórico.
4. **O estágio Dossiê do Comercial deixa de existir.** Ele é coberto por DOCUMENTACAO.

## 2. Modelo de dados (V74 em diante)

### Colunas novas em `prospeccao`

- **Dados comerciais.** Colunas tipadas, para permitir filtro e relatório:
  - valores: `faturamento_mensal`, `ticket_medio`, `valor_medio_operacoes`;
  - prazos: `prazo_medio_vendas`, `prazo_medio_pgto_fornecedor`, `prazo_medio_entrega`, `possui_canhotos_dias`;
  - booleanos: `opera_mercadoria_entregue`, `opera_mercadoria_embarcada`;
  - textos: `regiao_atendimento`, `produtos`, `parceiros_atuais`, `outros`.
- **Contato do contrato:** nome, telefone e e-mail.
- **Comitê:** `rodada_comite`.
- **Trava otimista:** `version`.
- **Legado:** a flag `legado` marca os cards migrados que ainda não têm decisão.
- **Prazo:** o tempo pausado acumulado, em horas.

### Tabelas novas

| Tabela | Conteúdo |
|---|---|
| `produto_catalogo` | DUPLICATA, CHEQUE PRÓPRIO, CHEQUE DE TERCEIROS, PEDIDO, ESCROW; editável pelo admin |
| `prospeccao_decisao` | resultado, limite, parecer, autor, data e `vigente`; uma linha por decisão |
| `prospeccao_decisao_condicao` | produto e valor, ligados a uma decisão |
| `prospeccao_aceite` | ACEITO, RECUSADO ou CONTRAPROPOSTA; limite e condições sugeridos, mensagem, quem registrou e "em nome do comercial" |
| `prospeccao_pendencia` | pergunta, campos exigidos, documentos pedidos, resposta, status (ABERTA, RESPONDIDA ou CANCELADA), autoria e datas |
| `prospeccao_parecer` | mesmo formato de `liberacao_parecer`: uma linha por membro do comitê por rodada |
| `prospeccao_comentario` | mesmo formato de `liberacao_comentario`, com menções `@` |

### O que é reaproveitado sem mudar a tabela

`notificacao`, o SSE, o e-mail, o `MencaoParser`, o compartilhamento SMB, o catálogo de
documentos e o checklist.

As tabelas da liberação não são generalizadas: a liberação está em uso, e o risco não
compensa. O que se compartilha é código (o enum de posição, o componente de parecer, o
parser de menções), não dado.

### Migração dos cards existentes

| Estágio atual | Estágio novo |
|---|---|
| TRIAGEM | NOVA |
| EM_ANALISE | EM_ANALISE |
| APROVADO, DOCS_PENDENTES | DOCUMENTACAO, marcado como legado |
| DOCS_COMPLETOS | DOCUMENTACAO, marcado como legado |
| PRONTO_HABILITACAO | HABILITADO |
| REPROVADO | REPROVADO |
| REMOVIDO_RADAR | INERTE |

Os eventos são preservados. Antes de aplicar em produção: backup, ensaio sobre uma cópia
do banco e aviso ao time de que as colunas mudaram de nome.

## 3. Telas

### Quadro (`/prospeccao`)

- **Colunas:** seis — NOVA, EM ANÁLISE, AGUARDANDO ACEITE, DOCUMENTAÇÃO, COMITÊ e CONTRATO.
  Os terminais (HABILITADO dos últimos 90 dias, REPROVADO, RECUSADO e INERTE) ficam numa
  seção recolhida.
- **Arrasto:**
  - Feito com `@dnd-kit`, como na liberação, com suporte a toque e a teclado. O
    `window.prompt` sai.
  - O card só pode ser solto num destino permitido.
  - Quando o destino exige dados (decisão, motivo ou aceite), soltar o card abre o diálogo
    correspondente.
- **Frente do card:**
  - razão social, CNPJ, comercial, analista e prazo;
  - selos: aguardando comercial, limite R$ X, docs 6/9, comitê 1/3 e legado.
- **Filtros:** comercial, analista, atrasados, aguardando comercial e "só minhas".
- **Nova prospecção:** o diálogo pede CNPJ, razão social, o comercial (opcional, escolhido
  na lista) e os dados comerciais (opcionais, recolhidos).

### Página da empresa (`/clients/[cnpj]`)

Ganha um bloco **Prospecção**:

- se existe card aberto, mostra o estágio, a analista e o link para o card;
- se não existe, mostra o botão **Abrir prospecção**, com CNPJ e razão social já preenchidos.

### Detalhe do card

O detalhe abre num modal, em duas colunas a partir de `lg`.

- **Topo:**
  - nome, CNPJ, estágio, comercial e analista;
  - a barra de ações, que mostra só o que vale no estágio atual;
  - a trilha das etapas, com a data de cada passagem.
- **Abas:**

| Aba | Conteúdo |
|---|---|
| Resumo | sinal da análise de crédito (link para a ficha), decisão vigente, pendências abertas |
| Dados comerciais | os 13 campos, editáveis; os campos exigidos por uma pendência aparecem com `*` |
| Documentos | o checklist atual |
| Decisão e aceite | a decisão vigente e o histórico; as respostas do cliente |
| Comitê | os pareceres da rodada atual e das anteriores |
| Contrato | o contato e a conclusão |

- **Coluna lateral:** comentários com `@` e a linha do tempo.

### Diálogos

- **Decidir:**
  - aprovar ou reprovar;
  - limite;
  - condições, em N linhas de produto e valor;
  - parecer.

  Se a soma das condições passar do limite, aparece um aviso, mas a decisão não é
  bloqueada.
- **Pendência:** a pergunta, os campos exigidos e os documentos pedidos.
- **Aceite:** aceitou, recusou ou contraproposta. A contraproposta exige mensagem.
- **Parecer:** posição e texto. Só membros do comitê.

O PDF do parecer, que existia no Comercial, fica fora da Fase A e entra se o time pedir.

## 4. Backend

### Máquina de estados

`EstagioProspeccao` é reescrito com os 10 estágios e os destinos permitidos.

O endpoint genérico `PATCH /{id}/estagio` aceita só movimentos que não exigem dados. Para
os outros, responde 422.

### Endpoints novos (`/api/v1/prospeccao`)

| Endpoint | O que faz |
|---|---|
| `POST /{id}/decisao` | aprova ou reprova |
| `POST /{id}/aceite` | registra aceitou, recusou ou contraproposta |
| `POST /{id}/pendencias` | abre uma pendência |
| `PATCH /pendencias/{pid}/resposta` | responde uma pendência |
| `PATCH /pendencias/{pid}/cancelar` | cancela uma pendência |
| `POST /{id}/comite` | abre a rodada e cria um parecer vazio para cada membro |
| `PATCH /{id}/parecer` | registra o parecer de um membro |
| `POST /{id}/comite/resultado` | aprova, devolve ou reprova |
| `PATCH /{id}/dados-comerciais` | edita os dados comerciais |
| `PATCH /{id}/contrato` | edita o contato do contrato |
| `POST /{id}/concluir` | leva o card a HABILITADO |
| `/{id}/comentarios` | CRUD de comentários |
| `/produtos` | CRUD do catálogo de produtos (só admin) |

### Serviços

`ProspeccaoDecisaoService`, `ProspeccaoPendenciaService`, `ProspeccaoComiteService`,
`ProspeccaoComentarioService` e `ProspeccaoNotificador`. O `ProspeccaoService` fica só com o
núcleo.

### Permissões

| Ação | Quem pode |
|---|---|
| Assumir | qualquer analista |
| Abrir pendência, **decidir**, validar documento, enviar ao comitê, concluir | **a analista dona** (o admin também) |
| Parecer | quem tem `users.comite` |
| Registrar aceite, responder pendência | qualquer analista, gravado como "em nome do comercial" |
| Comentar, anexar documento, editar dados comerciais | qualquer usuário autenticado |

### Prazo

O tempo de pendência aberta é acumulado quando a pendência fecha e descontado na leitura.
Não há job agendado.

### Notificações

Usam sino, SSE, som e e-mail, e respeitam a preferência de cada usuário.

| Evento | Quem é notificado |
|---|---|
| Card novo | analistas |
| Pendência respondida, aceite, parecer, devolução do comitê | a analista dona |
| Card entrou no comitê | os membros do comitê |
| Menção | o mencionado |
| Comentário | a analista dona e quem já participou |

As notificações para o comercial ficam escritas, mas desligadas até a Fase B.

### Linha do tempo

Novos tipos de evento: DECISAO, ACEITE, PENDENCIA_ABERTA, PENDENCIA_RESPONDIDA,
COMITE_ENVIADO, PARECER e COMITE_RESULTADO.

### Exportação

O CSV ganha decisão, limite, condições e aceite.

### Testes

- **Unitários:** transições, permissões e desconto da pausa no prazo.
- **Integração:** a V74 aplicada sobre uma cópia do banco de produção.
- **E2E (Playwright):** de NOVA até HABILITADO.

## 5. Entrega da Fase A

| Fatia | Conteúdo |
|---|---|
| A1 — Fundação | V74, enum, quadro com `@dnd-kit`, modal em abas (Resumo, Dados comerciais, Documentos), abertura pela página da empresa |
| A2 — Decisão e aceite | decisão versionada, condições por produto, aceite, contraproposta |
| A3 — Conversa | pendências com pausa, comentários com `@`, notificações |
| A4 — Comitê e contrato | rodadas de parecer, resultado, contrato, HABILITADO |
| A5 — Acabamento | filtros, exportação, manual (`frontend/src/content/manual-esteira.ts`, a fonte única), E2E |

A1 é a fatia arriscada, porque renomeia os estágios de cards reais. O manual é atualizado
junto com cada fatia, não no final.

## Fase B — decisões já tomadas (o design vem depois)

- **Perfil `COMERCIAL`:**
  - acesso só a `/api/v1/comercial/**`;
  - vê apenas os próprios cards.
- **Cadastro:** por código de uso único (`XXXX-XXXX`, válido por 24h), gerado por admin ou
  analista.
- **O que o comercial vê da análise:** só um **sinal resumido** — o badge de visão cedente
  e o semáforo da pré-análise, com uma frase curta. Score, negativações e o relatório do
  Serasa não aparecem para ele.
- **Pré-análise automática com freios:**
  - os dados gratuitos (Receita, QSA, CNPJ Já) são buscados sempre;
  - a consulta paga ao Serasa reaproveita uma análise recente do mesmo CNPJ e respeita um
    teto diário por comercial;
  - acima do teto, a consulta fica para a analista disparar.
- **Tela do comercial:**
  - área própria `(comercial)`, pensada primeiro para o celular;
  - no hostname externo, o middleware do Next serve só essa área.
- **Exposição:**
  - Cloudflare Tunnel em `comercial.<dominio>`;
  - regra de caminho que deixa passar só o login, `/api/v1/comercial/**` e a área do
    comercial;
  - nenhuma porta aberta no servidor.
- **Obrigatório antes de abrir ao público:**
  - fechar `/api/auth/register`;
  - fechar `/actuator`;
  - trocar o segredo JWT padrão;
  - remover o seed `admin123`;
  - rate limit no login.
