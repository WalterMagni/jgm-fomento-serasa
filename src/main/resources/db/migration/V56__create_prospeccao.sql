-- Esteira de prospecção: o card que atravessa da solicitação da análise até a documentação
-- completa. Substitui as abas VISÃO CEDENTE e DOC'S PENDENTES da planilha de controle.
--
-- O card é independente de credit_analysis: a análise é insumo, não o card. Uma empresa pode
-- entrar na esteira sem nunca ter sido consultada no Serasa (entrada manual do comercial), e
-- uma análise pode existir sem virar prospecção.
--
-- Papéis: users.role já é VARCHAR(50) e não é usado em nenhuma decisão de autorização hoje
-- (não há @PreAuthorize nem hasRole no projeto). Por isso esta migration NÃO reescreve o role
-- de ninguém — ROLE_USER continua válido e a camada de autorização o trata como comercial.
-- Reescrever em massa transformaria as analistas em comerciais silenciosamente; a atribuição
-- dos papéis novos é ato consciente do admin.
CREATE TABLE prospeccao (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cnpj                VARCHAR(14)  NOT NULL,
    razao_social        TEXT         NOT NULL,

    estagio             VARCHAR(24)  NOT NULL,
    -- TRIAGEM | EM_ANALISE | APROVADO | REPROVADO | DOCS_PENDENTES | DOCS_COMPLETOS
    -- | PRONTO_HABILITACAO | REMOVIDO_RADAR

    origem              VARCHAR(12)  NOT NULL DEFAULT 'MANUAL',   -- MANUAL | AUTOMATICA
    credit_analysis_id  BIGINT       REFERENCES credit_analysis (id) ON DELETE SET NULL,

    -- Comercial pode não ser usuário do portal: a planilha usa "DIRETO" para o que entra sem
    -- intermediário. Guardamos o id quando houver e o nome sempre, que sobrevive a
    -- desligamento.
    comercial_id        UUID         REFERENCES users (id) ON DELETE SET NULL,
    comercial_nome      VARCHAR(200),
    analista_id         UUID         REFERENCES users (id) ON DELETE SET NULL,

    estagio_desde       TIMESTAMP    NOT NULL DEFAULT now(),
    -- Snapshot do prazo vigente na hora da transição. Mudar a configuração de SLA não pode
    -- deixar card antigo estourado retroativamente.
    prazo_estagio_dias  INTEGER      NOT NULL,

    motivo_recusa       VARCHAR(40),
    -- QUANTIDADE_DE_RESTRICOES | SEGMENTO | PEFIN_COM_FUNDO | PROCESSO_COM_FIDC
    -- | RECUPERACAO_JUDICIAL | DECISAO_DIRETORIA | OUTRO
    observacao          TEXT,

    -- Empresa reprovada que volta é caso real na planilha. O contador distingue reanálise de
    -- card novo sem precisar varrer a timeline.
    reaberturas         INTEGER      NOT NULL DEFAULT 0,

    -- Alimentado por qualquer evento de cobrança. É o relógio dos 30 dias de silêncio que
    -- sugerem remover do radar.
    ultimo_contato_em   TIMESTAMP,

    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT now(),
    closed_at           TIMESTAMP
);

-- Uma empresa só pode ter um card em aberto. Terminais ficam de fora para permitir que a
-- mesma empresa volte num card novo depois de reprovada ou removida do radar.
CREATE UNIQUE INDEX ux_prospeccao_cnpj_aberta
    ON prospeccao (cnpj)
    WHERE estagio NOT IN ('REPROVADO', 'REMOVIDO_RADAR', 'PRONTO_HABILITACAO');

CREATE INDEX idx_prospeccao_estagio ON prospeccao (estagio, estagio_desde);
CREATE INDEX idx_prospeccao_comercial ON prospeccao (comercial_id) WHERE closed_at IS NULL;
CREATE INDEX idx_prospeccao_analista ON prospeccao (analista_id) WHERE closed_at IS NULL;
CREATE INDEX idx_prospeccao_cnpj ON prospeccao (cnpj);
