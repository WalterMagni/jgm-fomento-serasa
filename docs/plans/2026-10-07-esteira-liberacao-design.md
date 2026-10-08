# Esteira de liberação de operações — design

Data: 2026-10-07
Status: validado com o Walter, seção a seção, em 2026-10-07
Branch: `feat/esteira-liberacao`, criada a partir de `feat/esteira-prospeccao` (`7a1f768`)

## Por que existe

O departamento controla o dia a dia da liberação de operações numa planilha com cinco
colunas: Origem da análise, Comitê, Pendência, Aprovado, Reprovado. A planilha não avisa
ninguém quando um card muda de mãos, não guarda quem mexeu e não deixa marcar pessoas
ou empresas. A esteira leva esse quadro para o portal, no formato de um Trello, com
notificação em tempo real.

Pessoas envolvidas hoje: seis auxiliares que abrem os cards, duas analistas
(Andressa e Mychelly) que formam o Comitê, e a Diretoria.

## 1. Etapas, papéis e permissões

### Etapas

`ORIGEM → COMITE → PENDENCIA → APROVADO | REPROVADO`

### Marcas de usuário

O admin (e-mail listado em `APP_USER_MANAGEMENT_ALLOWED_EMAILS`) marca cada usuário na
tela Settings:

- **Analista**: edita e move cards a partir do Comitê.
- **Comitê**: parecer obrigatório no Comitê. Só aceita quem também é Analista.

Andressa e Mychelly recebem as duas marcas. A Diretoria recebe só Analista: pode editar
em qualquer etapa, mas não entra na conta dos pareceres.

### Transições

| De → Para | Quem | Regra |
|---|---|---|
| Origem → Comitê | qualquer usuário | cria um parecer pendente para cada membro do Comitê naquele momento e notifica cada um |
| Comitê → Pendência, Aprovado ou Reprovado | analista | bloqueado até todos os pareceres da rodada estarem registrados |
| Comitê → Origem | analista | devolução para correção; abre uma rodada nova de pareceres |
| Pendência → Aprovado, Reprovado ou Comitê | analista | os pareceres já dados continuam valendo |
| Aprovado ou Reprovado → Comitê | analista | reabertura, registrada no histórico |

### Parecer

Cada membro do Comitê registra uma posição (Favorável, Com ressalvas, Desfavorável) e um
texto com menções. O card mostra o estado de cada membro, por exemplo
`Andressa ✓ · Mychelly ⏳`. Quem registra o último parecer da rodada é avisado de que o
card está liberado para mover.

### Pendência

A analista abre uma ou várias pendências no card, cada uma com um destinatário (qualquer
usuário) e um texto. O destinatário responde e marca como respondida, e a analista que
abriu é notificada. Mover para Aprovado com pendência aberta pede confirmação, mas não
bloqueia.

### Permissões

| Ação | Origem | Comitê em diante |
|---|---|---|
| Criar | todos | — |
| Editar campos, etiquetas, cor e membros | todos | analista |
| Apagar | todos | analista |
| Comentar e ser marcado | todos | todos |
| Responder pendência | — | destinatário |

### Exclusão e auditoria

Apagar é exclusão lógica: o card some do quadro, mas fica no banco com quem apagou e
quando. A prospecção usa exclusão física; aqui a escolha é diferente porque se trata de
liberação de dinheiro, e a auditoria pesa mais do que limpar a fila. Restaurar um card
apagado fica para depois, se o time pedir.

Toda criação, edição de campo, movimento, parecer, pendência e comentário gera um evento
append-only com usuário, nome copiado, data, hora e o que mudou
(`valor: R$ 50.000 → R$ 80.000`). O card mostra a última edição, por exemplo
"editado por Aline · 07/10 14:32".

## 2. Conteúdo do card

### Campos

- **Cedente**: busca por nome ou CNPJ na base de empresas. O título é gerado
  automaticamente: `RAZÃO SOCIAL · 12.345.678/0001-90`. CNPJ fora da base é aceito, com
  aviso "Empresa não cadastrada" e botão para cadastrar em nova aba.
- **Tipo de operação**: opcional. Padrões Duplicata, Cheque, Comissária e Intercompany, mais
  os tipos que o time criar na hora (V68: texto livre, casado sem diferenciar maiúscula nem
  acento com os existentes; tipo sem card que o use sai da lista sozinho).
- **Valor da operação** em reais, opcional.
- **Prazo**: data e hora, opcional. O chip fica âmbar faltando menos de 24 horas e
  vermelho depois do vencimento.
- **Sacados**: lista de CNPJ, nome resolvido e valor opcional. Cada sacado abre a página
  da empresa em nova aba. O rodapé mostra a soma dos sacados e avisa quando ela diverge
  do valor da operação.
- **Parecer da origem**: texto livre com menções.

### Recursos do Trello que entram

- **Etiquetas** coloridas, compartilhadas pela esteira toda. Qualquer usuário cria na
  hora.
