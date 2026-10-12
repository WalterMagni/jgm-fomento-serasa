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

**Decidir.** Em análise, quem assumiu o card clica em **Decidir** — só ela (ou o admin) pode.
Para **aprovar**: limite, condições por produto (duplicata, cheque próprio…, quantas linhas
quiser) e parecer. Se a soma das condições passar do limite, o sistema avisa mas deixa seguir.
Para **reprovar**: motivo da lista fechada e, se quiser, parecer. Arrastar o card para
*Aguardando aceite* abre a mesma janela — a aprovação não anda sem limite e condições.

**Resposta do cliente.** Em Aguardando aceite, **Registrar resposta do cliente**:

| Resposta | O que acontece |
|---|---|
| **Aceitou** | O card segue para Documentação. |
| **Contraproposta** | Volta para Em análise com o pedido do cliente (mensagem, limite e produtos que ele quer). A analista decide de novo: a janela já vem com a proposta anterior. |
| **Recusou** | O card encerra em *Recusado pelo cliente*. |

Cada decisão fica guardada: a nova não apaga a anterior. A aba **Decisão e aceite** mostra a
proposta vigente e o histórico da negociação. Enquanto não há acesso do comercial ao portal,
quem registra a resposta é a equipe; o histórico marca "pelo comercial".

**Dados comerciais.** Aba *Dados comerciais* do card: faturamento, ticket médio, prazos,
região, produtos e parceiros. Edite e clique em **Salvar**; a alteração fica no histórico.

**Pedir algo ao comercial — pendência.** A dona do card clica em **Abrir pendência**: escreve o
que precisa vir, e pode marcar quais dados comerciais e quais documentos do checklist faltam.
Enquanto houver pendência aberta, o card mostra **aguardando comercial** e **o prazo da análise
para** — quem deve a resposta é o cliente. Quando o comercial responder, qualquer pessoa da
equipe clica em **Responder** na pendência (no Resumo do card) e registra o que chegou; a dona
recebe aviso no sino. Fechada a última pendência, o relógio volta a correr. Os campos pedidos
aparecem com **\*** na aba *Dados comerciais*.

**Conversar sobre o card.** Coluna *Atividade*, à direita do card (no celular, aba
*Atividade*). Em **Comentário**, escreva e use **@** para marcar alguém — a pessoa recebe aviso no
sino e por e-mail. A dona do card e quem já comentou recebem aviso de comentário novo. Só o autor
edita ou apaga o próprio comentário.

**Cobrar o cliente.** Na mesma coluna, escolha **E-mail** ou **WhatsApp**, escreva o que foi
cobrado e registre. Registrar a cobrança **zera o contador de silêncio**.

**Comitê.** Com a documentação completa, a dona do card clica em **Enviar ao comitê** (ou
arrasta o card para a coluna). Todos os usuários marcados como *Comitê* em Configurações são
convocados numa **rodada**, e cada um recebe aviso. Quem foi convocado abre o card, aba
**Comitê**, escolhe **Favorável**, **Com ressalvas** ou **Desfavorável** e, se quiser, comenta —
dá para rever o próprio parecer enquanto o card estiver no comitê. Com ao menos um parecer, a
dona ou um membro do comitê clica em **Registrar resultado**:

| Resultado | O que acontece |
|---|---|
| **Aprovar** | O card segue para Contrato. |
| **Devolver** | Volta para Em análise; a analista decide de novo e o card refaz o caminho. |
| **Reprovar** | Encerra, com motivo da lista. |

Quem não deu parecer fica dito no histórico. Voltar do Contrato para o Comitê abre rodada nova.

**Contrato.** Na aba **Contrato**, preencha nome, telefone e e-mail de quem assina pelo cliente e
salve. Com os três preenchidos, a dona clica em **Concluir: habilitado** — o card sai da esteira
e as operações do cedente seguem na esteira de liberação.

**Avisos.** O sino avisa a dona do card quando uma pendência é respondida e quando o cliente
responde à proposta; os membros do comitê são avisados quando o card chega para parecer, e a dona
quando o comitê completa a rodada e quando sai o resultado; as analistas recebem aviso de prospecção aberta à mão (as que entram pela
visão cedente não avisam, porque chegam em lote). O e-mail segue a mesma preferência da esteira de
liberação, em *Configurações*.

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

**Anexar**: arraste o arquivo para o item, ou clique na área tracejada. Reenviar cria uma versão
nova sem apagar a anterior — a mais nova aparece primeiro. O lugar dos arquivos da esteira é o
próprio card: em cada arquivo, **abrir** (PDF e imagem abrem numa aba; Word e Excel baixam),
**baixar** e **remover**. Remover tira da tela, mas o arquivo não é apagado do servidor.

**Baixar tudo (.zip)**, no topo da aba Documentos, junta num arquivo só a versão mais recente de
cada documento, já com o nome do documento ("Contrato social.pdf"), numa pasta para a empresa e uma
para cada sócio ou avalista. Se algum arquivo não for encontrado no servidor, o ZIP traz um
LEIA-ME dizendo qual.

O campo de **observação** ao lado de cada item é onde vai o dado real: "CRC válido",
"7 clientes", "válida até 2032".

**Sócio que saiu da sociedade ou faleceu:** use *tirar do checklist*, com o motivo. Os
documentos dele param de travar o fechamento e o histórico continua visível.

---

## Achar, ordenar, exportar

**Buscar** — a barra no topo procura por razão social ou CNPJ, com ou sem pontuação.

**Filtrar** — por responsável, por tipo de entrada (manual ou visão cedente) e os botões
**só atrasados**, **só minhas** (cards em que você é a analista dona) e **aguardando comercial**
(cards com pendência aberta). A faixa no topo avisa quantos **pareceres seus** o comitê espera —
clique para abrir o primeiro — e quantos cards aguardam o comercial.

**Ordenar** — o ícone no cabeçalho de cada coluna alterna entre urgência (mais parado
primeiro, que é o padrão), nome A→Z e nome Z→A. Ele fica colorido quando sai do padrão.

**Exportar** — botão **Exportar**, três arquivos que abrem no Excel e respeitam o filtro
atual: os cards (com decisão, limite, condições, resposta do cliente, pendências, comitê e contato
do contrato), o checklist item a item e o histórico de cobranças.

**Produtos** — o botão **Produtos** no topo mostra o catálogo usado nas condições da decisão
(duplicata, cheque próprio…). Só o administrador adiciona, renomeia ou desativa; produto
desativado some da escolha, mas decisões antigas continuam mostrando o nome.

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

**Quem pode o quê.** Todo mundo que entra no portal abre card, anexa documento, edita dados
comerciais, comenta, responde pendência e registra a resposta do cliente. Algumas ações são da
**analista dona do card** (quem assumiu) ou do administrador: decidir, abrir pendência, enviar ao
comitê e concluir o contrato. O **parecer** é de quem foi convocado ao comitê, e o **resultado do
comitê** é da dona ou de um membro do comitê. Os catálogos (documentos e produtos) só o
administrador altera. Toda ação fica registrada no histórico com autor e data.

**Anexo não sobe.** Se a mensagem falar em compartilhamento indisponível, a pasta de rede
não está acessível — o mesmo problema que afeta a pasta de documentos da empresa.
`;
