-- Esteira unificada, fatia A1: o fluxo do Portal Comercial entra na esteira de prospecção.
-- Design em docs/plans/2026-10-11-esteira-unificada-design.md.
--
-- Estágios novos:
--   NOVA → EM_ANALISE → AGUARDANDO_ACEITE → DOCUMENTACAO → COMITE → CONTRATO → HABILITADO
--   saídas: REPROVADO, RECUSADO_CLIENTE, INERTE
--
-- A aprovação deixa de levar direto à coleta de documentos: entre as duas vêm a decisão com
-- limite e o aceite do cliente. Os cards que já tinham passado da aprovação não têm decisão
-- registrada; eles caem em DOCUMENTACAO marcados como legado, e o time completa o que faltar
-- na revisão combinada com o Walter.

-- ------------------------------------------------------------------ estágios dos cards

-- O índice parcial de CNPJ aberto cita os nomes antigos dos terminais. Ele sai antes do UPDATE:
-- durante a troca, um card terminal renomeado contaria como aberto e colidiria com o card
-- novo do mesmo CNPJ.
DROP INDEX IF EXISTS ux_prospeccao_cnpj_aberta;

ALTER TABLE prospeccao
    ADD COLUMN legado BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN prospeccao.legado IS
    'Card migrado da esteira antiga que já tinha passado da aprovação sem decisão registrada (V74)';

UPDATE prospeccao
   SET legado = TRUE
 WHERE estagio IN ('APROVADO', 'DOCS_PENDENTES', 'DOCS_COMPLETOS', 'PRONTO_HABILITACAO');

UPDATE prospeccao SET estagio = CASE estagio
        WHEN 'TRIAGEM'            THEN 'NOVA'
        WHEN 'APROVADO'           THEN 'DOCUMENTACAO'
        WHEN 'DOCS_PENDENTES'     THEN 'DOCUMENTACAO'
        WHEN 'DOCS_COMPLETOS'     THEN 'DOCUMENTACAO'
        WHEN 'PRONTO_HABILITACAO' THEN 'HABILITADO'
        WHEN 'REMOVIDO_RADAR'     THEN 'INERTE'
        ELSE estagio
    END;

-- Prazo vigente pelo estágio novo. Só NOVA e EM_ANALISE têm relógio; o resto corre por
-- silêncio do cliente ou não tem prazo.
UPDATE prospeccao SET prazo_estagio_horas = CASE estagio
        WHEN 'NOVA'       THEN 12
        WHEN 'EM_ANALISE' THEN 24
        ELSE 0
    END;

CREATE UNIQUE INDEX ux_prospeccao_cnpj_aberta
    ON prospeccao (cnpj)
    WHERE estagio NOT IN ('HABILITADO', 'REPROVADO', 'RECUSADO_CLIENTE', 'INERTE');

-- ------------------------------------------------------------------ timeline

-- A timeline guarda o estágio como texto e a entidade lê como enum: um nome antigo que
-- sobrasse aqui derrubaria a leitura do card inteiro. O passado é traduzido para o vocabulário
-- novo, e o texto do evento continua dizendo o que aconteceu na época.
UPDATE prospeccao_evento SET estagio_de = CASE estagio_de
        WHEN 'TRIAGEM'            THEN 'NOVA'
        WHEN 'APROVADO'           THEN 'AGUARDANDO_ACEITE'
        WHEN 'DOCS_PENDENTES'     THEN 'DOCUMENTACAO'
        WHEN 'DOCS_COMPLETOS'     THEN 'DOCUMENTACAO'
        WHEN 'PRONTO_HABILITACAO' THEN 'HABILITADO'
        WHEN 'REMOVIDO_RADAR'     THEN 'INERTE'
        ELSE estagio_de
    END
 WHERE estagio_de IN ('TRIAGEM', 'APROVADO', 'DOCS_PENDENTES', 'DOCS_COMPLETOS', 'PRONTO_HABILITACAO', 'REMOVIDO_RADAR');

UPDATE prospeccao_evento SET estagio_para = CASE estagio_para
        WHEN 'TRIAGEM'            THEN 'NOVA'
        WHEN 'APROVADO'           THEN 'AGUARDANDO_ACEITE'
        WHEN 'DOCS_PENDENTES'     THEN 'DOCUMENTACAO'
        WHEN 'DOCS_COMPLETOS'     THEN 'DOCUMENTACAO'
        WHEN 'PRONTO_HABILITACAO' THEN 'HABILITADO'
        WHEN 'REMOVIDO_RADAR'     THEN 'INERTE'
        ELSE estagio_para
    END
 WHERE estagio_para IN ('TRIAGEM', 'APROVADO', 'DOCS_PENDENTES', 'DOCS_COMPLETOS', 'PRONTO_HABILITACAO', 'REMOVIDO_RADAR');

-- Marco da migração na timeline de cada card afetado: quem abrir o card depois entende por
-- que ele está em DOCUMENTACAO sem decisão.
INSERT INTO prospeccao_evento (prospeccao_id, tipo, texto, usuario_nome, criado_em)
SELECT id, 'NOTA',
       'Card migrado para a esteira unificada sem decisão registrada. Revisar limite, condições e aceite com o time.',
       'Sistema', now()
  FROM prospeccao
 WHERE legado;

-- ------------------------------------------------------------------ dados comerciais

-- Os treze campos que o comercial preenchia no Portal Comercial. Colunas tipadas, não JSONB,
-- para virarem filtro e relatório sem parse.
ALTER TABLE prospeccao
    ADD COLUMN faturamento_mensal           NUMERIC(16, 2),
    ADD COLUMN ticket_medio                 NUMERIC(16, 2),
    ADD COLUMN valor_medio_operacoes        NUMERIC(16, 2),
    ADD COLUMN prazo_medio_vendas           INTEGER,
    ADD COLUMN prazo_medio_pgto_fornecedor  INTEGER,
    ADD COLUMN prazo_medio_entrega          INTEGER,
    ADD COLUMN possui_canhotos_dias         INTEGER,
    ADD COLUMN opera_mercadoria_entregue    BOOLEAN,
    ADD COLUMN opera_mercadoria_embarcada   BOOLEAN,
    ADD COLUMN regiao_atendimento           TEXT,
    ADD COLUMN produtos                     TEXT,
    ADD COLUMN parceiros_atuais             TEXT,
    ADD COLUMN outros_dados_comerciais      TEXT;

COMMENT ON COLUMN prospeccao.prazo_medio_vendas IS 'Dias';
COMMENT ON COLUMN prospeccao.prazo_medio_pgto_fornecedor IS 'Dias';
COMMENT ON COLUMN prospeccao.prazo_medio_entrega IS 'Dias';
COMMENT ON COLUMN prospeccao.possui_canhotos_dias IS 'Há quantos dias o cliente guarda canhotos';

-- ------------------------------------------------------------------ trava otimista

-- Duas analistas editando o mesmo card: a segunda recebe 409 em vez de apagar o trabalho da
-- primeira em silêncio. Mesmo mecanismo da liberação (V64).
ALTER TABLE prospeccao
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