- **Cor do card**: faixa no topo, paleta de oito cores.
- **Membros**: avatares com iniciais. Entram automaticamente o criador, os membros do
  Comitê e quem recebe pendência; dá para adicionar à mão. Membros são notificados quando
  o card muda de coluna. Ajuste da fase 3: quem comenta ou é marcado também passa a
  acompanhar o card, como no GitHub, para receber a resposta da conversa em que entrou.
- **Comentários**: thread com menções. O autor edita e apaga o próprio comentário, que
  fica marcado como editado.
- **Atividade**: comentários e eventos numa linha do tempo única, com filtro "só
  comentários".

### Fora de escopo

Checklist (as pendências cumprem esse papel), anexos (os arquivos ficam na página da
empresa), votos, capa com imagem.

### Face do card

```
┌─────────────────────────────────┐
▌██████████ faixa de cor ██████████▌
│ [Urgente] [Cliente novo]         │
│ ACME TÊXTIL LTDA                 │
│ 12.345.678/0001-90               │
│ Duplicata · R$ 80.000,00         │
│ ⏰ 08/10 18h  💬 3  ⚠ 1 pend.    │
│ Parecer: AN ✓  MY ⏳     (AL)(WM)│
└─────────────────────────────────┘
```

### Detalhe

Modal largo em duas colunas. À esquerda: campos, sacados, parecer da origem, pareceres
do Comitê, pendências e atividade. À direita: Membros, Etiquetas, Cor, Prazo, Mover,
Apagar. Ações que o usuário não pode fazer naquela etapa aparecem desabilitadas, com
tooltip explicando o motivo.

## 3. Menções e links de empresa

- Um único gatilho, `@`, abre um popover com dois grupos: Pessoas e Empresas. `@` seguido
  de números busca empresa por CNPJ. Setas navegam, Enter ou Tab confirmam, Esc fecha.
- Pessoas são filtradas localmente (a lista é pequena e carregada uma vez). Empresas são
  buscadas no servidor 200 ms depois da última tecla.
- CNPJ digitado ou colado sem `@`, formatado ou não, também vira link.
- Formato salvo: texto puro com marcação, `@[Andressa Lima](user:<uuid>)` e
  `@[ACME](cnpj:12345678000190)`. A busca e o XLSX continuam legíveis, e a menção
  sobrevive à troca de nome da pessoa.
- Exibição: pessoa vira chip, mais forte quando é o próprio usuário. Empresa cadastrada
  vira chip sólido que abre a página da empresa em nova aba. Empresa não cadastrada vira
  chip tracejado que abre um popover "Empresa não cadastrada" com botão "Cadastrar agora".
- A resposta do card já informa quais CNPJs mencionados existem na base, para o front não
  consultar chip a chip.
- O backend lê as menções ao salvar e notifica apenas quem é novo no texto.
- Endpoints novos: `GET /api/v1/usuarios/diretorio` (todos os usuários autenticados; id,
  nome, iniciais, analista, comitê; sem e-mail) e `GET /api/v1/company/busca?q=`
  (reaproveita `searchByNameOrDocument`, limite 8).
- Componente `MentionTextarea` em `components/ui/`: textarea comum com popover, sem editor
  rico, para evitar os problemas de cursor e de colagem do `contenteditable`.

## 4. Notificações

### Quem recebe

Quem fez a ação nunca é notificado.

| Evento | Destinatário |
|---|---|
| Card criado na Origem | analistas |
| Card entrou no Comitê | membros do Comitê |
| Parecer registrado | demais membros do Comitê |
| Último parecer da rodada | membros do Comitê ("liberado para decidir") |
| Pendência aberta | destinatário |
| Pendência respondida | analista que abriu |
| Menção em parecer, comentário ou pendência | mencionado |
| Comentário novo | membros do card |
| Card aprovado, reprovado ou devolvido | criador e membros |

### No navegador

- Sino na barra do topo, entre o botão de tema e o nome do usuário, com contador
  vermelho (`99+` no máximo).
- O painel do sino lista as 20 últimas, destaca as não lidas e leva ao card ao clicar,
  marcando a notificação como lida. Tem "Marcar todas como lidas".
- Toast no canto com botão Abrir.
- Título da aba com o contador: `(3) JGM Fomento`.
- Som curto gerado com Web Audio, no máximo um a cada 3 segundos, tocado por uma única
  aba quando há várias abertas. Liga e desliga no painel; a preferência fica no perfil do
  usuário (`som_notificacao`), e não no navegador.
- Notificação da área de trabalho opcional, ativada por botão no painel.

### Transporte

- Server-Sent Events com `SseEmitter`, que já vem no Spring Web.
- O front troca o JWT por um ticket de 60 segundos e uso único, e abre o `EventSource`
  com ele. O JWT de 8 horas nunca vai na URL.
- Conexões guardadas em memória por usuário. Basta porque o backend roda numa instância.
- Os eventos saem depois do commit da transação (`TransactionSynchronization.afterCommit`),
  como o enriquecimento Bacen já faz.
- Dois tipos de evento: `notificacao`, só para o destinatário, e `quadro`, para todos,
  avisando que um card mudou para o quadro se atualizar sozinho.
- Batimento a cada 25 segundos. Se a conexão cair, o navegador reconecta e o front
  rebusca; enquanto isso, consulta a cada 30 segundos.

