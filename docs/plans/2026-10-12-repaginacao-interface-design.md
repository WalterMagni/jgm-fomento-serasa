# Repaginação da interface — design

Data: 2026-10-12
Status: validado com o Walter, seção a seção, em 2026-10-12
Branch: `feat/repaginacao`, criada a partir de `feat/esteira-unificada` (`59dc0d1`)
Protótipo: canvas "Repaginação Portal JGM" no claude.ai (privado do Walter), quadro "✓ Escolhida"

## Por que existe

As esteiras de prospecção e de liberação ganharam uma linguagem visual própria: cor que
carrega significado, número importante em destaque, entrada animada dos cards. O resto do
portal ficou para trás. As telas seguem as cores da marca, mas cada uma reinventa
cabeçalho, filtro, tabela e estado vazio, e o conjunto parece um template genérico.

Dois casos concretos puxaram a repaginação:

- **Pessoas Físicas** é uma tabela simples com paginação ‹ ›. Em produção são centenas de
  CPFs, sem filtro por situação, sem ordenação, sem resumo. A analista rola às cegas.
- **Informações Comerciais** é uma tabela de 16 colunas com rolagem horizontal, mistura de
  fonte serifada e um azul-marinho que não é da marca.

Os ícones (Lucide, traço 2px) também contribuem para a cara genérica: é o set mais usado
do mercado.

## Direção escolhida

Três direções foram prototipadas com 412 CPFs fictícios. A escolhida mistura duas:

- **Casca da direção "Esteira"**: sidebar bordô em degradê com brilho laranja, itens
  agrupados por seção, item ativo em pílula branca.
- **Página Pessoas Físicas da direção "Areia"**: cabeçalho editorial, quatro KPIs, filtros
  com contagem, lista em linhas e gaveta de detalhe.
- **Ícones Phosphor duotone**, com a camada de fundo em laranja da marca.
- **Fundo do conteúdo em tom areia** (`#F8F7F3`), não o cinza-azulado das esteiras.

**Modo escuro sai.** Ninguém do time usa. O tema fica fixo no claro.

## 1. Fundação

### Tokens

O `@theme` de `frontend/src/app/globals.css` ganha a paleta do protótipo:

| Token | Valor | Uso |
|---|---|---|
| papel | `#F8F7F3` | fundo do conteúdo |
| linha | `#E4E1D8` | bordas e divisórias |
| texto | `#262833` | texto principal (grafite) |
| texto secundário | `#69675F` | legendas |
| bordô | `#612035` | ação primária, sidebar |
| laranja | `#D1732C` | destaque, camada duotone |
| laranja-texto | `#944C17` | laranja usado como texto (o puro não passa contraste) |
| ok | `#1F7A5C` | situação limpa |
| crítico | `#B42345` | situação crítica |

Raios de 12, 18 e 20 px e três níveis de sombra. Fontes sem mudança (Clash Display nos
títulos, Hanken Grotesk no corpo). Entra a JetBrains Mono para CPF e CNPJ.

### Modo escuro

O botão de tema sai do topo e a classe `dark` nunca mais é aplicada. As classes `dark:`
espalhadas pelo código ficam inertes e somem quando cada tela for refeita. Não há faxina
geral de uma vez.

### Ícones

Pacote oficial `@phosphor-icons/react`. Só `components/ui/Icon.tsx` muda: os nomes
semânticos atuais (`person_search`, `account_balance`, …) continuam valendo, então nenhuma
tela precisa ser tocada. Peso `duotone` por padrão. A camada de fundo é pintada por
variável CSS: laranja por padrão, cor própria (`currentColor`) nos ícones de situação.

### Animações

Só CSS, sem biblioteca nova. Entradas (subir, cair), barras que crescem, contador, gaveta
que desliza e o brilho da sidebar ficam em `globals.css`. Entrada escalonada por
`animation-delay` em cada item. `prefers-reduced-motion` desliga tudo.

### Primitivos

Em `components/ui/`, cada um criado quando a primeira tela precisar dele:
`PageHeader`, `KpiCard`, `FilterBar` (busca + segmentos com contagem + ordenação),
`DataList` (linhas em grid, paginação, estado vazio), `Drawer`, `Chip`/`Pill`,
`EmptyState`.

## 2. Casca

### Sidebar

Componente único `Sidebar`, usado no desktop e na gaveta do tablet. Grupos:

- **Carteira**: Gestão de Carteira, Informações Comerciais, Pessoas Físicas, Visão Cedente
- **Operação**: Praça de Pagamento (subitens Inconclusivos, Padrões e Histórico aparecem
  quando se está na Praça, como hoje)
- **Esteiras**: Prospecção e Liberação, com selo beta

Contadores só os que a API já fornece (atrasados da prospecção, pendências da liberação),
em laranja. No rodapé, cartão com o nome do usuário, engrenagem de Configurações e Sair; o
menu de usuário sai do topo. `NavItems.tsx` continua sendo a fonte única dos itens e ganha
o campo `grupo`.

