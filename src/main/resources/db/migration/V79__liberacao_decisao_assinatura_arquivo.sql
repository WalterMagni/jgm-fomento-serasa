-- Colunas da esteira de liberação de volta a sete (pedido do time em 2026-10-12): Origem, Comitê,
-- Pendência, Aprovado, Reprovado, Assinatura e Finalizados. A etapa é texto (VARCHAR 12) sem
-- restrição, então as novas cabem sem mudar a coluna. Os cards que já estão em Finalizados ficam
-- lá, com o resultado que têm.
--
-- Finalizado sai do quadro quando alguém arquiva, no lugar do corte automático de 30 dias.
ALTER TABLE liberacao_card ADD COLUMN arquivado_em TIMESTAMP;
ALTER TABLE liberacao_card ADD COLUMN arquivado_por_nome VARCHAR(200);

CREATE INDEX idx_liberacao_card_quadro ON liberacao_card (arquivado_em) WHERE excluido_em IS NULL;
