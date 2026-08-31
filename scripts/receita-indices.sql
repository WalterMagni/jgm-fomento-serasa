-- Índices que o portal precisa na cópia local do cadastro da Receita (banco `cnpj`,
-- carregado pelo cnpj-data-pipeline).
--
-- QUANDO RODAR
--   * uma vez, ao provisionar o banco da Receita em um ambiente novo;
--   * DE NOVO depois de toda recarga do pipeline — a recarga pode recriar as tabelas e
--     levar os índices junto, e sem eles a consulta de sócios cai de ~25ms para ~2700ms.
--
-- COMO RODAR
--   docker exec -i cnpj-pipeline-postgres psql -U postgres -d cnpj < scripts/receita-indices.sql
--
-- CONCURRENTLY não bloqueia leitura nem escrita, então é seguro rodar com o pipeline no ar.
-- Por isso mesmo não pode estar dentro de uma transação: rode o arquivo como está, sem
-- envolver em BEGIN/COMMIT.

-- Busca reversa do quadro societário: dado um sócio, em quais empresas ele figura.
-- Sustenta o grupo econômico e o alerta de partes ligadas da praça de pagamento.
-- O nome entra na chave porque a máscara de CPF publicada pela Receita (6 dígitos) colide.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_socios_cpf_nome
    ON socios (cnpj_cpf_do_socio, nome_socio);

-- Conferência:
--   select indexname from pg_indexes where tablename = 'socios';
