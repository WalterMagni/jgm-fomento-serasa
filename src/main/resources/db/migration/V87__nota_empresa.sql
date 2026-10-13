-- Nota que a equipe dá para a empresa, de A (excelente) a D (evitar operar).
-- Chave é o CNPJ, não o id de clients: a Visão 360º abre empresas que ainda não estão na carteira.
CREATE TABLE empresa_nota (
    cnpj          VARCHAR(14)  PRIMARY KEY,
    nota          VARCHAR(1)   NOT NULL CHECK (nota IN ('A', 'B', 'C', 'D')),
    usuario_id    UUID,
    usuario_nome  VARCHAR(255),
    atribuida_em  TIMESTAMP    NOT NULL
);

CREATE INDEX idx_empresa_nota_nota ON empresa_nota (nota);

-- Cada troca fica registrada. Nota nula = a nota foi retirada.
CREATE TABLE empresa_nota_historico (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    cnpj          VARCHAR(14)  NOT NULL,
    nota          VARCHAR(1)   CHECK (nota IN ('A', 'B', 'C', 'D')),
    usuario_id    UUID,
    usuario_nome  VARCHAR(255),
    criado_em     TIMESTAMP    NOT NULL
);

CREATE INDEX idx_empresa_nota_historico_cnpj ON empresa_nota_historico (cnpj, criado_em DESC);
