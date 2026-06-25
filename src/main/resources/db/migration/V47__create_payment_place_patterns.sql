-- Padrões aprendidos por par (cedente × sacado). Cada decisão do analista alimenta a
-- contagem do par; o scorer lê para sugerir nas importações futuras ("aprende com o tempo").
-- Documentos guardados SÓ dígitos (payer_document costuma vir mascarado do PDF).
create table if not exists payment_place_patterns (
    id uuid primary key,
    client_document varchar(20) not null,      -- cedente, só dígitos
    payer_document varchar(20) not null,       -- sacado, só dígitos
    cedente_count integer not null default 0,
    sacado_count integer not null default 0,
    inconclusivo_count integer not null default 0,
    total_count integer not null default 0,
    last_decision varchar(30),
    last_decided_at timestamp,
    locked boolean not null default false,     -- trava manual do analista (override forte)
    locked_decision varchar(30),               -- decisão fixada quando locked
    locked_by_name varchar(255),
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint uq_payment_place_pattern unique (client_document, payer_document)
);

create index if not exists idx_payment_place_pattern_lookup
    on payment_place_patterns (client_document, payer_document);

-- Snapshot do padrão no momento do score do lançamento (denormalizado, sem N+1 na listagem).
alter table payment_place_entries
    add column if not exists learned_pattern_decision varchar(30),
    add column if not exists learned_pattern_count integer,
    add column if not exists learned_pattern_total integer;

-- Backfill: compila o histórico de decisões já existente em padrões (par com ambos os documentos).
insert into payment_place_patterns (
    id, client_document, payer_document,
    cedente_count, sacado_count, inconclusivo_count, total_count,
    last_decision, last_decided_at, locked, created_at, updated_at
)
select
    gen_random_uuid(),
    regexp_replace(e.client_document, '\D', '', 'g') as ced,
    regexp_replace(e.payer_document, '\D', '', 'g') as pay,
    count(*) filter (where e.analyst_decision = 'CEDENTE'),
    count(*) filter (where e.analyst_decision = 'SACADO'),
    count(*) filter (where e.analyst_decision = 'INCONCLUSIVO'),
    count(*),
    (array_agg(e.analyst_decision order by e.decided_at desc nulls last))[1],
    max(e.decided_at),
    false,
    now(),
    now()
from payment_place_entries e
where e.analyst_decision is not null
  and e.client_document is not null and regexp_replace(e.client_document, '\D', '', 'g') <> ''
  and e.payer_document is not null and regexp_replace(e.payer_document, '\D', '', 'g') <> ''
group by ced, pay;
