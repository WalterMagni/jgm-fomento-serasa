alter table payment_place_entries
    add column if not exists client_name varchar(500),
    add column if not exists client_document varchar(14);
