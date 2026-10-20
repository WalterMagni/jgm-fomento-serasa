-- Anexos da ficha comercial (pedido de 2026-10-20): PDF ou imagem de até 5 MB, como no mural da
-- empresa. Guardados no banco, igual aos anexos das anotações.
create table if not exists company_commercial_information_attachments (
    id uuid primary key,
    record_id uuid not null references company_commercial_information(id) on delete cascade,
    file_name varchar(255),
    content_type varchar(100),
    file_size bigint,
    data bytea not null,
    created_at timestamp not null default now()
);

create index if not exists idx_commercial_information_attachments_record
    on company_commercial_information_attachments(record_id);
