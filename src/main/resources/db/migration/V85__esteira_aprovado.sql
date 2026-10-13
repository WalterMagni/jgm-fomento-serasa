-- Etapa Aprovado e aceite depois do comitê (pedido do time em 2026-10-13).
-- Design em docs/plans/2026-10-13-esteira-aprovado-design.md.
--
-- Ordem nova:
--   NOVA → EM_ANALISE → APROVADO → DOCUMENTACAO → COMITE → AGUARDANDO_ACEITE → CONTRATO → HABILITADO
--
-- O estágio é texto (VARCHAR), sem lista fechada no banco: o valor novo não pede ALTER.
--
-- Card em AGUARDANDO_ACEITE hoje foi aprovado na análise e ainda não chegou à documentação. No
-- fluxo novo esse lugar é o APROVADO: o comercial junta os documentos e o aceite do cliente vem
-- depois do comitê. Estágio_desde fica como está — o relógio de silêncio não reinicia.
-- A timeline antiga continua dizendo "Aguardando aceite", que era o nome na época.

INSERT INTO prospeccao_evento (prospeccao_id, tipo, texto, usuario_nome, criado_em)
SELECT id, 'NOTA',
       'Esteira reorganizada: o card estava aguardando o aceite do cliente e passou para Aprovado. '
           || 'Agora o comercial junta os documentos, e o aceite vem depois do comitê.',
       'Sistema', now()
  FROM prospeccao
 WHERE estagio = 'AGUARDANDO_ACEITE';

UPDATE prospeccao
   SET estagio = 'APROVADO',
       updated_at = now()
 WHERE estagio = 'AGUARDANDO_ACEITE';
