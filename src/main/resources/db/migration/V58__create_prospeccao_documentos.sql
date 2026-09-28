-- Checklist documental. O catálogo é editável pelo admin; o card guarda uma cópia do que
-- valia no momento em que foi materializado, para que mexer no catálogo não reescreva
-- histórico.
--
-- A lista foi extraída literalmente da aba DOC'S PENDENTES, onde já é aplicada à mão, empresa
-- por empresa — repetida idêntica em 8 empresas, em dois níveis: 13 itens da empresa e 5 de
-- cada sócio.
CREATE TABLE documento_tipo (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    codigo       VARCHAR(60) NOT NULL UNIQUE,
    nome         TEXT        NOT NULL,
    escopo       VARCHAR(8)  NOT NULL,   -- EMPRESA | SOCIO
    obrigatorio  BOOLEAN     NOT NULL DEFAULT TRUE,
    -- Item 11 da planilha ("Possui filiais?") não é documento, é pergunta. Itens assim entram
    -- como informativo: aparecem no checklist, aceitam resposta, e nunca travam o avanço.
    informativo  BOOLEAN     NOT NULL DEFAULT FALSE,
    -- A certidão simplificada é retirada na JUCESP, ou seja, só existe em São Paulo. Fora de
    -- SP o item nasce NAO_APLICAVEL. A planilha traz isso escrito à mão: "Não tem, cliente de
    -- MG", "Cliente é de RS".
    somente_uf   VARCHAR(2),
    ativo        BOOLEAN     NOT NULL DEFAULT TRUE,
    ordem        INTEGER     NOT NULL,
    created_at   TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_documento_tipo_ativo ON documento_tipo (escopo, ordem) WHERE ativo;

CREATE TABLE prospeccao_documento (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    prospeccao_id         UUID        NOT NULL REFERENCES prospeccao (id) ON DELETE CASCADE,
    documento_tipo_id     UUID        REFERENCES documento_tipo (id) ON DELETE SET NULL,

    -- Cópias do catálogo no momento da materialização.
    codigo_snapshot       VARCHAR(60) NOT NULL,
    nome_snapshot         TEXT        NOT NULL,
    obrigatorio_snapshot  BOOLEAN     NOT NULL,
    informativo_snapshot  BOOLEAN     NOT NULL DEFAULT FALSE,

    escopo                VARCHAR(8)  NOT NULL,   -- EMPRESA | SOCIO
    -- Preenchidos quando escopo = SOCIO, a partir do QSA (shareholders / shareholder_companies).
    socio_nome            VARCHAR(200),
    socio_documento       VARCHAR(14),
    -- Sócio tem ciclo de vida: a planilha registra "vai sair da sociedade" e "Faleceu —
    -- recebemos certidão de óbito". Inativo some da conta de obrigatórios sem sumir da tela.
    socio_ativo           BOOLEAN     NOT NULL DEFAULT TRUE,
    socio_inativo_motivo  TEXT,

    status                VARCHAR(16) NOT NULL DEFAULT 'PENDENTE',
    -- PENDENTE | RECEBIDO | VALIDADO | REJEITADO | NAO_APLICAVEL | DISPENSADO
    --
    -- RECEBIDO e VALIDADO são estados diferentes porque na planilha a observação é
    -- conferência de conteúdo, não recebimento: "OK - CRC válido", "OK - Sofisa, Bradesco,
    -- Itaú", "OK - 7 Clientes". Só VALIDADO, NAO_APLICAVEL e DISPENSADO fecham o obrigatório.

    -- Status estruturado e texto livre lado a lado: é a observação que carrega o dado real
    -- ("Válida até 2032", "7 Clientes").
    observacao            TEXT,
    motivo                TEXT,       -- exigido em REJEITADO e DISPENSADO

    recebido_em           TIMESTAMP,
    recebido_por          UUID        REFERENCES users (id) ON DELETE SET NULL,
    validado_em           TIMESTAMP,
    validado_por          UUID        REFERENCES users (id) ON DELETE SET NULL,
    atualizado_em         TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_prospeccao_documento_card ON prospeccao_documento (prospeccao_id, escopo, socio_nome);

-- O avanço para DOCS_COMPLETOS pergunta "sobrou algum obrigatório em aberto?". Índice parcial
-- porque a resposta esperada é uma lista curta ou vazia.
CREATE INDEX idx_prospeccao_documento_pendente
    ON prospeccao_documento (prospeccao_id)
    WHERE obrigatorio_snapshot AND status IN ('PENDENTE', 'RECEBIDO', 'REJEITADO');

-- Catálogo inicial — empresa (13 itens).
INSERT INTO documento_tipo (codigo, nome, escopo, obrigatorio, informativo, somente_uf, ordem) VALUES
    ('certidao-simplificada',  'Certidão simplificada (retirar na JUCESP)',                              'EMPRESA', TRUE,  FALSE, 'SP',  1),
    ('receita-federal',        'Receita Federal',                                                        'EMPRESA', TRUE,  FALSE, NULL,  2),
    ('contrato-social',        'Contrato / última alteração consolidada',                                'EMPRESA', TRUE,  FALSE, NULL,  3),
    ('faturamento-12m',        'Faturamento atualizado — datado e assinado por sócio ou contador',       'EMPRESA', TRUE,  FALSE, NULL,  4),
    ('declaracao-instituicoes','Declaração com instituições',                                            'EMPRESA', TRUE,  FALSE, NULL,  5),
    ('comprovante-endereco',   'Comprovante de endereço — emissão até 90 dias',                          'EMPRESA', TRUE,  FALSE, NULL,  6),
    ('endividamento',          'Endividamento',                                                          'EMPRESA', TRUE,  FALSE, NULL,  7),
    ('curva-abc',              'Curva ABC',                                                              'EMPRESA', TRUE,  FALSE, NULL,  8),
    ('autorizacao-scr',        'Autorização SCR',                                                        'EMPRESA', TRUE,  FALSE, NULL,  9),
    ('balanco-dre',            'Balanço patrimonial e DRE',                                              'EMPRESA', TRUE,  FALSE, NULL, 10),
    ('filiais',                'Possui filiais? Vai operar pelas filiais?',                              'EMPRESA', FALSE, TRUE,  NULL, 11),
    ('dados-contrato',         'Dados para informação do contrato',                                      'EMPRESA', TRUE,  FALSE, NULL, 12),
    ('relatorio-visita',       'Relatório de visita comercial',                                          'EMPRESA', TRUE,  FALSE, NULL, 13);

-- Catálogo inicial — por sócio (5 itens). Os dois últimos são "se houver".
INSERT INTO documento_tipo (codigo, nome, escopo, obrigatorio, informativo, somente_uf, ordem) VALUES
    ('socio-identidade',           'RG/CPF ou CNH',                                            'SOCIO', TRUE,  FALSE, NULL, 1),
    ('socio-comprovante-endereco', 'Comprovante de endereço — emissão até 60 dias',            'SOCIO', TRUE,  FALSE, NULL, 2),
    ('socio-irpf',                 'IRPF',                                                     'SOCIO', TRUE,  FALSE, NULL, 3),
    ('socio-certidao-casamento',   'Certidão de casamento (se houver)',                        'SOCIO', FALSE, FALSE, NULL, 4),
    ('socio-identidade-conjuge',   'RG/CPF ou CNH do cônjuge — somente se assinar como avalista','SOCIO', FALSE, FALSE, NULL, 5);
