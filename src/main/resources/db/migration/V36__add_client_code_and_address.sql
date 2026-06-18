alter table clients
    add column if not exists client_code varchar(20),
    add column if not exists address_zip varchar(10),
    add column if not exists address_street varchar(255),
    add column if not exists address_number varchar(20),
    add column if not exists address_complement varchar(120),
    add column if not exists address_district varchar(120),
    add column if not exists address_city varchar(120),
    add column if not exists address_uf varchar(2),
    add column if not exists latitude numeric(10, 6),
    add column if not exists longitude numeric(10, 6);

create index if not exists idx_clients_client_code on clients (client_code);
