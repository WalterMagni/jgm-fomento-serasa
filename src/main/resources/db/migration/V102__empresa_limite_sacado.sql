-- Limite da empresa como sacado (pedido do time em 2026-10-26): definido na Visão 360º e mostrado
-- na esteira de liberação. Chave é o CNPJ, como a nota: a Visão 360º abre empresas que ainda não
-- estão na carteira. Por enquanto só empresa; CPF fica de fora.
CREATE TABLE empresa_limite_sacado (
    cnpj           VARCHAR(14)    PRIMARY KEY,
    valor          NUMERIC(16, 2) NOT NULL CHECK (valor > 0),
    usuario_id     UUID,
    usuario_nome   VARCHAR(255),
    definido_em    TIMESTAMP      NOT NULL
);

-- Cada troca fica registrada. Valor nulo = o limite foi retirado.
CREATE TABLE empresa_limite_sacado_historico (
    id             UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    cnpj           VARCHAR(14)    NOT NULL,
    valor          NUMERIC(16, 2) CHECK (valor > 0),
    usuario_id     UUID,
    usuario_nome   VARCHAR(255),
    criado_em      TIMESTAMP      NOT NULL
);

CREATE INDEX idx_empresa_limite_sacado_historico_cnpj ON empresa_limite_sacado_historico (cnpj, criado_em DESC);
