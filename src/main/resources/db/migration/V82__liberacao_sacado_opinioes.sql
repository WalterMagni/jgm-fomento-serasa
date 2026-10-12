-- Três quadrados por sacado (pedido do time em 2026-10-12): O = opinião da Origem, C = opinião do
-- Comitê, D = decisão da Diretoria. D já existe (situacao, valor_aprovado...) e continua sendo a
-- que calcula o resultado do card; O e C são registro de quem opinou e quando.
ALTER TABLE liberacao_sacado ADD COLUMN opiniao_origem VARCHAR(12);
ALTER TABLE liberacao_sacado ADD COLUMN opiniao_origem_por_nome VARCHAR(200);
ALTER TABLE liberacao_sacado ADD COLUMN opiniao_origem_em TIMESTAMP;
ALTER TABLE liberacao_sacado ADD COLUMN opiniao_comite VARCHAR(12);
ALTER TABLE liberacao_sacado ADD COLUMN opiniao_comite_por_nome VARCHAR(200);
ALTER TABLE liberacao_sacado ADD COLUMN opiniao_comite_em TIMESTAMP;
