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
**Origem da análise** até **Aprovado** ou **Reprovado**. Tudo o que acontece com ele fica
registrado com nome, data e hora.

A tela está marcada como **beta**: o fluxo ainda pode mudar conforme o time usar.

---

## As cinco colunas

| Coluna | O que significa |
|---|---|
| **Origem da análise** | A auxiliar abriu o card e está preenchendo. |
| **Comitê** | Esperando o parecer de cada membro do Comitê. |
| **Pendência** | Quem é analista pediu algo a alguém antes de decidir. |
| **Aprovado** | Operação liberada. |
| **Reprovado** | Operação recusada. |

No topo de cada coluna aparecem quantos cards ela tem e a soma dos valores.

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
2. **Tipo de operação**, **valor** e **prazo para decisão** — os três são opcionais. Se o
   tipo que você precisa não está na lista, use **+ Novo tipo**: ele passa a aparecer para
   todo mundo. O prazo pode ser digitado (dd/mm/aaaa hh:mm) ou escolhido no calendário.
3. **Sacados**: um CNPJ ou CPF por linha, com valor opcional. **Dá para colar vários de
   uma vez** — copie a lista do e-mail ou da planilha e cole na primeira linha.
4. **Parecer da origem**: o que a análise encontrou e o que recomenda.

Se a soma dos sacados não bater com o valor da operação, a tela avisa em amarelo.

---

## Comitê

Quando o card entra no Comitê, cada membro recebe um aviso e o card mostra quem já deu
parecer (selo verde, âmbar ou vermelho) e quem falta (círculo tracejado).

Para dar seu parecer, abra o card: escolha **Favorável**, **Com ressalvas** ou
**Desfavorável** e escreva o porquê. Dá para rever o parecer enquanto o card estiver no
Comitê.

**O card só sai do Comitê depois que todos deram parecer.** Quem dá o último parecer recebe o
aviso de que o card está liberado para decidir. A exceção é **Devolver à Origem**, para corrigir um
card mal preenchido — aí os pareceres que faltavam deixam de ser esperados, e o Comitê
começa uma rodada nova quando o card voltar.

---

## Pendência

Ao mover para **Pendência**, quem é analista diz **para quem** e **o que falta**. Dá para pedir a
várias pessoas de uma vez. A pessoa recebe um aviso, responde dentro do card, e quem
pediu é avisado.

Da Pendência, quem é analista aprova, reprova ou devolve ao Comitê. Aprovar com pendência ainda
sem resposta é possível, mas a tela pede confirmação.

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

A Atividade junta comentários e histórico. O botão **Comentários** mostra só a conversa.

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

No painel do sino dá para **desligar o som** e **ativar avisos na área de trabalho**, que
aparecem mesmo com o navegador minimizado. O som só funciona depois do primeiro clique na
página — é regra do navegador.

O quadro se atualiza sozinho: quando alguém move um card, você vê na hora.

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
- **Finalizados**: por padrão aparecem os aprovados e reprovados dos últimos 30 dias. Dá para
  ver 90 dias, o último ano ou **ocultar** as duas colunas.
- **Ordenar**: prazo mais próximo, última atividade, mais recentes, mais antigos, maior valor
  ou nome do cedente.

Os filtros ficam no endereço da página: copie o link para mandar a mesma visão a uma colega.

---

## Relatório

Botão **Relatório XLSX**. A planilha leva **exatamente os cards que estão na tela**, com os
filtros aplicados, em seis abas: Cards, Sacados, Pareceres, Pendências, Histórico e
Comentários. A aba Cards traz também quanto tempo cada card passou em cada etapa.

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

**O card não sai do Comitê.** Falta parecer de alguém — o card mostra de quem.

**Alguém salvou antes de mim.** Se duas pessoas editam o mesmo card ao mesmo tempo, a
segunda a salvar recebe um aviso para recarregar, em vez de apagar o que a outra fez.

**Apaguei um card sem querer.** O card some do quadro, mas fica guardado com quem apagou e
quando. Fale com o admin.
`;
