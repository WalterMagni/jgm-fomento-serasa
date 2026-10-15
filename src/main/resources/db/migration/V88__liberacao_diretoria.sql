-- Diretoria como etapa e perfil na esteira de liberação (pedido do time em 2026-10-14).
-- Ver docs/plans/2026-10-14-liberacao-diretoria-design.md.
--
-- O Comitê decide cada sacado no quadrado C, ou manda o card para a Diretoria, que decide no D.
-- A situação do sacado passa a ser só a decisão final, gravada quando o card é decidido.

-- Perfil: quem decide na etapa Diretoria. Como o Comitê, exige analista.
ALTER TABLE users ADD COLUMN diretoria BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users
    ADD CONSTRAINT ck_users_diretoria_exige_analista CHECK (NOT diretoria OR analista);

-- C ganha o valor do parcial: agora é decisão, não só opinião.
ALTER TABLE liberacao_sacado ADD COLUMN valor_aprovado_comite NUMERIC(15, 2);

-- D ganha campos próprios; antes era a própria situação.
ALTER TABLE liberacao_sacado
    ADD COLUMN decisao_diretoria          VARCHAR(12),
    ADD COLUMN valor_aprovado_diretoria   NUMERIC(15, 2),
    ADD COLUMN decisao_diretoria_por_nome VARCHAR(200),
    ADD COLUMN decisao_diretoria_em       TIMESTAMP;

-- Motivo de quem mandou o card para a Diretoria.
ALTER TABLE liberacao_card
    ADD COLUMN motivo_diretoria           TEXT,
    ADD COLUMN enviado_diretoria_por_nome VARCHAR(200),
    ADD COLUMN enviado_diretoria_em       TIMESTAMP;

-- Card já decidido: o D continua mostrando a decisão que foi tomada.
UPDATE liberacao_sacado s
SET decisao_diretoria = s.situacao,
    valor_aprovado_diretoria = s.valor_aprovado,
    decisao_diretoria_por_nome = s.situacao_por_nome,
    decisao_diretoria_em = s.situacao_em
FROM liberacao_card c
WHERE s.card_id = c.id
  AND s.situacao IS NOT NULL
  AND c.etapa IN ('APROVADO', 'REPROVADO', 'ASSINATURA', 'FINALIZADO');

-- Card em aberto com D marcado: quem marcou foi o comitê, então vira C. A situação volta a vazio
-- até o card ser decidido, para não contar como decisão no histórico dos outros cards.
UPDATE liberacao_sacado s
SET opiniao_comite = COALESCE(s.opiniao_comite, s.situacao),
    valor_aprovado_comite = CASE WHEN s.opiniao_comite IS NULL THEN s.valor_aprovado ELSE NULL END,
    opiniao_comite_por_nome = COALESCE(s.opiniao_comite_por_nome, s.situacao_por_nome),
    opiniao_comite_em = COALESCE(s.opiniao_comite_em, s.situacao_em),
    situacao = NULL,
    valor_aprovado = NULL,
    situacao_por_nome = NULL,
    situacao_em = NULL
FROM liberacao_card c
WHERE s.card_id = c.id
  AND s.situacao IS NOT NULL
  AND c.etapa IN ('ORIGEM', 'COMITE', 'PENDENCIA');
