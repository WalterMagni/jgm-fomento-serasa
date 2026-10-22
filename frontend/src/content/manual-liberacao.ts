/**
 * Manual de uso da esteira de liberação, em markdown.
 *
 * <p>Fonte única do texto: é ela que o time lê pelo botão Ajuda da tela. O design técnico fica
 * em docs/plans/2026-10-07-esteira-liberacao-design.md; aqui é o que muda para quem usa.</p>
 */
export const MANUAL_LIBERACAO = `# Esteira de liberação — manual de uso

Para quem abre, analisa e decide a liberação de operações: auxiliares, analistas do Comitê e
Diretoria.

A esteira substitui a planilha de liberação. Cada operação vira um card, e o card anda da
**Origem da análise** até **Finalizados**, passando pelo Comitê, pela decisão e, se aprovado,
pela assinatura do contrato. Tudo o que acontece com ele fica registrado com
nome, data e hora.

A tela está marcada como **beta**: o fluxo ainda pode mudar conforme o time usar.

---

## As sete colunas

| Coluna | O que significa |
|---|---|
| **Origem da análise** | A auxiliar abriu o card e está preenchendo. |
| **Comitê** | Esperando o parecer do Comitê. |
| **Pendência** | Quem é analista pediu algo a alguém antes de decidir. |
| **Aprovado** | A analista aprovou, inteiro ou em parte. Próximo passo: **Enviar para assinatura**. |
| **Reprovado** | A analista reprovou. Próximo passo: **Finalizar**. |
| **Assinatura** | Aguardando a assinatura do contrato. Assinado, **Finalizar**. |
| **Finalizados** | Fim de linha, com o resultado: **Aprovado**, **Reprovado**, **Parcialmente aprovado** ou **Declinado** (o cliente desistiu). Fica até alguém **arquivar**. |

No topo de cada coluna aparecem quantos cards ela tem e a soma dos valores. Com sete colunas,
o quadro rola para o lado (no trackpad, ou com Shift + roda do mouse).

---

## Quem pode o quê

| Ação | Na Origem | Do Comitê em diante |
|---|---|---|
| Criar card | todos | — |
| Editar campos, etiquetas e cor | todos | só analista |
| Apagar | todos | só analista |
| Mover | todos (para o Comitê) | só analista |
| Comentar e marcar pessoas | todos | todos |
| Responder pendência | — | quem recebeu a pendência |

Quem é analista e quem é do Comitê é definido pelo admin em **Configurações → Gerenciamento
de usuários**. Quando um botão está cinza, passe o mouse em cima: ele diz o motivo.

---

## Abrir um card

Botão **Nova análise** (ou o **+** na coluna Origem).

1. **Cedente**: digite o nome ou o CNPJ. Se a empresa já está no portal, escolha na lista.
   Se não está, digite o CNPJ completo e informe a razão social.
2. **Tipo de operação**, **valor**, **taxa** (em %), **TAC** (em R$), **tranche** (em R$) e
   **prazo para decisão** — todos opcionais. Se o
   tipo que você precisa não está na lista, use **+ Novo tipo**: ele passa a aparecer para
   todo mundo. O prazo pode ser digitado (dd/mm/aaaa hh:mm) ou escolhido no calendário.
   **Empresa** (**SEC** ou **FIDC**) também é opcional: marcada, aparece como selo no canto
   do card e dá para filtrar o quadro por ela.
3. **Sacados**: um CNPJ ou CPF por linha, com valor opcional. **Dá para colar vários de
   uma vez** — copie a lista do e-mail ou da planilha e cole na primeira linha.
4. **Parecer da origem**: a posição (**Favorável**, **Com ressalvas** ou **Desfavorável**) e o
   que a análise encontrou.

Ao terminar de digitar o CNPJ do sacado, aparecem o nome e a praça (cidade/UF), como no
cedente. Se o sacado já foi **reprovado** ou **parcialmente aprovado** em outro card, aparece
um aviso com o número do card — clique para abrir. Sacado que não está no portal pode ser
cadastrado ali mesmo, pelo CNPJ Já.

Se a soma dos sacados não bater com o valor da operação, a tela avisa em amarelo.

### Importar a proposta (PDF da AR)

No topo do formulário, **Importar proposta (PDF da AR)**: escolha ou arraste o PDF da Análise
de Risco gerado pelo sistema de operações. O portal preenche:

- **Cedente**, pelo código do cliente. Se o código ainda não estiver vinculado a uma empresa
  do portal, a tela avisa: escolha o cedente à mão e, depois, vincule o código na página da
  empresa (campo "Código do cliente") — da próxima vez ele vem sozinho.
- **Valor da operação**: a face dos títulos **liberados**.
- **Tranche**: o "a confirmar" mais o "não confirmados" da linha **TOTAL EM ABERTO**. Dá para
  corrigir à mão no formulário.
- **Sacados**: os que têm título nesta proposta, com o valor de face e a carteira de cada um
  (a vencer, vencidos, liquidados, recomprados). Sacado que aparece na AR sem título fica de
  fora, e a tela diz quem.

Confira e salve. O PDF entra sozinho nos **Anexos** do card, e o card ganha o bloco
**Análise de risco**: títulos liberados, prazo médio, face, desconto e líquido da operação; do
cedente, o limite e a carteira (a vencer, vencidos, liquidados, recomprados). Vencidos aparecem
em vermelho e recomprados em âmbar, no cedente e em cada sacado. Comprometimento e concentração
não aparecem na tela, mas estão no relatório XLSX.

Os números são uma foto do relatório: não se atualizam sozinhos. Para trocar, edite o card e
use **reimportar**. Card já decidido não reimporta — reabra no Comitê antes.

---

## Comitê

Quando o card entra no Comitê, cada membro recebe um aviso e o card mostra quem já deu
parecer (selo verde, âmbar ou vermelho) e quem falta (círculo tracejado).

Para dar seu parecer, abra o card: escolha **Favorável**, **Com ressalvas** ou
**Desfavorável** e escreva o porquê. Dá para rever o parecer enquanto o card estiver no
Comitê.

**Para sair do Comitê basta um parecer.** Se faltar o de alguém (férias, ausência), a tela
avisa de quem e pede confirmação, e o histórico registra que o card saiu sem aquele parecer.
Quem dá o último parecer recebe o aviso de que o card está liberado para decidir. A exceção é **Devolver à Origem**, para corrigir um
card mal preenchido — aí os pareceres que faltavam deixam de ser esperados, e o Comitê
começa uma rodada nova quando o card voltar.

---

## Pendência

Ao mover para **Pendência**, quem é analista diz **para quem** e **o que falta**. Dá para pedir a
várias pessoas de uma vez. A pessoa recebe um aviso, responde dentro do card, e quem
pediu é avisado.

Da Pendência, quem é analista aprova, reprova ou devolve ao Comitê.

---

## Praça de Pagamento

Se o cedente tem títulos apontados na **Praça de Pagamento** (decididos como pagos pelo
cedente), o card avisa em vermelho: no quadro aparece **praça R$ …**, e dentro do card, na
Operação, quantos títulos e o valor total — com o link para os detalhes na página da empresa.
Sacado da operação que também aparece em apontamentos ganha a etiqueta **praça de pagamento**
com os números dele. A conta é a mesma da página da empresa.

## Os quadrados O, C e D de cada sacado

Cada sacado tem três quadrados, pintados conforme a decisão: **verde** = aprovado,
**amarelo** = parcial, **vermelho** = reprovado. Quadrado tracejado ainda não foi marcado.

| Quadrado | De quem | Quem marca e quando |
|---|---|---|
| **O** | Operador | Quem é analista, antes da decisão: com o card na Origem, no Comitê ou em Pendência. |
| **C** | Comitê | Quem é analista, com o card no Comitê ou em Pendência. No parcial, pede o valor aprovado. |
| **D** | Diretoria | Preenchido sozinho quando a Diretoria decide: aprovar repete o C, reprovar reprova todos. |

Clique no quadrado para escolher; clique de novo na mesma opção (ou em **Limpar**) para
desmarcar. No mesmo menu fica o **parecer** daquele quadrado: escreva o porquê e clique em
**Salvar parecer** — dá para marcar pessoas e empresas com @, e quem for marcado recebe aviso.
Os pareceres aparecem embaixo do sacado, cada um com a letra na cor da decisão. Passando o
mouse, aparece quem marcou e quando.

**O resultado do card sai do C** quando o Comitê decide, ou **do D** quando o card passa pela
Diretoria. O **O** é o parecer dos operadores: fica registrado, mas não muda o resultado. Depois
da decisão os três aparecem só para consulta.

## Aprovar, reprovar, assinatura e Finalizados

Quem decide é a analista, movendo o card do Comitê (ou da Pendência) para **Aprovado** ou
**Reprovado** — arrastando ou pelo botão no card.

- **Aprovar** abre a decisão (D) de cada sacado: **Aprovado**, **Reprovado** ou **Parcial** (no
  parcial, com o valor aprovado). O que já foi marcado no quadrado D vem preenchido.
  Basta um sacado aprovado ou parcial; se todos forem reprovados, use **Reprovar**.
- **Reprovar** reprova todos os sacados de uma vez e pede só a observação.

O resultado do card sai das decisões dos sacados:

- todos aprovados → **Aprovado**;
- qualquer mistura ou parcial → **Parcialmente aprovado**, com o total aprovado;
- todos reprovados → **Reprovado**.

Card sem sacados aprovado fica **Aprovado** inteiro.

Depois da decisão o card segue sozinho pelos botões: do Aprovado, **Enviar para assinatura**;
da Assinatura e do Reprovado, **Finalizar**. Em qualquer uma dessas colunas dá para
**Reabrir no Comitê** — o card volta com rodada nova de pareceres, e o resultado é refeito na
nova decisão. O prazo para de correr assim que o card é decidido.

### Cliente declinou

Se o cliente desiste da operação, abra o card e use **Cliente declinou**, embaixo de "Mover
para". Vale em qualquer etapa antes de Finalizados (na Origem qualquer pessoa; depois, analista).
O motivo é opcional. O card vai direto para **Finalizados** com a marca **Declinado**; se o
crédito já tinha sido decidido, a decisão continua registrada no card. Se o cliente voltar
atrás, **Reabrir no Comitê** tira a marca.

### Arquivar e a tela Arquivo

Em Finalizados, quem é analista **arquiva** o card (botão no card) ou a coluna inteira
(**arquivar todos**, no topo da coluna). Arquivado sai do quadro e vai para a tela
**Arquivo** (botão no topo da esteira). Lá dá para filtrar pelo período em que o card terminou
(atalhos: este mês, mês passado, últimos 90 dias, este ano) e pelo desfecho — **Aprovado**,
**Parcial**, **Reprovado** ou **Declinado** — e gerar o **relatório** do que está filtrado.
Sem filtro, é o relatório geral. Clicar no card abre o detalhe; **Desarquivar** devolve ao quadro.

---

## Marcar pessoas e empresas com @

Em qualquer campo de texto do card, digite **@** e comece o nome:

- **Pessoas** do portal: a pessoa recebe um aviso e passa a acompanhar o card.
- **Empresas** da base, pelo nome ou pelo CNPJ: vira um link que abre a página da empresa em
  nova aba.

Empresa que ainda não está no portal aparece **tracejada**; clique nela para cadastrar pelo
CNPJ Já. CNPJ colado no texto sem @ também vira link.

---

## Comentários

Na seção **Atividade** do card. Todo mundo comenta, em qualquer coluna. **Ctrl + Enter**
envia. Só quem escreveu pode editar ou apagar o próprio comentário.

A Atividade abre em **Comentários**, só com a conversa. Para ver também o histórico (quem
moveu, editou, decidiu), clique em **Tudo**.

---

## Notificações

O **sino** no topo do portal mostra quantas notificações você não leu. Clique nele para ver
as últimas e ir direto ao card. Você recebe aviso quando:

- um card chega ao Comitê e seu parecer é esperado;
- alguém te marca com @;
- recebe uma pendência, ou a pendência que você pediu foi respondida;
- um card que você acompanha é comentado, aprovado, reprovado ou devolvido;
- (analistas) alguém abre um card novo.

Quem faz a ação nunca é avisado da própria ação.

No painel do sino dá para **desligar o som** e **desligar o e-mail**. O som só funciona depois
do primeiro clique na página — é regra do navegador. Com o portal em segundo plano, o número de
notificações aparece no título da aba.

**Por e-mail**, com link para o card, chegam: card novo (para analistas), card no Comitê
esperando seu parecer, Comitê completo, pendência para você ou respondida, menção e decisão.
Comentário e parecer de outra pessoa ficam só no sino.

O quadro se atualiza sozinho: quando alguém move um card, você vê na hora.

---

## Anexos

Na seção **Anexos** do card: arraste o arquivo ou clique em **Anexar arquivo**. PDF, imagem,
Word, Excel, texto ou CSV, até 10 MB. O arquivo fica guardado no próprio portal — baixe pelo
card. Quem enviou ou uma analista pode remover: some do card, mas o arquivo continua guardado.

---

## Organizar: etiquetas, cor e membros

Dentro do card, na coluna da direita:

- **Etiquetas**: marque as existentes ou digite um nome novo para criar. Etiqueta vale para a
  esteira toda.
- **Cor do card**: faixa colorida no topo do card.
- **Membros**: quem acompanha o card e recebe os avisos dele. Entram sozinhos quem criou, o
  Comitê, quem recebeu pendência, quem comentou e quem foi marcado. Use **seguir** ou
  **deixar de seguir** para você.

---

## Encontrar cards

- **Busca**: nome do cedente, CNPJ, sacado, número do card (#42) ou trecho do parecer.
- **Só os meus**: cards que você criou, acompanha ou em que dá parecer.
- **Filtros**: membro, etiqueta, tipo, prazo (vencido, hoje, próximos 7 dias), quem criou e
  período de criação.
- **Resultado**: aprovado, parcialmente aprovado, reprovado ou declinado.
- **Finalizados**: por padrão aparecem os não arquivados. **Com arquivados** traz os arquivados
  de volta para consulta; **ocultos** tira a coluna do quadro.
- **Ordenar**: prazo mais próximo, última atividade, mais recentes, mais antigos, maior valor
  ou nome do cedente.

Os filtros ficam no endereço da página: copie o link para mandar a mesma visão a uma colega.

---

## Relatório

Botão **Relatório XLSX**. A planilha leva **exatamente os cards que estão na tela**, com os
filtros aplicados, em seis abas: Cards, Sacados, Pareceres, Pendências, Histórico e
Comentários. A aba Cards traz também quanto tempo cada card passou em cada etapa e o
**desfecho** (com data, quem registrou e motivo do declínio). Para relatório de período dos
arquivados, use a tela **Arquivo**.

---

## Arrastar e atalhos

- Arraste o card para outra coluna. As colunas para onde ele não pode ir ficam apagadas e
  dizem o motivo.
- No celular, uma coluna por vez; para mover, abra o card e use **Mover para**.
- Pelo teclado: **Tab** chega ao card, **Enter** abre. **Espaço** pega o card, as setas o
  levam até a coluna e **Espaço** de novo solta.
- **Esc** fecha a janela aberta.

---

## Dúvidas comuns

**Não consigo mover para o Comitê.** Ninguém está marcado como Comitê nas Configurações.
Peça ao admin.

**O card não sai do Comitê.** Ninguém deu parecer ainda — é preciso pelo menos um.

**Não consigo aprovar.** Algum sacado está sem decisão; a tela diz qual. Se todos foram
reprovados, use **Reprovar**.

**Não consigo finalizar um card aprovado.** Aprovado passa pela **Assinatura** antes de
finalizar.

**Alguém salvou antes de mim.** Se duas pessoas editam o mesmo card ao mesmo tempo, a
segunda a salvar recebe um aviso para recarregar, em vez de apagar o que a outra fez.

**Apaguei um card sem querer.** O card some do quadro, mas fica guardado com quem apagou e
quando. Fale com o admin.
`;
