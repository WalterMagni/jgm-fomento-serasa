-- V21: Tabela de análise de crédito de Pessoa Física (CPF) via Serasa RELATORIO_AVANCADO_PF
CREATE TABLE person_analysis (
    id              BIGSERIAL PRIMARY KEY,
    cpf             VARCHAR(11)  NOT NULL,
    person_name     VARCHAR(500),

    -- Seções do relatório armazenadas como JSONB
    registration         JSONB,   -- reports[0].registration (dados pessoais, endereço, telefone)
    negative_summary     JSONB,   -- reports[0].negativeData (pefin, refin, notary, check, collectionRecords)
    facts                JSONB,   -- reports[0].facts (inquiry, judgementFilings, bankrupts, stolenDocuments)
    partner_companies    JSONB,   -- reports[0].partner (empresas onde é sócio)

    original_payload TEXT,
    consulta_em     TIMESTAMP    NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- Index único por CPF (apenas a consulta mais recente será usada via ORDER BY)
CREATE INDEX idx_person_analysis_cpf          ON person_analysis (cpf);
CREATE INDEX idx_person_analysis_person_name  ON person_analysis (person_name);
CREATE INDEX idx_person_analysis_consulta_em  ON person_analysis (consulta_em DESC);

-- GIN indexes para queries JSONB
CREATE INDEX idx_person_analysis_negative_summary_gin ON person_analysis USING GIN (negative_summary);
CREATE INDEX idx_person_analysis_facts_gin            ON person_analysis USING GIN (facts);
