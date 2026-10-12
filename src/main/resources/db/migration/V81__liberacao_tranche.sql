-- Tranche do card da esteira de liberação: o em aberto não confirmado do cedente — na AR, "a
-- confirmar" + "não confirmados" da linha TOTAL EM ABERTO. Vem do PDF, mas é editável.
ALTER TABLE liberacao_card ADD COLUMN tranche NUMERIC(15, 2);
