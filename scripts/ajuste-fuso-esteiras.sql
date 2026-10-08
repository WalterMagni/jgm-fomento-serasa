-- Corrige, uma única vez, os horários gravados em UTC pelas esteiras antes do backend passar a
-- rodar no fuso de São Paulo (deploy de 2026-10). Volta 3 h em cada coluna de horário que o
-- sistema grava sozinho, nas tabelas das duas esteiras e das notificações.
--
-- Fica de fora o que a pessoa digita: liberacao_card.prazo já foi informado no horário local.
--
-- COMO RODAR (no servidor, com o backend PARADO, antes do deploy):
--   docker compose stop backend
--   docker exec -i portal-serasa-db psql -U serasa -d portal_serasa < scripts/ajuste-fuso-esteiras.sql
--   ./deploy.sh
--
-- Rodar de novo não faz nada: a tabela ajuste_fuso_aplicado registra que já foi feito.
-- Não rodar em ambiente de desenvolvimento, onde o backend sempre gravou no horário local.
DO $$
DECLARE
    coluna record;
BEGIN
    IF to_regclass('public.ajuste_fuso_aplicado') IS NOT NULL THEN
        RAISE NOTICE 'Ajuste de fuso já aplicado antes; nada a fazer.';
        RETURN;
    END IF;

    FOR coluna IN
        SELECT table_name, column_name
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND data_type = 'timestamp without time zone'
          AND (table_name LIKE 'prospeccao%' OR table_name LIKE 'liberacao%' OR table_name = 'notificacao')
          AND NOT (table_name = 'liberacao_card' AND column_name = 'prazo')
    LOOP
        EXECUTE format('UPDATE %I SET %I = %I - interval ''3 hours'' WHERE %I IS NOT NULL',
                       coluna.table_name, coluna.column_name, coluna.column_name, coluna.column_name);
        RAISE NOTICE 'Ajustado: %.%', coluna.table_name, coluna.column_name;
    END LOOP;

    CREATE TABLE ajuste_fuso_aplicado (aplicado_em TIMESTAMP NOT NULL DEFAULT now());
    INSERT INTO ajuste_fuso_aplicado DEFAULT VALUES;
END $$;
