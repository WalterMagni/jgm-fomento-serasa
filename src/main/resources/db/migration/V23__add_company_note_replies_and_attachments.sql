alter table company_notes
    add column if not exists parent_note_id uuid,
    add column if not exists attachment_file_name varchar(255),
    add column if not exists attachment_content_type varchar(255),
    add column if not exists attachment_size bigint,
    add column if not exists attachment_data bytea;

do $$
begin
    if not exists (
        select 1
        from information_schema.table_constraints
        where table_name = 'company_notes'
          and constraint_name = 'fk_company_notes_parent'
    ) then
        alter table company_notes
            add constraint fk_company_notes_parent
            foreign key (parent_note_id) references company_notes (id) on delete set null;
    end if;
end $$;

create index if not exists idx_company_notes_parent_note_id
    on company_notes (parent_note_id);
