-- ─────────────────────────────────────────────────────────────────────────────
-- Migration 002: App-Nutzungsstatistiken
-- Ausführen in: Supabase Console → SQL Editor
-- ─────────────────────────────────────────────────────────────────────────────

-- Tabelle für tägliche App-Nutzungsdaten
create table if not exists usage_logs (
  id              uuid primary key default gen_random_uuid(),
  device_id       text not null,
  date            date not null,
  app_package     text not null,        -- z.B. "com.whatsapp"
  app_name        text,                 -- z.B. "WhatsApp"
  total_time_ms   bigint not null,      -- Nutzungsdauer in Millisekunden
  last_used       bigint,               -- Unix-Timestamp letzter Aufruf
  created_at      timestamptz default now(),
  unique(device_id, date, app_package) -- pro Tag + Gerät + App nur ein Eintrag
);

create index if not exists usage_logs_device_date
  on usage_logs(device_id, date desc);

-- RLS: Gerät (anon) darf einfügen; Besitzer liest
alter table usage_logs enable row level security;

create policy "anon_insert_usage_logs"
  on usage_logs for insert to anon
  with check (total_time_ms > 0);

create policy "owner_select_usage_logs"
  on usage_logs for select to authenticated using (true);
