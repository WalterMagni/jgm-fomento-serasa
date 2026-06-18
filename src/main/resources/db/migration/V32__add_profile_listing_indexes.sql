create index if not exists idx_credit_analysis_cnpj_consulta_id_desc
    on credit_analysis (cnpj, consulta_em desc, id desc);

create index if not exists idx_clients_created_at_desc
    on clients (created_at desc);
