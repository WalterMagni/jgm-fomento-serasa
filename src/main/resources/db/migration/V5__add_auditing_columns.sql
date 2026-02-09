-- Auditoria: createdAt/updatedAt para rastreamento de alterações
ALTER TABLE clients ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;
ALTER TABLE credit_analysis ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;
ALTER TABLE company_details ADD COLUMN IF NOT EXISTS modified_at TIMESTAMP;
