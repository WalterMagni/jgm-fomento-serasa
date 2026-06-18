alter table payment_place_entries
    add column if not exists ai_analysis jsonb,
    add column if not exists ai_analyzed_at timestamp;
