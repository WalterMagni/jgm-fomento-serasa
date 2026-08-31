-- Alerta de partes ligadas: cedente e sacado do mesmo título compartilham sócio.
--
-- Em fomento esse é o indício clássico de duplicata simulada — a pessoa emite título de uma
-- empresa dela contra outra empresa dela e vende o recebível. Não entra no score sacado/cedente
-- porque não pesa para nenhum dos dois lados: é um sinal de fraude, ortogonal à classificação
-- da praça, e por isso vive em coluna própria.
ALTER TABLE payment_place_entries ADD COLUMN related_parties BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE payment_place_entries ADD COLUMN related_parties_detail TEXT;
ALTER TABLE payment_place_entries ADD COLUMN related_parties_checked_at TIMESTAMP;

-- A triagem filtra por este sinal, e ele é raro: índice parcial só sobre os marcados.
CREATE INDEX idx_payment_place_entries_related_parties
    ON payment_place_entries (batch_id) WHERE related_parties;
