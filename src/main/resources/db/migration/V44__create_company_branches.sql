-- Cache das filiais (estabelecimentos da raiz do CNPJ) vindas do BigQuery.
-- Uma linha por estabelecimento. fetched_at controla o TTL por raiz.
CREATE TABLE company_branches (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cnpj_raiz       VARCHAR(8)  NOT NULL,
    cnpj            VARCHAR(14) NOT NULL,
    matriz_filial   VARCHAR(1),
    nome_fantasia   VARCHAR(255),
    uf              VARCHAR(2),
    id_municipio    VARCHAR(7),
    nome_municipio  VARCHAR(255),
    logradouro      VARCHAR(255),
    numero          VARCHAR(30),
    bairro          VARCHAR(255),
    cep             VARCHAR(10),
    latitude        NUMERIC(10, 6),
    longitude       NUMERIC(10, 6),
    fetched_at      TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_company_branches_raiz ON company_branches (cnpj_raiz);
