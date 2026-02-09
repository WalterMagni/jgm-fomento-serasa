CREATE TABLE api_request_counters (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider VARCHAR(20) NOT NULL UNIQUE,
    request_count BIGINT NOT NULL DEFAULT 0,
    last_request_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_api_request_counters_provider ON api_request_counters(provider);

-- Inicializa contadores para CNPJ Já e Serasa
INSERT INTO api_request_counters (provider, request_count) VALUES ('CNPJA', 0);
INSERT INTO api_request_counters (provider, request_count) VALUES ('SERASA', 0);
