-- Origem do cadastro da empresa (ex.: CNPJA). Recriada a partir do schema já aplicado:
-- a migration original foi aplicada em 2026-08-10 e o arquivo se perdeu antes do commit,
-- o que fazia o Flyway abortar com "Detected applied migration not resolved locally: 49".
-- Idempotente de propósito: em bancos que já rodaram a versão original vira no-op.
ALTER TABLE company_details ADD COLUMN IF NOT EXISTS data_source VARCHAR(20);

CREATE INDEX IF NOT EXISTS idx_company_details_data_source
    ON company_details (data_source)
    WHERE data_source IS DISTINCT FROM 'CNPJA';
