# Esteira de prospecção: etapa Aprovado e aceite depois do comitê

Pedido do Walter em 2026-10-13. Reorganiza a esteira unificada
([design de 2026-10-11](2026-10-11-esteira-unificada-design.md)).

## Fluxo

Antes:

    NOVA → EM_ANALISE → AGUARDANDO_ACEITE → DOCUMENTACAO → COMITE → CONTRATO → HABILITADO

Agora:

    NOVA → EM_ANALISE → APROVADO → DOCUMENTACAO → COMITE → AGUARDANDO_ACEITE → CONTRATO → HABILITADO

Saídas continuam: REPROVADO, RECUSADO_CLIENTE, INERTE.

- **APROVADO** (novo): a analista aprovou com limite e condições. O comercial é avisado e junta os
  documentos do cliente. Corre o relógio de silêncio, como antes na documentação.
- **DOCUMENTACAO**: a analista confere os documentos e envia ao comitê.
- **AGUARDANDO_ACEITE**: mudou de lugar. Vem depois do comitê aprovar; o cliente responde à
  proposta aprovada.

## Decisões

| Ponto | Decisão |
|---|---|
| O que leva de Aprovado para Documentação | Automático quando nenhum documento obrigatório (de sócio ativo) está pendente ou rejeitado: tudo anexado, dispensado ou não aplicável. A conferência (validar) é da analista, já em Documentação. A troca manual continua possível. |
| Limite e condições | A análise propõe (o comercial já vê no Aprovado). O comitê aprova mantendo ou ajustando; ajustar grava uma decisão nova, vigente. O cliente responde ao que o comitê aprovou. |
| Contraproposta do cliente | Volta ao comitê numa rodada nova. Documentos já conferidos não se repetem. |
| Cards em AGUARDANDO_ACEITE no deploy | Viram APROVADO (V85), com nota na timeline. O histórico antigo continua dizendo "Aguardando aceite", que era o nome na época. |

## Transições

| De | Para |
|---|---|
| NOVA | EM_ANALISE, INERTE |
| EM_ANALISE | APROVADO e REPROVADO (pela decisão), NOVA, INERTE |
| APROVADO | DOCUMENTACAO, EM_ANALISE, INERTE |
| DOCUMENTACAO | COMITE (pelo envio), APROVADO (devolver ao comercial), EM_ANALISE, INERTE |
| COMITE | AGUARDANDO_ACEITE, EM_ANALISE e REPROVADO (pelo resultado) |
| AGUARDANDO_ACEITE | CONTRATO, RECUSADO_CLIENTE e COMITE (pela resposta do cliente), INERTE |
| CONTRATO | HABILITADO (pela conclusão), COMITE |

## Avisos

- Aprovado na análise: comercial do card.
- Documentos completos (card seguiu sozinho para Documentação): analista dona.
- Aprovado no comitê: analista, membros e, agora, o comercial (é hora de colher o aceite).