### Fora de escopo

Aviso de prazo vencendo por agendador. A cor do chip já sinaliza.

## 5. Quadro, filtros e relatório

- Rota `/liberacao`. Item de menu "Esteira de Liberação" (BETA) abaixo da prospecção, com
  contador do que espera o usuário: parecer pendente dele mais pendências destinadas a
  ele.
- Cinco colunas com nome, quantidade e soma em reais no cabeçalho. "+ Novo card" no topo
  da Origem abre uma criação rápida (busca o cedente, cria e abre o detalhe).
- Arrastar com `@dnd-kit`, porque o arrasto nativo do HTML5 usado na prospecção não
  funciona em toque nem no teclado. Colunas proibidas esmaecem durante o arrasto e mostram
  o motivo. O movimento é otimista e desfeito se o servidor recusar.
- Sem reordenação manual dentro da coluna; a ordenação cobre o caso.
- Filtros: busca (cedente, CNPJ de sacado, texto), membro, etiqueta, tipo, criado por,
  período de criação, prazo (vencido, hoje, esta semana), "só os meus" e "ocultar
  finalizados". Finalizados mostram por padrão os últimos 30 dias, com "carregar mais
  antigos".
- Os filtros ficam na URL: sobrevivem ao recarregar e o link pode ser compartilhado.
- Ordenação: prazo mais próximo (padrão), última atividade, mais recentes, mais antigos,
  maior valor, nome do cedente.
- Abaixo da largura de tablet, uma coluna por vez com abas; mover pelo botão do detalhe.
- Relatório XLSX com Apache POI, respeitando os filtros da tela (a tela manda os ids dos
  cards visíveis), com seis abas: Cards, Sacados, Pareceres, Pendências, Histórico e
  Comentários — a sexta entrou na fase 4, junto com as horas que cada card passou em cada
  etapa. Cabeçalho congelado, filtro automático, moeda e data formatadas.
- Visual alinhado ao portal: bordô `#612035`, laranja `#D1732C`, modo escuro.

## 6. Backend, testes e entrega

### Tabelas (a partir da V63)

- `users`: colunas `analista`, `comite` e `som_notificacao`, com restrição de que comitê
  exige analista.
- `liberacao_card`: etapa, cedente (CNPJ e nome copiado), tipo, valor, prazo, parecer da
  origem, cor, criado e editado por e em, `finalizado_em`, `excluido_em`, `excluido_por`,
  `version`.
- `liberacao_sacado`, `liberacao_etiqueta`, `liberacao_card_etiqueta`, `liberacao_membro`.
- `liberacao_parecer` com `rodada`: devolver para a Origem abre uma rodada nova em vez de
  apagar os pareceres.
- `liberacao_pendencia`, `liberacao_comentario` (exclusão lógica).
- `liberacao_evento`: append-only, com campo, valor anterior, valor novo, autor e data.
- `notificacao`: genérica, com link em vez de chave estrangeira para o card, para outras
  telas reaproveitarem.

A coluna `version` dá trava otimista: se duas pessoas editam o mesmo card, a segunda a
salvar recebe "o card foi alterado por outra pessoa, recarregue" em vez de sobrescrever.

### Código

- `domain/model/liberacao/EtapaLiberacao`: enum com os destinos permitidos.
- `application/service/liberacao/`: `LiberacaoService`, `LiberacaoAutorizacao` (a matriz
  de permissões num lugar só), `MencaoParser`, `LiberacaoExportService`.
- `application/service/notificacao/`: `NotificacaoService`, `SseHub`.
- Controllers: `/api/v1/liberacao`, `/api/v1/notificacoes`, `/api/v1/usuarios/diretorio`,
  `PATCH /api/auth/users/{id}/papeis` (admin).
- A checagem de admin por `APP_USER_MANAGEMENT_ALLOWED_EMAILS` hoje está copiada em dois
  controllers; vai para um componente único antes de ganhar o terceiro uso.

### Testes

- Unitários: transições do enum, matriz de permissões (papel × etapa × ação),
  `MencaoParser` (marcação, CNPJ solto, notificação só para menção nova).
- Integração com Testcontainers: trava do Comitê, rodada nova ao devolver, pendência
  respondida notifica, exclusão lógica, conflito de `version`, XLSX com as cinco abas.
- Ponta a ponta no navegador: dois usuários em abas diferentes; card criado numa faz o
  sino e o som dispararem na outra.
- `scripts/liberacao-cenarios.sh` com `--limpar`, como na prospecção.
- Manual da tela em `frontend/src/content/manual-liberacao.ts`.

### Entrega em quatro fases

Cada fase roda e é testada antes da seguinte.

1. **Núcleo**: marcas no Settings, quadro, criar, editar, mover e apagar, Comitê com
   pareceres, pendências, histórico.
2. **Menções e comentários**: `MentionTextarea`, links de empresa, comentários.
3. **Notificações**: SSE, sino, toast, som, área de trabalho.
4. **Trello e relatório**: etiquetas, cor, membros, filtros, ordenação, ocultar
   finalizados, XLSX, layout de celular, manual.
