-- Nota E, "Saída de risco" (pedido do time, 2026-10-26): a quinta letra da escala, depois da D.
-- Como no Nutri-Score, o E é o vermelho; o D passa a laranja (cores na tela, não no banco).

ALTER TABLE empresa_nota DROP CONSTRAINT empresa_nota_nota_check;
ALTER TABLE empresa_nota
    ADD CONSTRAINT empresa_nota_nota_check CHECK (nota IN ('A', 'B', 'C', 'D', 'E'));

ALTER TABLE empresa_nota_historico DROP CONSTRAINT empresa_nota_historico_nota_check;
ALTER TABLE empresa_nota_historico
    ADD CONSTRAINT empresa_nota_historico_nota_check CHECK (nota IN ('A', 'B', 'C', 'D', 'E'));
