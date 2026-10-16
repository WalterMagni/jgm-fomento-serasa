-- Consulta de processos judiciais pela Judit (pedido de 2026-10-15).
-- Ver docs/judit/02-implementacao.md.
--
-- Cada chamada custa dinheiro (plano anual, franquia de R$ 1.000/mês), por isso três travas:
-- marca do usuário, empresa habilitada e limite do mês somado nesta tabela de consultas.

-- Marca de usuário: quem liga a Judit numa empresa e consulta. Como Comitê e Diretoria, exige analista.
ALTER TABLE users ADD COLUMN judit BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users
    ADD CONSTRAINT ck_users_judit_exige_analista CHECK (NOT judit OR analista);

-- Empresa habilitada. Chave é o CNPJ, não o id de clients: o sacado costuma não estar na carteira
-- nem ter sido enriquecido (mesmo motivo da empresa_nota, V87).
CREATE TABLE judit_empresa (
    cnpj               VARCHAR(14)  PRIMARY KEY,
    habilitada         BOOLEAN      NOT NULL,
    alterado_por_id    UUID,
    alterado_por_nome  VARCHAR(255),
    alterado_em        TIMESTAMP    NOT NULL
);

-- Uma linha por chamada cobrada. Serve de cache (a tela mostra a última sem chamar de novo) e de
-- extrato: a soma do mês é o que trava o limite. A resposta crua fica guardada inteira — foi paga,
-- e o mapa de campos ainda vai mudar depois de conversar com a Judit.
CREATE TABLE judit_consulta (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    documento        VARCHAR(14)   NOT NULL,
    cnpj_empresa     VARCHAR(14)   NOT NULL,
    servico          VARCHAR(20)   NOT NULL CHECK (servico IN ('RESUMO', 'PROCESSOS')),
    custo            NUMERIC(10, 2) NOT NULL,
    total_processos  INTEGER,
    request_id       VARCHAR(100),
    resposta         JSONB         NOT NULL,
    usuario_id       UUID,
    usuario_nome     VARCHAR(255),
    criado_em        TIMESTAMP     NOT NULL
);

CREATE INDEX idx_judit_consulta_documento ON judit_consulta (documento, servico, criado_em DESC);
CREATE INDEX idx_judit_consulta_criado_em ON judit_consulta (criado_em);
