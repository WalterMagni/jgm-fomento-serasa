-- Tabela de Usuários e Autenticação
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'ROLE_USER',
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Índices GIN para buscas em JSONB (Persistência Híbrida)
CREATE INDEX IF NOT EXISTS idx_credit_analysis_inquiry_history ON credit_analysis USING GIN (inquiry_history jsonb_path_ops);
CREATE INDEX IF NOT EXISTS idx_credit_analysis_negative_summary ON credit_analysis USING GIN (negative_summary jsonb_path_ops);
CREATE INDEX IF NOT EXISTS idx_credit_analysis_partner_details ON credit_analysis USING GIN (partner_details jsonb_path_ops);
CREATE INDEX IF NOT EXISTS idx_company_details_members ON company_details USING GIN (members jsonb_path_ops);
CREATE INDEX IF NOT EXISTS idx_company_details_main_activity ON company_details USING GIN (main_activity jsonb_path_ops);
CREATE INDEX IF NOT EXISTS idx_company_details_side_activities ON company_details USING GIN (side_activities jsonb_path_ops);
