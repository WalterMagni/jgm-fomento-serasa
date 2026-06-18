-- V17: Adiciona campo visao_cedente na tabela credit_analysis
-- Valores: PENDENTE (default), SIM (empresa opera como cedente), NAO (não opera como cedente)
ALTER TABLE credit_analysis
    ADD COLUMN IF NOT EXISTS visao_cedente VARCHAR(10) NOT NULL DEFAULT 'PENDENTE';

COMMENT ON COLUMN credit_analysis.visao_cedente IS
    'Indica se a empresa possui perfil de cedente de crédito (factoring/fomento). PENDENTE = não consultado ainda, SIM = opera como cedente, NAO = sem registros de cedente.';
