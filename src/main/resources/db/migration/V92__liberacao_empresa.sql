-- Empresa da operação na esteira de liberação (pedido de 2026-10-20): SEC ou FIDC. Opcional;
-- card antigo fica sem.
ALTER TABLE liberacao_card
    ADD COLUMN empresa VARCHAR(4) CHECK (empresa IN ('SEC', 'FIDC'));
