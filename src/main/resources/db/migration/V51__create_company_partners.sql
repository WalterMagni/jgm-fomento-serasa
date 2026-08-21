-- Vínculo manual entre empresas parceiras (mesmo grupo econômico, CNPJs distintos).
-- Uma linha por par, com os CNPJs em ordem canônica (cnpj_a < cnpj_b) para que o
-- vínculo seja bidirecional sem duplicar registro.
CREATE TABLE company_partners (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cnpj_a       VARCHAR(14) NOT NULL,
    cnpj_b       VARCHAR(14) NOT NULL,
    note         TEXT,
    author_name  VARCHAR(255),
    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT ck_company_partners_order CHECK (cnpj_a < cnpj_b)
);

CREATE UNIQUE INDEX ux_company_partners_pair ON company_partners (cnpj_a, cnpj_b);
CREATE INDEX idx_company_partners_a ON company_partners (cnpj_a);
CREATE INDEX idx_company_partners_b ON company_partners (cnpj_b);
