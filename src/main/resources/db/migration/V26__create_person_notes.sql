create table if not exists person_notes (
    id uuid primary key,
    cpf varchar(11) not null,
    author_user_id uuid,
    author_name varchar(255) not null,
    author_email varchar(255) not null,
    content text not null,
    parent_note_id uuid,
    attachment_file_name varchar(255),
    attachment_content_type varchar(255),
    attachment_size bigint,
    attachment_data bytea,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);

create index if not exists idx_person_notes_cpf_created_at
    on person_notes (cpf, created_at desc);

create index if not exists idx_person_notes_author_user_id
    on person_notes (author_user_id);

create index if not exists idx_person_notes_parent_note_id
    on person_notes (parent_note_id);

do $$
begin
    if not exists (
        select 1
        from information_schema.table_constraints
        where table_name = 'person_notes'
          and constraint_name = 'fk_person_notes_parent'
    ) then
        alter table person_notes
            add constraint fk_person_notes_parent
            foreign key (parent_note_id) references person_notes (id) on delete set null;
    end if;
end $$;

create table if not exists person_note_attachments (
    id uuid primary key,
    note_id uuid not null references person_notes(id) on delete cascade,
    file_name varchar(255),
    content_type varchar(255),
    file_size bigint,
    data bytea not null,
    created_at timestamp not null default now()
);

create index if not exists idx_person_note_attachments_note_id
    on person_note_attachments(note_id);
