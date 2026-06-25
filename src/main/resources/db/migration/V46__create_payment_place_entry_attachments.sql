-- Anexos por título (comprovante de pagamento, endereço, txt etc.), guardados como bytea.
-- Limite de 5 MB validado na aplicação.
create table if not exists payment_place_entry_attachments (
    id uuid primary key,
    entry_id uuid not null references payment_place_entries(id) on delete cascade,
    file_name varchar(255),
    content_type varchar(150),
    file_size bigint,
    data bytea not null,
    created_by_name varchar(255),
    created_at timestamp not null
);

create index if not exists idx_pp_entry_attachment_entry on payment_place_entry_attachments (entry_id);
