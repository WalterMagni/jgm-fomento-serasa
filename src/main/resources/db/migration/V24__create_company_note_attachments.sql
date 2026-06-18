create table if not exists company_note_attachments (
    id uuid primary key,
    note_id uuid not null references company_notes(id) on delete cascade,
    file_name varchar(255),
    content_type varchar(255),
    file_size bigint,
    data bytea not null,
    created_at timestamp not null default now()
);

create index if not exists idx_company_note_attachments_note_id on company_note_attachments(note_id);
