create table if not exists system_settings (
    setting_key varchar(120) primary key,
    setting_value text,
    updated_by_user_id uuid,
    updated_by_name varchar(255),
    updated_by_email varchar(255),
    created_at timestamp not null default now(),
    updated_at timestamp not null default now(),
    constraint fk_system_settings_updated_by
        foreign key (updated_by_user_id) references users (id) on delete set null
);
