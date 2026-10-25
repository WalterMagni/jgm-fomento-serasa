-- Documento de referência das informações comerciais (pedido do Walter em 2026-10-24): um arquivo
-- por empresa, PDF ou imagem, com os dados externos que o time consulta ao preencher a ficha — a
-- "colinha". Fica no cabeçalho do painel, e não em cada ficha. Enviar outro substitui o anterior.
create table if not exists company_commercial_reference_file (
    client_id uuid primary key,
    file_name varchar(255),
    content_type varchar(100) not null,
    file_size bigint,
    data bytea not null,
    uploaded_by_name varchar(200),
    uploaded_at timestamp not null default now(),
    constraint fk_commercial_reference_file_client
        foreign key (client_id) references clients (id) on delete cascade
);
