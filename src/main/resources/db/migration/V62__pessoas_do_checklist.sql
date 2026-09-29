-- Bloco de pessoa no checklist: participação societária e avalista.
--
-- O departamento de cadastro respondeu duas coisas em 2026-09-29:
--
--   * documento é exigido de sócio com mais de 15% de participação;
--   * além dos sócios, algumas empresas têm AVALISTAS, que entregam os mesmos documentos
--     embora não constem do quadro societário.
--
-- O percentual só chega quando a origem é o Serasa. Na cópia da Receita ele não existe — hoje,
-- 194 de 201 vínculos estão sem percentual. Por isso a regra dos 15% desliga o bloco apenas
-- quando o número é conhecido e ficou abaixo do corte; no silêncio, o bloco nasce ativo e a
-- analista tira quem não interessa. O contrário deixaria o checklist de sócio vazio em quase toda
-- empresa.
ALTER TABLE prospeccao_documento
    ADD COLUMN pessoa_papel VARCHAR(10) NOT NULL DEFAULT 'SOCIO',
    ADD COLUMN socio_participacao NUMERIC(9, 4);

COMMENT ON COLUMN prospeccao_documento.pessoa_papel IS 'SOCIO | AVALISTA';
COMMENT ON COLUMN prospeccao_documento.socio_participacao IS
    'Participação no capital, quando conhecida. Só o Serasa informa; a Receita não traz';

-- O índice do checklist passa a agrupar por pessoa, não só por nome de sócio.
CREATE INDEX idx_prospeccao_documento_pessoa
    ON prospeccao_documento (prospeccao_id, pessoa_papel, socio_nome);
