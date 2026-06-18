CREATE TABLE api_usage_logs (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_name   VARCHAR(255),
    company_name VARCHAR(500),
    cnpj        VARCHAR(14),
    query_type  VARCHAR(20) NOT NULL,
    cost        NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    queried_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_api_usage_logs_queried_at ON api_usage_logs (queried_at DESC);
CREATE INDEX idx_api_usage_logs_cnpj       ON api_usage_logs (cnpj);
