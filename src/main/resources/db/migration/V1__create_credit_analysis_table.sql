CREATE TABLE credit_analysis (
    id BIGSERIAL PRIMARY KEY,
    cnpj VARCHAR(14) NOT NULL,
    score INTEGER,
    consulta_em TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_credit_analysis_cnpj ON credit_analysis(cnpj);
CREATE INDEX idx_credit_analysis_consulta_em ON credit_analysis(consulta_em);
