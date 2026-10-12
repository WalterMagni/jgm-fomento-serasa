-- Esteira unificada, fatia A2: decisão com limite e aceite do cliente.
-- Design em docs/plans/2026-10-11-esteira-unificada-design.md.
--
-- Vem do Portal Comercial: a analista aprova com um limite e condições por produto, e o cliente
-- responde — aceita, recusa ou faz contraproposta. Lá a contraproposta seguida de nova decisão
-- somava as condições antigas às novas; aqui cada decisão é uma linha própria, e só uma é a
-- vigente.

-- ------------------------------------------------------------------ catálogo de produtos

CREATE TABLE produto_catalogo (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    nome        VARCHAR(120) NOT NULL,
    ordem       INTEGER      NOT NULL DEFAULT 0,
    -- Desativa em vez de apagar: decisão antiga guarda o nome do produto como texto e continua
    -- legível, mas o produto some da lista de escolha.
    ativo       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_produto_catalogo_nome ON produto_catalogo (upper(nome));

-- Os mesmos do Portal Comercial.
INSERT INTO produto_catalogo (nome, ordem) VALUES
    ('DUPLICATA',           1),
    ('CHEQUE PRÓPRIO',      2),
    ('CHEQUE DE TERCEIROS', 3),
    ('PEDIDO',              4),
    ('ESCROW',              5);

-- ------------------------------------------------------------------ decisão

CREATE TABLE prospeccao_decisao (
    id                 UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    prospeccao_id      UUID          NOT NULL REFERENCES prospeccao (id) ON DELETE CASCADE,
    -- 1, 2, 3… na ordem em que a analista decidiu. Contraproposta e devolução do comitê geram
    -- decisão nova; a anterior fica como histórico.
    versao             INTEGER       NOT NULL,
    resultado          VARCHAR(10)   NOT NULL,   -- APROVADO | REPROVADO
    -- Teto informativo da aprovação. Nulo na reprovação.
    limite             NUMERIC(16, 2),
    parecer            TEXT,
    motivo_recusa      VARCHAR(40),
    vigente            BOOLEAN       NOT NULL DEFAULT TRUE,
    decidido_por       UUID          REFERENCES users (id) ON DELETE SET NULL,
    -- Denormalizado, como na timeline: o histórico sobrevive à saída de quem decidiu.
    decidido_por_nome  VARCHAR(200),
    decidido_em        TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT ux_prospeccao_decisao_versao UNIQUE (prospeccao_id, versao),
    CONSTRAINT ck_prospeccao_decisao_resultado CHECK (resultado IN ('APROVADO', 'REPROVADO')),
    CONSTRAINT ck_prospeccao_decisao_limite CHECK (
        (resultado = 'APROVADO' AND limite > 0) OR (resultado = 'REPROVADO' AND limite IS NULL))
);

-- No máximo uma vigente por card.
CREATE UNIQUE INDEX ux_prospeccao_decisao_vigente
    ON prospeccao_decisao (prospeccao_id) WHERE vigente;

CREATE TABLE prospeccao_decisao_condicao (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    decisao_id  UUID          NOT NULL REFERENCES prospeccao_decisao (id) ON DELETE CASCADE,
    -- Nome do produto como texto, e não FK: o catálogo pode mudar sem reescrever decisão antiga.
    produto     VARCHAR(120)  NOT NULL,
    valor       NUMERIC(16, 2) NOT NULL CHECK (valor > 0),
    ordem       INTEGER       NOT NULL DEFAULT 0
);

CREATE INDEX idx_prospeccao_decisao_condicao ON prospeccao_decisao_condicao (decisao_id, ordem);

-- ------------------------------------------------------------------ aceite do cliente

CREATE TABLE prospeccao_aceite (
    id                   UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    prospeccao_id        UUID          NOT NULL REFERENCES prospeccao (id) ON DELETE CASCADE,
    -- A decisão a que o cliente respondeu.
    decisao_id           UUID          NOT NULL REFERENCES prospeccao_decisao (id) ON DELETE CASCADE,
    resposta             VARCHAR(16)   NOT NULL,   -- ACEITO | RECUSADO | CONTRAPROPOSTA
    limite_sugerido      NUMERIC(16, 2),
    -- [{produto, valor}]. JSONB porque é só o que o cliente pediu: não vira condição até a
    -- analista decidir de novo.
    condicoes_sugeridas  JSONB,
    mensagem             TEXT,
    registrado_por       UUID          REFERENCES users (id) ON DELETE SET NULL,
    registrado_por_nome  VARCHAR(200),
    -- Fase A: a analista registra o que o comercial trouxe. Fase B: o próprio comercial.
    em_nome_do_comercial BOOLEAN       NOT NULL DEFAULT FALSE,
    registrado_em        TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT ck_prospeccao_aceite_resposta CHECK (resposta IN ('ACEITO', 'RECUSADO', 'CONTRAPROPOSTA')),
    CONSTRAINT ck_prospeccao_aceite_contraproposta CHECK (
        resposta <> 'CONTRAPROPOSTA' OR (mensagem IS NOT NULL AND length(trim(mensagem)) > 0))
);

CREATE INDEX idx_prospeccao_aceite_card ON prospeccao_aceite (prospeccao_id, registrado_em DESC);
