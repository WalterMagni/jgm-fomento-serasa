-- Marca quando um lançamento teve a decisão desfeita (reaberto) a partir da página do cliente.
-- Usado para destacar o lançamento ao voltar para a triagem da Praça de Pagamento.
ALTER TABLE payment_place_entries
    ADD COLUMN reopened_at TIMESTAMP;
