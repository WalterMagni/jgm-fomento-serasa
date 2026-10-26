# Judit — painel de processos no desenho do Vadu

Pedido do time em 2026-10-26: os dados da Judit aparecem como a aba **Processos** do Vadu, que é a
referência do dia a dia. Os dados continuam sendo os da lista do Data Lake já guardada — montar o painel
não chama a Judit nem cobra. Continua [02-implementacao.md](02-implementacao.md) e
[03-on-demand.md](03-on-demand.md).

## Onde aparece

| Lugar | O quê |
|---|---|
| Card na Visão 360º | O **Dashboard** do Vadu: cards Total, Trabalhista, Estadual e Federal (quantidade e valor), processos por área, status jurídico (polo), Estado, quantidade por ano e últimos processos. As lupas abrem a página de processos já filtrada ou o detalhe do processo |
| Página `/clients/{cnpj}/processos` | Abas **Dashboard**, **Instâncias** e **Processos**. Em Instâncias, uma aba por justiça (Estadual, Trabalhista, Federal, Eleitoral, Militar e, se houver, Superiores e Outras) e, dentro de cada uma, **Dashboard** (parte ativa, passiva, total e outros; área; por polo: total por situação, classes e assuntos; novos nos últimos 12 meses; por ano; Estado), **Processos**, **Partes** e **Seg. financeiro** |

Antes da lista (só com o resumo), o card mostra os números do resumo e os pontos de atenção, como antes.

## De onde sai cada informação

| Do Vadu | Da Judit |
|---|---|
| Justiça (Estadual, Trabalhista...) | Dígito do segmento no número CNJ (`NNNNNNN-DD.AAAA.J.TR.OOOO`: 8 estadual, 5 trabalho, 4 federal, 6 eleitoral, 7/9 militar, 1–3 superiores); sem número no padrão, `justice_description` |
| Status jurídico (ativo, passivo, outros) | O polo da empresa na lista: autora, ré; terceiro e desconhecido em "outros" |
| Situação (em tramitação, arquivamento definitivo...) | **Deduzida do último andamento** — a base histórica da Judit traz `situation` "NÃO INFORMADO". A tela avisa que é dedução |
| Segmento financeiro | Etiqueta `banking` da Judit ou o nome da outra parte (banco, financeira, FIDC, securitizadora, cooperativa de crédito, fomento, factoring, leasing) |
| Área, classe, assunto, UF, valor, datas | Os mesmos campos da lista. A área vem "não informada" em boa parte dos processos (36% na I G Labegalini) |

### Regras da situação deduzida (`JuditLeitura.situacao`)

Sobre a `situation` da Judit, quando informada, e depois o texto do último andamento, sem acento:

1. arquivamento provisório, suspensão, sobrestamento → **Suspenso**;
2. desarquivamento, reativação → **Em tramitação**;
3. arquivado, baixa definitiva, extinto, trânsito em julgado, processo findo → **Arquivado**;
4. qualquer outro andamento → **Em tramitação**; sem andamento → "Sem andamento".

Erra quando o último andamento de um processo encerrado é genérico ("juntada de petição"). A situação
oficial vem no **Detalhar** (R$ 0,35 por processo).

## Onde fica cada coisa

| | |
|---|---|
| Backend | `JuditPainel` (os blocos, sem banco, testado em `JuditPainelTest`); justiça, situação e segmento financeiro em `JuditLeitura.processo`; o painel vai em `ProcessosConsultados.painel` (no card, só o geral e sem partes) |
| Frontend | `components/judit/painel/`: `Graficos` (barras em CSS/SVG, sem biblioteca), `PainelGeral`, `PainelJustica`, `ListaProcessos` (filtros de polo, situação e área, planilha e impressão), `PartesJudit`; a página em `clients/[cnpj]/processos/page.tsx` |

O desenho foi tirado da tela do Vadu numa visita única à empresa de teste, só de leitura. A Dimensa
(dona do Vadu) foi consultada sobre a API de integração; ver a conversa de 2026-10-26.
