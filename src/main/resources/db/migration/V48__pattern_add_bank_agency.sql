-- Padrão aprendido agora por (cedente × sacado × banco × agência). Banco/agência mudam o
-- contexto: a mesma dupla cedente×sacado com agência DIFERENTE não herda o padrão consolidado
-- (ex.: Zilhões / On The Table). bank_code/agency_code vêm do retorno bancário (PDF) no import,
-- presentes mesmo quando o Bacen não resolve o endereço. Ausentes → bucket próprio (''), sem
-- herança cruzada. Códigos guardados como vêm (NÃO tira zero à esquerda da agência).
alter table payment_place_patterns
    add column if not exists bank_code varchar(20) not null default '',
    add column if not exists agency_code varchar(20) not null default '',
    add column if not exists bank_name varchar(255);

-- Troca a chave única de (cedente,sacado) para (cedente,sacado,banco,agência).
alter table payment_place_patterns drop constraint if exists uq_payment_place_pattern;
drop index if exists idx_payment_place_pattern_lookup;

-- Rebuild completo a partir da fonte de verdade (decisões), agora agrupando por banco/agência.
-- Os contadores antigos agregavam todas as agências → estavam "generalizando demais". Recompila.
-- (locks são override manual recente — ~0 em produção; o analista re-trava se precisar.)
delete from payment_place_patterns;

insert into payment_place_patterns (
    id, client_document, payer_document, bank_code, agency_code, bank_name,
    cedente_count, sacado_count, inconclusivo_count, total_count,
    last_decision, last_decided_at, locked, created_at, updated_at
)
select
    gen_random_uuid(),
    regexp_replace(e.client_document, '\D', '', 'g') as ced,
    regexp_replace(e.payer_document, '\D', '', 'g') as pay,
    coalesce(e.bank_code, '') as bank,
    coalesce(e.agency_code, '') as agency,
    (array_agg(e.bank_name order by e.decided_at desc nulls last)
        filter (where e.bank_name is not null))[1],
    count(*) filter (where e.analyst_decision = 'CEDENTE'),
    count(*) filter (where e.analyst_decision = 'SACADO'),
    count(*) filter (where e.analyst_decision = 'INCONCLUSIVO'),
    count(*),
    (array_agg(e.analyst_decision order by e.decided_at desc nulls last))[1],
    max(e.decided_at),
    false, now(), now()
from payment_place_entries e
where e.analyst_decision is not null
  and e.client_document is not null and regexp_replace(e.client_document, '\D', '', 'g') <> ''
  and e.payer_document is not null and regexp_replace(e.payer_document, '\D', '', 'g') <> ''
group by ced, pay, bank, agency;

alter table payment_place_patterns
    add constraint uq_payment_place_pattern
        unique (client_document, payer_document, bank_code, agency_code);

create index if not exists idx_payment_place_pattern_lookup
    on payment_place_patterns (client_document, payer_document, bank_code, agency_code);
