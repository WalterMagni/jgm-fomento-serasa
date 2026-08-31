-- V14 criou esta coluna com nome errado ("participações societárias"). O dado nunca foi
-- isso: é sempre reports[0].checkFilingsHistorical (histórico de cheques sustados/extraviados
-- do CCF/SCPC). Nunca teve UI nem foi consumido — renomear é seguro.
ALTER TABLE credit_analysis RENAME COLUMN company_participations_report TO check_filings_historical;

COMMENT ON COLUMN credit_analysis.check_filings_historical IS
    'Seção checkFilingsHistorical do relatório Serasa: histórico de cheques '
    '(sustados/extraviados) por banco/agência, array checkFilingsHistoricalResponse.';
