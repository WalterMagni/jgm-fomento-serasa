-- Taxa (%) e TAC (R$) da operação no card da esteira de liberação. Opcionais, como o valor.
ALTER TABLE liberacao_card ADD COLUMN taxa NUMERIC(9, 4);
ALTER TABLE liberacao_card ADD COLUMN tac NUMERIC(15, 2);
