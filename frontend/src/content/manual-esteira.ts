/**
 * Manual de uso da esteira, em markdown.
 *
 * <p>Esta é a fonte única do texto: é ela que o time lê no portal, pelo botão Ajuda da tela da
 * esteira. O arquivo em docs/ aponta para cá justamente para não existirem duas versões
 * divergindo em silêncio.</p>
 */
export const MANUAL_ESTEIRA = `# Esteira de prospecção — manual de uso

Para quem acompanha a prospecção no portal: comercial, analista de crédito e backoffice.

A esteira substitui as abas \`VISÃO CEDENTE\` e \`DOC'S PENDENTES\` da planilha
\`CONTROLE DE EMPRESAS\` e absorve o fluxo do antigo Portal Comercial. Cada empresa vira um
card, e o card anda da abertura até o cedente habilitado: análise, aceite do cliente,
documentação, comitê e contrato.

A tela está marcada como **beta**: o fluxo ainda pode mudar conforme o time usar.

---

## Onde fica

Menu lateral, **Esteira de Prospecção**. O número vermelho ao lado do nome é quanto
precisa de atenção hoje — atrasado ou sem resposta do cliente.

---

## Como uma empresa entra

**Você mesmo abre.** Botão **Nova prospecção** no topo da esteira, ou **Abrir prospecção**
na página da empresa (lá o CNPJ e o nome já vêm preenchidos). O CNPJ é obrigatório: é por ele
que a esteira conversa com a análise de crédito, com as filiais e com a pasta de documentos
no compartilhamento.

O **comercial** é opcional: escolha da lista, digite o nome de quem ainda não tem acesso ao
portal, ou deixe "sem comercial" para cliente direto. Os **dados comerciais** (faturamento,
ticket médio, prazos…) também podem ser preenchidos já na abertura, ou depois.

**O sistema abre sozinho.** Sempre que uma consulta ao Serasa indica perfil de cedente,
a empresa aparece em Nova automaticamente. O card mostra "VC" no rodapé.

**Você traz o que já existia.** Botão **puxar visão cedente**, aba *Escolher empresas*:
busque por razão social ou CNPJ, marque o que quiser e traga. A aba *Trazer um lote* serve
para a carga inicial — ela mostra quantas empresas entrariam antes de você confirmar e
limita o tamanho do lote, porque não há como desfazer em massa.

---

## As colunas

| Coluna | O que significa |
|---|---|
| **Nova** | Chegou. Ninguém pegou ainda. |
| **Em análise** | Uma analista assumiu e é a dona do card até o fim. |
| **Aguardando aceite** | Aprovado pela analista; falta o cliente responder. |
| **Documentação** | Coleta e conferência dos documentos. |
| **Comitê** | O comitê avalia a empresa. |
| **Contrato** | Aprovado no comitê; falta fechar o contrato. |

Os **desfechos** — Habilitado, Reprovado, Recusado pelo cliente e Inerte — ficam na faixa
recolhível no pé da página, com o motivo ao lado. Aparecem por 90 dias.

Cards marcados **legado** vieram da esteira antiga já aprovados, antes de existir a decisão
com limite. Revise com o time o que faltar.

---

## O dia a dia

**Pegar uma análise.** Abra o card em Nova e clique em **Assumir análise** (ou arraste para
Em análise). Se outra pessoa pegar no mesmo instante, o sistema avisa quem ficou com ele —
ninguém trabalha em dobro sem saber.

**Andar com o card.** No topo do card, a trilha mostra por onde ele passou e quando. Logo
abaixo, os botões mostram só o que vale naquele estágio: o próximo passo em destaque, e ao
lado os retrocessos (devolver, contraproposta, comitê devolveu) e as saídas. **Reprovar** e
**Mover para inerte** pedem o motivo de uma lista fechada; se escolher "Outro", descreva.

**Dados comerciais.** Aba *Dados comerciais* do card: faturamento, ticket médio, prazos,
região, produtos e parceiros. Edite e clique em **Salvar**; a alteração fica no histórico.

**Cobrar o cliente.** Coluna *Histórico*, à direita do card (no celular, aba *Histórico*):
escreva o que aconteceu e escolha o canal, e-mail ou WhatsApp. Registrar a cobrança **zera o
contador de silêncio**. Se for recado interno, deixe em "nota interna" — não conta como
contato com o cliente.

**Mover o card.** Arraste entre colunas — funciona também no toque, segurando o card um
instante. A coluna que não aceita fica apagada — é a regra do fluxo, não um travamento. Pelo
teclado: Tab até o card, Espaço pega e solta; Enter abre.

---

## As cores

| Cor da borda | Quer dizer |
|---|---|
| cinza | dentro do prazo |
| **âmbar** | perto de estourar (a partir de 70% do prazo) |
| **vermelho** | prazo estourado |

Prazos da análise: **12 horas úteis** para alguém pegar e **24 horas úteis** para decidir.
Fim de semana e feriado nacional não contam.

**Aguardando aceite e Documentação não têm prazo** — quem responde é o cliente. O que corre
ali é o silêncio, em dias corridos: **30 dias** sem retorno deixa o card âmbar, **45 dias**
marca para inerte. O sistema **sugere**; quem decide é o analista.

---

## O checklist

Nasce junto com o card — a equipe pode anexar desde o primeiro dia —, em dois blocos: **documentos da empresa** e um bloco
**por sócio**, montado a partir do quadro societário. Sócio pessoa jurídica não gera bloco.

Quando a participação no capital é conhecida, ela aparece ao lado do nome, e quem está **abaixo
de 15%** já nasce fora do checklist, com o motivo. Na maioria das empresas o percentual não é
conhecido — a Receita não publica esse dado —, então o bloco nasce ativo e vocês tiram quem não
interessa. Se o quadro societário ainda não estava carregado quando o card nasceu, o bloco de
sócios é completado quando o card entra em Documentação.

**Avalista** entra pelo botão *adicionar avalista*, no fim do checklist. Recebe os mesmos itens
do sócio. Ele não vem automático porque não consta do quadro societário.

| Status | Quer dizer |
|---|---|
| Pendente | ainda não chegou |
| Recebido, a conferir | o cliente mandou, ninguém olhou |
| Validado | alguém abriu, conferiu e aceitou |
| Rejeitado | errado, vencido ou ilegível — volta a ser cobrado |
| Não se aplica | automático, como a certidão da JUCESP para empresa fora de São Paulo |
| Liberado com justificativa | a casa abriu mão do item, com o porquê registrado |

Só **Validado**, **Não se aplica** e **Liberado com justificativa** liberam a passagem de
Documentação para o Comitê. Se tentar enviar com pendência, o sistema diz exatamente quais
documentos faltam.

Três itens são obrigatórios mas admitem exceção: **endividamento**, **curva ABC** e
**autorização SCR**. O cliente pode legitimamente não ter os dois primeiros, ou não aceitar
assinar o terceiro. Neles a ação se chama **Liberar na exceção** e exige escrever o motivo.

**Anexar** sobe o arquivo para a pasta da empresa no compartilhamento de rede, a mesma
pasta \`CLIENTES\` de sempre. Reenviar cria uma versão nova sem apagar a anterior. Remover
tira da tela, mas **o arquivo continua no compartilhamento** — nada é apagado de lá pelo
portal.

O campo de **observação** ao lado de cada item é onde vai o dado real: "CRC válido",
"7 clientes", "válida até 2032".

**Sócio que saiu da sociedade ou faleceu:** use *tirar do checklist*, com o motivo. Os
documentos dele param de travar o fechamento e o histórico continua visível.

---

## Achar, ordenar, exportar

**Buscar** — a barra no topo procura por razão social ou CNPJ, com ou sem pontuação.

**Filtrar** — por responsável, por tipo de entrada (manual ou visão cedente) e o botão
**só atrasados**.

**Ordenar** — o ícone no cabeçalho de cada coluna alterna entre urgência (mais parado
primeiro, que é o padrão), nome A→Z e nome Z→A. Ele fica colorido quando sai do padrão.

**Exportar** — botão **Exportar**, três arquivos que abrem no Excel e respeitam o filtro
atual: os cards, o checklist item a item e o histórico de cobranças.

---

## Casos que costumam gerar dúvida

**Empresa reprovada que melhorou.** Abra o card na faixa de desfechos e use **Reabrir em
Nova**. O motivo anterior fica no histórico e o card passa a mostrar "reanálise". Vale também
para Habilitado, quando for preciso rever o limite.

**Card criado por engano ou fila para limpar.** Dentro do card, **Apagar**. Para vários de
uma vez, botão **selecionar** no topo: clique nos cards (ou no ícone do cabeçalho para marcar
a coluna inteira) e **Apagar** na barra de baixo. Só entra o que está visível no filtro
atual. Apagar não tem volta: checklist e histórico somem junto, os arquivos ficam na pasta
\`CLIENTES\`. Se a empresa só não vai seguir, prefira **Reprovar** ou **Mover para inerte**,
que guardam o motivo. Card que veio da visão cedente volta a aparecer em **puxar visão
cedente** depois de apagado.

**Mesma empresa duas vezes.** Não dá: só existe um card aberto por CNPJ. Quando o card
chega a um desfecho, o CNPJ é liberado para um card novo.

**Quem pode o quê.** Todo mundo que entra no portal opera a esteira inteira. Toda ação
fica registrada no histórico com autor e data. A exceção é o catálogo de documentos, que
só o administrador altera.

**Anexo não sobe.** Se a mensagem falar em compartilhamento indisponível, a pasta de rede
não está acessível — o mesmo problema que afeta a pasta de documentos da empresa.

---

## O que vem a seguir

A esteira unificada está entrando por partes. Nas próximas:

- **decisão com limite**: ao aprovar, a analista registra limite, condições por produto e
  parecer; o aceite do cliente aceita contraproposta;
- **pendências e comentários**: pedir informação ou documento ao comercial, com aviso no sino;
- **comitê com pareceres**: cada membro dá a posição dele, como na esteira de liberação;
- **contrato**: contato de quem assina e conclusão do card.
`;
