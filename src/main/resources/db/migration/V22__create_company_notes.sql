create table if not exists company_notes (
    id uuid primary key,
    client_id uuid not null,
    author_user_id uuid,
    author_name varchar(255) not null,
    author_email varchar(255) not null,
    content text not null,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now(),
    constraint fk_company_notes_client
        foreign key (client_id) references clients (id) on delete cascade,
    constraint fk_company_notes_author
        foreign key (author_user_id) references users (id) on delete set null
);

create index if not exists idx_company_notes_client_created_at
    on company_notes (client_id, created_at desc);

create index if not exists idx_company_notes_author_user_id
    on company_notes (author_user_id);
