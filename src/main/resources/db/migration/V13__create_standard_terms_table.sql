CREATE TABLE standard_terms (
    id BIGSERIAL PRIMARY KEY,
    cnpj VARCHAR(20) NOT NULL UNIQUE,
    term_text TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_standard_term_cnpj ON standard_terms(cnpj);
