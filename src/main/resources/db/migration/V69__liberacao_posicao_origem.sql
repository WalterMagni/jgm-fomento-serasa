-- Parecer da origem ganha posição, como o do Comitê: Favorável, Com ressalvas ou Desfavorável.
-- Pedido do time em 2026-10-08. Nulo nos cards antigos e enquanto a auxiliar não escolher.
ALTER TABLE liberacao_card ADD COLUMN posicao_origem VARCHAR(16);
