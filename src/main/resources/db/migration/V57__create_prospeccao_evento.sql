-- Timeline do card. É append-only: nada aqui é editado ou apagado, e toda troca de estágio
-- grava um evento na mesma transação da troca.
--
-- É daqui que sai o SLA real por estágio — a métrica que a coluna QTDE DE DIAS PARA RETORNO
-- da planilha pedia e nunca teve (0 de 114 linhas preenchidas).
--
-- Substitui também as colunas SOLICITAÇÃO POR EMAIL e SOLICITAÇÃO POR WHATS da aba
-- DOC'S PENDENTES, que só guardavam a última cobrança de cada canal.
CREATE TABLE prospeccao_evento (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    prospeccao_id UUID        NOT NULL REFERENCES prospeccao (id) ON DELETE CASCADE,

    tipo          VARCHAR(30) NOT NULL,
    -- CRIACAO | TRANSICAO | COBRANCA | NOTA | DOC_RECEBIDO | DOC_VALIDADO | DOC_REJEITADO
    -- | DOC_DISPENSADO | ARQUIVO_ENVIADO | ARQUIVO_REMOVIDO | ANALISTA_ALTERADO | REABERTURA

    canal         VARCHAR(12),
    -- EMAIL | WHATSAPP | LIGACAO | REUNIAO | SISTEMA. NULL quando o evento não é contato.

    estagio_de    VARCHAR(24),
    estagio_para  VARCHAR(24),

    texto         TEXT,

    usuario_id    UUID        REFERENCES users (id) ON DELETE SET NULL,
    -- Denormalizado de propósito: a timeline precisa continuar legível depois que o usuário
    -- sair da empresa e a linha em users for removida.
    usuario_nome  VARCHAR(200),

    criado_em     TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_prospeccao_evento_card ON prospeccao_evento (prospeccao_id, criado_em DESC);

-- Sustenta o cálculo de dias parados por estágio sem varrer a tabela inteira.
CREATE INDEX idx_prospeccao_evento_transicao
    ON prospeccao_evento (prospeccao_id, criado_em) WHERE tipo = 'TRANSICAO';
