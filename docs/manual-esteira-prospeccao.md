# Esteira de prospecção — manual de uso

Para quem acompanha a prospecção no portal: comercial, analista de crédito e backoffice.

A esteira substitui as abas `VISÃO CEDENTE` e `DOC'S PENDENTES` da planilha
`CONTROLE DE EMPRESAS`. Cada empresa vira um card, e o card anda da análise do cedente
até a documentação completa.

A tela está marcada como **beta**: o fluxo ainda pode mudar conforme o time usar.

---

## Onde fica

Menu lateral, **Esteira de Prospecção**. O número vermelho ao lado do nome é quanto
precisa de atenção hoje — atrasado ou sem resposta do cliente.

---

## Como uma empresa entra

**Você mesmo abre.** Botão **Nova prospecção**, informe o CNPJ e a razão social. O CNPJ é
obrigatório: é por ele que a esteira conversa com a análise de crédito, com as filiais e
com a pasta de documentos no compartilhamento.

**O sistema abre sozinho.** Sempre que uma consulta ao Serasa indica perfil de cedente,
a empresa aparece em Triagem automaticamente. O card mostra "visão cedente" no rodapé.

**Você traz o que já existia.** Botão **puxar visão cedente**, aba *Escolher empresas*:
busque por razão social ou CNPJ, marque o que quiser e traga. A aba *Trazer um lote* serve
para a carga inicial — ela mostra quantas empresas entrariam antes de você confirmar e
limita o tamanho do lote, porque não há como desfazer em massa.

---

## As colunas

| Coluna | O que significa |
|---|---|
| **Triagem** | Chegou. Ninguém pegou ainda. |
| **Em análise** | Uma analista assumiu. O nome dela fica no card. |
| **Aprovado** | Passagem rápida: ao aprovar, o checklist é criado e o card segue sozinho. |
| **Documentos pendentes** | Coleta em andamento. É aqui que a maioria fica. |
| **Documentos completos** | Tudo que era obrigatório foi resolvido. |
| **Pronto p/ habilitação** | Sai da esteira. Daqui em diante é a habilitação. |

**Reprovados e removidos do radar** ficam na faixa recolhível no pé da página, com o
motivo ao lado. Aparecem por 90 dias.

---

## O dia a dia

**Pegar uma análise.** Abra o card em Triagem e clique em **Assumir análise**. Se outra
pessoa pegar no mesmo instante, o sistema avisa quem ficou com ele — ninguém trabalha em
dobro sem saber.

**Decidir.** Dentro do card, os botões mostram só para onde ele pode ir. Ao **Aprovar**, o
checklist de documentos é criado na hora. Ao **Reprovar**, o sistema pede o motivo de uma
lista fechada; se escolher "Outro", descreva.

**Cobrar o cliente.** Aba *Histórico* do card: escreva o que aconteceu e escolha o canal
(e-mail, WhatsApp, ligação, reunião). Registrar a cobrança **zera o contador de silêncio**.
Se for recado interno, deixe em "nota interna" — não conta como contato com o cliente.

**Mover o card.** Arraste entre colunas. A coluna que não aceita fica apagada — é a regra
do fluxo, não um travamento. Quem prefere não arrastar usa o botão **Mover** no card.

---

## As cores

| Cor da borda | Quer dizer |
|---|---|
| cinza | dentro do prazo |
| **âmbar** | perto de estourar (a partir de 70% do prazo) |
| **vermelho** | prazo estourado |

Prazos atuais: **1 dia útil** para alguém pegar a análise, **2 dias úteis** para decidir,
**10 dias úteis** para a documentação. São dias úteis, com feriado nacional descontado.

A faixa vermelha **"Sem resposta há mais de 30 dias"** conta desde a última cobrança
registrada. O sistema **sugere** remover do radar; quem decide é uma pessoa.

---

## O checklist

Nasce quando a análise é aprovada, em dois blocos: **documentos da empresa** e um bloco
**por sócio**, montado a partir do quadro societário. Sócio pessoa jurídica não gera bloco.

| Status | Quer dizer |
|---|---|
| Pendente | ainda não chegou |
| Recebido, a conferir | o cliente mandou, ninguém olhou |
| Validado | alguém abriu, conferiu e aceitou |
| Rejeitado | errado, vencido ou ilegível — volta a ser cobrado |
| Não se aplica | automático, como a certidão da JUCESP para empresa fora de São Paulo |
| Dispensado | decisão de abrir mão, com motivo |

Só **Validado**, **Não se aplica** e **Dispensado** liberam o avanço. Se tentar fechar com
pendência, o sistema diz exatamente quais documentos faltam.

**Anexar** sobe o arquivo para a pasta da empresa no compartilhamento de rede, a mesma
pasta `CLIENTES` de sempre. Reenviar cria uma versão nova sem apagar a anterior. Remover
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

**Empresa reprovada que melhorou.** Abra o card na faixa de reprovados e use **Reabrir em
triagem**. O motivo anterior fica no histórico e o card passa a mostrar "reanálise".

**Mesma empresa duas vezes.** Não dá: só existe um card aberto por CNPJ. Quando o card
chega a um desfecho, o CNPJ é liberado para um card novo.

**Quem pode o quê.** Todo mundo que entra no portal opera a esteira inteira. Toda ação
fica registrada no histórico com autor e data. A exceção é o catálogo de documentos, que
só o administrador altera.

**Anexo não sobe.** Se a mensagem falar em compartilhamento indisponível, a pasta de rede
não está acessível — o mesmo problema que afeta a pasta de documentos da empresa.

---

## Ainda em discussão

Dois pontos não foram confirmados com o time e podem mudar:

- **de quais sócios** exigir documento — hoje o checklist é criado para todos os sócios
  pessoa física do quadro societário;
- **quem confere** o documento recebido — hoje qualquer pessoa pode validar.

Os prazos de 1, 2 e 10 dias úteis também são uma proposta, não um número acordado.
