-- Observações persistentes por entidade (cedente/sacado), keyed pelo documento normalizado.
-- A mesma observação aparece em todos os títulos do mesmo cedente/sacado, em qualquer dia;
-- só muda quando o analista a edita.
create table if not exists payment_place_party_notes (
    id uuid primary key,
    party_type varchar(10) not null,        -- CEDENTE | SACADO
    document varchar(20) not null,           -- somente dígitos (CNPJ normalizado)
    note text,
    updated_by_name varchar(255),
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint uq_party_note unique (party_type, document)
);

create index if not exists idx_party_note_lookup on payment_place_party_notes (party_type, document);
