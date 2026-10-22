-- Cliente declinou a operação (pedido de 2026-10-22): de qualquer etapa em aberto o card vai
-- direto para Finalizados com a marca de declínio. O resultado do crédito, se já havia, fica: um
-- aprovado que o cliente recusou continua aprovado, e o desfecho é "declinado".
ALTER TABLE liberacao_card ADD COLUMN declinado_em TIMESTAMP;
ALTER TABLE liberacao_card ADD COLUMN declinado_por_nome VARCHAR(200);
ALTER TABLE liberacao_card ADD COLUMN motivo_declinio TEXT;
