create table if not exists company_document_roots (
    id uuid primary key,
    client_id uuid not null unique,
    root_path text not null,
    mapped_by_user_id uuid,
    mapped_by_name varchar(255),
    mapped_by_email varchar(255),
    created_at timestamp not null default now(),
    updated_at timestamp not null default now(),
    constraint fk_company_document_roots_client
        foreign key (client_id) references clients (id) on delete cascade,
    constraint fk_company_document_roots_user
        foreign key (mapped_by_user_id) references users (id) on delete set null
);

create index if not exists idx_company_document_roots_client_id
    on company_document_roots (client_id);
