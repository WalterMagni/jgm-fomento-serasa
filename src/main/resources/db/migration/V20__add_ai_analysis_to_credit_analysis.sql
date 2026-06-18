ALTER TABLE credit_analysis ADD COLUMN IF NOT EXISTS ai_analysis TEXT;
ALTER TABLE credit_analysis ADD COLUMN IF NOT EXISTS ai_analysis_date TIMESTAMP;
