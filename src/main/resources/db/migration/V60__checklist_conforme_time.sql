-- Obrigatoriedade do checklist conforme o departamento de cadastro respondeu em 2026-09-29.
--
-- Os valores anteriores eram uma proposta minha a partir da planilha; agora são a regra do time.
-- Nove dos treze itens da empresa são obrigatórios, e dois dos cinco do sócio.
--
-- Três itens têm natureza própria: são obrigatórios, mas o cliente pode legitimamente não ter
-- (endividamento, curva ABC) ou não aceitar assinar (autorização SCR). Nesses casos o time libera
-- mediante justificativa, e não simplesmente ignora — daí a coluna de exceção, que muda o texto da
-- ação na tela e obriga a escrever o motivo.
ALTER TABLE documento_tipo
    ADD COLUMN admite_excecao BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN documento_tipo.admite_excecao IS
    'Obrigatório, mas liberável mediante justificativa registrada';

ALTER TABLE prospeccao_documento
    ADD COLUMN admite_excecao_snapshot BOOLEAN NOT NULL DEFAULT FALSE;

-- Empresa: o que deixou de ser obrigatório.
UPDATE documento_tipo SET obrigatorio = FALSE
 WHERE escopo = 'EMPRESA'
   AND codigo IN ('certidao-simplificada', 'receita-federal', 'balanco-dre');

-- Empresa: o que é obrigatório e admite exceção justificada.
UPDATE documento_tipo SET obrigatorio = TRUE, admite_excecao = TRUE
 WHERE escopo = 'EMPRESA'
   AND codigo IN ('endividamento', 'curva-abc', 'autorizacao-scr');

-- Empresa: obrigatórios sem exceção.
UPDATE documento_tipo SET obrigatorio = TRUE
 WHERE escopo = 'EMPRESA'
   AND codigo IN ('contrato-social', 'faturamento-12m', 'declaracao-instituicoes',
                  'comprovante-endereco', 'dados-contrato', 'relatorio-visita');

-- Sócio: só identidade e comprovante de endereço são exigidos.
UPDATE documento_tipo SET obrigatorio = TRUE
 WHERE escopo = 'SOCIO' AND codigo IN ('socio-identidade', 'socio-comprovante-endereco');

UPDATE documento_tipo SET obrigatorio = FALSE
 WHERE escopo = 'SOCIO'
   AND codigo IN ('socio-irpf', 'socio-certidao-casamento', 'socio-identidade-conjuge');

-- Correção pontual dos cards já abertos.
--
-- O snapshot existe justamente para que mexer no catálogo não reescreva histórico, e esta migration
-- é a exceção deliberada a essa regra: o que estava gravado nos cards não era uma decisão do time,
-- era o meu palpite. Cobrar de alguém um documento que a casa não exige seria carregar o erro
-- adiante. Só alcança card ainda aberto; o que já teve desfecho fica como foi decidido na época.
UPDATE prospeccao_documento pd
   SET obrigatorio_snapshot = dt.obrigatorio,
       admite_excecao_snapshot = dt.admite_excecao
  FROM documento_tipo dt, prospeccao p
 WHERE pd.documento_tipo_id = dt.id
   AND pd.prospeccao_id = p.id
   AND p.closed_at IS NULL;
