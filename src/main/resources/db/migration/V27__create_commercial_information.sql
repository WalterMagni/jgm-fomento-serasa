create table if not exists company_commercial_information (
    id uuid primary key,
    client_id uuid not null,
    author_user_id uuid,
    updated_by_user_id uuid,
    operation_date varchar(10),
    operation_type varchar(50),
    partner varchar(255),
    customer_since varchar(10),
    last_operation_date varchar(10),
    last_operation_value varchar(50),
    credit_limit varchar(50),
    duplicate_risk varchar(50),
    check_risk varchar(50),
    commission_risk varchar(50),
    overdue_date varchar(10),
    duplicate_due_date varchar(10),
    concentration varchar(50),
    vop varchar(50),
    punctual_percentage varchar(20),
    delay_percentage varchar(20),
    notary_percentage varchar(20),
    repurchase_percentage varchar(20),
    notes text,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now(),
    constraint fk_company_commercial_information_client
        foreign key (client_id) references clients (id) on delete cascade,
    constraint fk_company_commercial_information_author
        foreign key (author_user_id) references users (id) on delete set null,
    constraint fk_company_commercial_information_updated_by
        foreign key (updated_by_user_id) references users (id) on delete set null
);

create index if not exists idx_company_commercial_information_client_created_at
    on company_commercial_information (client_id, created_at desc);

create index if not exists idx_company_commercial_information_partner
    on company_commercial_information (partner);
