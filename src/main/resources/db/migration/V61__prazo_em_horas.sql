-- Prazo da esteira passa a ser medido em horas, conforme o departamento de cadastro respondeu.
--
-- O time trabalha assim: doze horas para alguém pegar a análise, vinte e quatro para decidir, e
-- contadas em dia útil. A coleta de documentos NÃO tem prazo — ela depende do cliente. O que vale
-- ali é o silêncio: trinta dias corridos sem retorno é atenção, quarenta e cinco encaminha para
-- inerte.
--
-- Os valores antigos (1, 2 e 10 dias úteis) eram proposta minha, não acordo, e por isso são
-- convertidos e não preservados.
ALTER TABLE prospeccao RENAME COLUMN prazo_estagio_dias TO prazo_estagio_horas;

COMMENT ON COLUMN prospeccao.prazo_estagio_horas IS
    'Prazo do estágio em horas úteis. Zero significa estágio sem prazo, como a coleta de documentos';

UPDATE prospeccao SET prazo_estagio_horas = CASE estagio
    WHEN 'TRIAGEM'    THEN 12
    WHEN 'EM_ANALISE' THEN 24
    ELSE 0
END;