### Topo

Caminho da página ("Carteira / Pessoas Físicas") e o sino de notificações atual, com
visual novo. O botão de tema sai.

### Transição entre páginas

Leve fade-up do conteúdo a cada troca de rota, em CSS.

### Interruptor "Novo visual"

`layout.tsx` se divide em `CascaAntiga` e `CascaNova`. Configurações ganha o interruptor
"Novo visual", salvo por usuário no navegador e desligado por padrão. As páginas não mudam
com ele: só a moldura em volta. Aprovada a casca nova, a antiga é apagada num commit só.

### Fica para depois

Busca global ⌘K (empresa, CNPJ ou CPF de qualquer tela). É funcionalidade nova, não
visual. Entra como item próprio depois da casca.

## 3. Pessoas Físicas

### Dados

Novo endpoint `GET /person/profiles/resumo`. Devolve a **última análise de cada CPF**, já
resumida:

- nome, CPF, cidade/UF, data da consulta
- por tipo de restrição (Pefin, Refin, protesto, cheque, ações judiciais, falência):
  quantidade e valor
- total de ocorrências e dívida total
- empresas da carteira ligadas à pessoa (CNPJ e razão social)

O resumo é montado em Java a partir do JSONB que já existe em `person_analysis`: **sem
migration**. As razões sociais vêm de uma consulta única para todos os CNPJs, sem N+1. O
endpoint `/person/profiles` atual continua: a página da empresa depende dele.

O frontend carrega o resumo uma vez e filtra, ordena, pagina e calcula os KPIs na memória.
Com centenas de CPFs a resposta fica na casa de dezenas de KB e a busca responde na hora,
como na página de Informações Comerciais. **Limite conhecido:** funciona bem até alguns
milhares de CPFs. Se crescer além disso, o caminho é guardar o resumo em colunas e paginar
no servidor.

### Regras de situação

| Situação | Regra |
|---|---|
| Limpo | nenhuma ocorrência |
| Com restrição | 1 ou 2 ocorrências **e** dívida até R$ 50 mil |
| Crítico | 3 ou mais ocorrências **ou** dívida acima de R$ 50 mil |

**Consulta velha**: mais de 90 dias. A data aparece em laranja e a gaveta sugere
reconsultar antes de uma decisão de crédito.

### Tela

- Cabeçalho: rótulo "Gestão de Carteira", título, total de CPFs e quantos têm restrição;
  ações "Nova consulta PF" (abre painel inline com o custo de R$ 15,52)
- Quatro KPIs: Consultados (com mini-gráfico de 12 meses e total dos últimos 30 dias),
  Sem restrição, Com restrição, Críticos
- Busca por nome ou CPF, filtros Todos / Limpos / Com restrição / Críticos com contagem,
  ordenação (recentes, A–Z, mais restrições, maior dívida, consulta mais antiga)
- Lista paginada (15 por página): pessoa, vínculo na carteira, barra de restrições por
  tipo, dívida, data da consulta
- Gaveta de detalhe: restrições por tipo com valor, vínculos com link para a Visão 360º,
  data da consulta, "Abrir análise completa" (`/individuals/{cpf}`) e "Reconsultar"
- Filtros na URL (`?situacao=critico&ordem=divida&q=…`): voltar do detalhe mantém a lista

**Fora**: botão Exportar do protótipo. Entra se alguém pedir.

## 4. Ordem, erros e testes

### Etapas

Cada etapa pode ir para produção sozinha e ser revertida sozinha.

1. **Fundação**: tokens, Phosphor no `Icon.tsx`, animações, tema fixo no claro
2. **Pessoas Físicas**: endpoint `resumo`, primitivos, página nova
3. **Casca nova** atrás do interruptor
4. **Informações Comerciais**, com brainstorming próprio antes (as 16 colunas pedem outra
   solução)
5. **Demais telas**, uma por commit: Carteira, Visão 360º, Praça…
6. **Limpeza e extras**: apagar a casca antiga após aprovação; busca ⌘K

### Erros e carregamento

- Carregando: linhas esqueleto com brilho, no lugar do ícone girando
- Falha da API: estado de erro com "Tentar de novo", sem quebrar o resto da tela
- Reconsulta Serasa: a mensagem crua da API externa nunca chega à tela (mesma regra do
  CNPJ Já); o erro técnico vai para o log

### Testes

- Backend: teste unitário do resumo, cobrindo regra de situação, somas por tipo, última
  análise por CPF e vínculos só com empresas da carteira
- Frontend: `build` e `lint` limpos; Playwright de fumaça em `/individuals` (filtrar,
  ordenar, URL mantém estado, gaveta abre e fecha)
- Visual: print antes e depois de cada etapa
