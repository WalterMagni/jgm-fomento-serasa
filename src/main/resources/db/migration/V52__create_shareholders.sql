-- Quadro societário consolidado: uma linha por sócio (pessoa física ou jurídica) e uma
-- linha por vínculo sócio × empresa. Alimentado por duas fontes com precisões diferentes:
--
--   RECEITA_MASK - cópia local do cadastro da Receita (tabela socios). Cobre a carteira
--                  inteira e é gratuita, mas o CPF vem mascarado ('***216508**' = dígitos
--                  4..9 do CPF). A máscara é determinística, então serve como chave de
--                  cruzamento entre empresas mesmo sem o CPF completo.
--   SERASA       - QSA do relatório de empresa, que traz o CPF completo. Quando chega,
--                  faz upgrade da linha existente (casada por document_mask + name) em vez
--                  de criar sócio duplicado: '***' || substring(cpf,4,6) || '**' = a máscara.
--
-- O CPF completo é o que destrava a consulta de processos/restritivos por pessoa em
-- serviços externos, que não aceitam máscara.
CREATE TABLE shareholders (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_type         VARCHAR(12)  NOT NULL,  -- CPF | CNPJ | ESTRANGEIRO
    document              VARCHAR(14),            -- completo; NULL enquanto só houver máscara
    document_mask         VARCHAR(14)  NOT NULL,  -- CPF mascarado; para sócio PJ, o próprio CNPJ
    name                  TEXT         NOT NULL,
    document_source       VARCHAR(20)  NOT NULL,  -- RECEITA_MASK | SERASA | CNPJA
    age_range             VARCHAR(1),             -- faixa_etaria da Receita
    -- Espaço para a consulta paga de pessoa física (dívidas, protestos, pefin/refin, score).
    -- Não é preenchido pela carga gratuita; fica NULL até alguém consultar.
    restrictive_data      JSONB,
    restrictive_score     INTEGER,
    restrictive_fetched_at TIMESTAMP,
    created_at            TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP    NOT NULL DEFAULT now()
);

-- Chave de identidade da carga gratuita. A máscara sozinha colide (6 dígitos para ~200M
-- CPFs), por isso o nome entra na chave.
CREATE UNIQUE INDEX ux_shareholders_mask_name ON shareholders (document_mask, name);

-- O documento completo, quando conhecido, é único de verdade.
CREATE UNIQUE INDEX ux_shareholders_document ON shareholders (document) WHERE document IS NOT NULL;

CREATE INDEX idx_shareholders_name ON shareholders (name);

CREATE TABLE shareholder_companies (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shareholder_id            UUID         NOT NULL REFERENCES shareholders (id) ON DELETE CASCADE,
    cnpj_raiz                 VARCHAR(8)   NOT NULL,
    company_name              TEXT,                 -- razao_social denormalizada
    qualification_code        VARCHAR(2),
    qualification_description VARCHAR(255),
    entry_date                DATE,
    capital_percent           NUMERIC(9, 4),        -- só o Serasa informa
    partner_status            VARCHAR(30),          -- status do sócio no Serasa (ex.: ATIVA)
    company_status            VARCHAR(2),           -- situacao_cadastral da matriz na Receita
    source                    VARCHAR(20)  NOT NULL,
    fetched_at                TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT ux_shareholder_companies UNIQUE (shareholder_id, cnpj_raiz)
);

CREATE INDEX idx_shareholder_companies_raiz ON shareholder_companies (cnpj_raiz);
CREATE INDEX idx_shareholder_companies_shareholder ON shareholder_companies (shareholder_id);
