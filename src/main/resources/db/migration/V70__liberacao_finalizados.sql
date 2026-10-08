-- Aprovado e Reprovado viram uma coluna só, Finalizados (pedido do time em 2026-10-08).
--
-- A decisão passa a ser por sacado: cada um fica Aprovado, Reprovado ou Parcial (com o valor
-- aprovado). O resultado do card sai deles — todos aprovados = APROVADO, todos reprovados =
-- REPROVADO, qualquer mistura ou parcial = PARCIAL — e aparece como etiqueta automática.
ALTER TABLE liberacao_card ADD COLUMN resultado VARCHAR(12);   -- APROVADO | REPROVADO | PARCIAL

ALTER TABLE liberacao_sacado
    ADD COLUMN situacao          VARCHAR(12),     -- APROVADO | REPROVADO | PARCIAL; nulo = a decidir
    ADD COLUMN valor_aprovado    NUMERIC(15,2),   -- só no parcial
    ADD COLUMN situacao_por_nome VARCHAR(200),
    ADD COLUMN situacao_em       TIMESTAMP;

-- Cards já decididos: o sacado herda a decisão do card, que é o que ela significava até aqui.
UPDATE liberacao_sacado s
SET situacao = c.etapa, situacao_por_nome = c.atualizado_por_nome, situacao_em = c.finalizado_em
FROM liberacao_card c
WHERE s.card_id = c.id AND c.etapa IN ('APROVADO', 'REPROVADO');

UPDATE liberacao_card SET resultado = etapa, etapa = 'FINALIZADO' WHERE etapa IN ('APROVADO', 'REPROVADO');

-- Histórico: a etapa antiga vira Finalizados, e a decisão fica registrada no próprio evento.
UPDATE liberacao_evento
SET campo = 'resultado',
    valor_depois = CASE etapa_para WHEN 'APROVADO' THEN 'Aprovado' ELSE 'Reprovado' END,
    etapa_para = 'FINALIZADO'
WHERE etapa_para IN ('APROVADO', 'REPROVADO');

UPDATE liberacao_evento SET etapa_de = 'FINALIZADO' WHERE etapa_de IN ('APROVADO', 'REPROVADO');

-- "Este sacado já foi reprovado antes?" é pergunta por CNPJ, feita a cada sacado digitado.
CREATE INDEX idx_liberacao_sacado_decidido ON liberacao_sacado (cnpj) WHERE situacao IS NOT NULL;
