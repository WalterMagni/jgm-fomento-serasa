-- Serasa Credit Rating: persistência híbrida (colunas indexáveis + JSONB + originalPayload)
ALTER TABLE credit_analysis ADD COLUMN client_id UUID REFERENCES clients(id);
ALTER TABLE credit_analysis ADD COLUMN risk_class VARCHAR(10);
ALTER TABLE credit_analysis ADD COLUMN probability DECIMAL(10,4);
ALTER TABLE credit_analysis ADD COLUMN analysis_date TIMESTAMP;
ALTER TABLE credit_analysis ADD COLUMN inquiry_history JSONB;
ALTER TABLE credit_analysis ADD COLUMN negative_summary JSONB;
ALTER TABLE credit_analysis ADD COLUMN original_payload TEXT;

CREATE INDEX idx_credit_analysis_client_id ON credit_analysis(client_id);
CREATE INDEX idx_credit_analysis_risk_class ON credit_analysis(risk_class);
CREATE INDEX idx_credit_analysis_analysis_date ON credit_analysis(analysis_date);
