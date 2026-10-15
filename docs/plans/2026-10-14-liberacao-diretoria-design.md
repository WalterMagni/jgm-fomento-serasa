# Liberação: Diretoria como etapa e perfil (2026-10-14)

Pedido do time: a Origem não decide sacado; o Comitê decide ou manda para a Diretoria; a
Diretoria dá a palavra final. Hoje "Diretoria" é só o rótulo do quadrado D, marcado por qualquer
analista.

## Decisões (com o Walter, 2026-10-14)

1. **Origem não decide.** O quadrado O sai da tela. Os dados antigos ficam no banco e o parecer O
   já escrito continua aparecendo.
2. **Perfil Diretoria.** Marca `users.diretoria`, como Analista e Comitê; exige analista (mesma
   regra do Comitê). Pode acumular com Comitê.
3. **C é a decisão do Comitê** por sacado: aprovado, parcial (com valor) ou reprovado. Marca quem
   já marca hoje: analista, com o card no Comitê ou em Pendência.
4. **Comitê decide sozinho ou manda para a Diretoria.** Do Comitê (ou da Pendência) o card vai
   para Aprovado, Reprovado ou para a etapa nova **Diretoria**. Ir para a Diretoria pede motivo
   (qualquer caso, não só ressalva) e segue a regra de sair do Comitê: pelo menos um parecer.
   Aprovar com ressalva sem passar pela Diretoria continua valendo.
5. **Diretoria dá sim ou não à proposta do Comitê.** Não devolve, não põe ressalva nem observação
   e não mexe sacado a sacado (ajuste com o Walter no mesmo dia): aprovar mantém o C de cada sacado,
   reprovar reprova todos. O D é preenchido sozinho na decisão e fica só de leitura. Para mandar
   para a Diretoria, todo sacado precisa do C, e não todos reprovados. Só quem tem o perfil move o
   card na Diretoria.
6. **Um parecer basta.** Já era assim; agora fica visível: card e cabeçalho mostram "1 de 2
   pareceres" e de quem foi.
7. **Cards antigos ficam como estão.** Card já decidido: o D mostra a decisão de hoje. Card em
   aberto com D marcado: o D vira C (foi o comitê que marcou).

## Modelo

- `situacao`/`valor_aprovado` do sacado = **decisão final**, gravada só quando o card é decidido:
  vem do C se o Comitê decidiu, do D se a Diretoria decidiu. O histórico de decisões anteriores
  lê só decisões finais.
- C: `opiniao_comite` + `valor_aprovado_comite` (novo).
- D: `decisao_diretoria`, `valor_aprovado_diretoria`, `decisao_diretoria_por_nome`,
  `decisao_diretoria_em` (novos). Gravado na decisão da Diretoria: cópia do C no sim, reprovado no
  não, com o nome de quem decidiu.
- Card: `motivo_diretoria`, `enviado_diretoria_por_nome`, `enviado_diretoria_em`.
- Reabrir no Comitê limpa o D e o motivo (o histórico de eventos guarda).

## Transições

```
Origem ──► Comitê ──┬──► Aprovado / Reprovado          (vale o C)
          Pendência ┘└─► Diretoria ──► Aprovado / Reprovado   (vale o D)
```

Ao chegar na Diretoria, todos com o perfil são avisados e passam a acompanhar o card.

## O de volta como parecer dos operadores (2026-10-15)

O time pediu o quadrado O de volta, agora como **parecer dos operadores**: qualquer analista marca
(aprovado, parcial ou reprovado, sem valor) e escreve o parecer, antes da decisão — com o card na
Origem, no Comitê ou em Pendência. Não entra no resultado: C e D continuam decidindo. Sem
migração: usa `opiniao_origem*` e `parecer_origem`, que ficaram no banco. Na tela e no XLSX o
nome passa a ser "Operador".
