-- ─────────────────────────────────────────────────────────────────────────────
-- Iki Das Anti Theft Tool – Supabase Datenbankschema
-- Ausführen in: Supabase Console → SQL Editor
-- ─────────────────────────────────────────────────────────────────────────────

-- ── Tabellen ─────────────────────────────────────────────────────────────────

-- Registrierte Geräte (Zweithandy)
create table if not exists devices (
  id          text primary key,        -- z.B. "phone-1"
  fcm_token   text not null,
  last_seen   bigint,
  last_boot   bigint,
  created_at  timestamptz default now()
);

-- Standortpunkte
create table if not exists locations (
  id          uuid primary key default gen_random_uuid(),
  device_id   text references devices(id) on delete cascade,
  lat         double precision not null,
  lng         double precision not null,
  timestamp   bigint not null,
  created_at  timestamptz default now()
);

-- Tagesberichte (werden von Edge Function geschrieben)
create table if not exists reports (
  id               uuid primary key default gen_random_uuid(),
  date             date not null,
  device_id        text,
  location_count   int,
  first_timestamp  bigint,
  last_timestamp   bigint,
  first_lat        double precision,
  first_lng        double precision,
  last_lat         double precision,
  last_lng         double precision,
  maps_url         text,
  created_at       timestamptz default now()
);

-- Owner-Gerät (Web-Push-Token für Dashboard auf Handy 1)
create table if not exists owner (
  id            text primary key default 'dashboard',
  fcm_token     text,
  registered_at bigint,
  updated_at    timestamptz default now()
);

-- ── Indexes ───────────────────────────────────────────────────────────────────

create index if not exists locations_device_timestamp
  on locations(device_id, timestamp desc);

create index if not exists reports_device_date
  on reports(device_id, date desc);

-- ── Storage Buckets ───────────────────────────────────────────────────────────
-- In der Supabase Console unter Storage anlegen:
--   Bucket "photos"  – privat, max. 10 MB
--   Bucket "audio"   – privat, max. 20 MB

-- ── Row Level Security ────────────────────────────────────────────────────────

alter table devices   enable row level security;
alter table locations enable row level security;
alter table reports   enable row level security;
alter table owner     enable row level security;

-- devices: Gerät (anon) darf einfügen/aktualisieren;
--          authentifizierter Besitzer darf alles lesen
create policy "anon_upsert_devices"
  on devices for insert to anon with check (true);

create policy "anon_update_devices"
  on devices for update to anon using (true);

create policy "owner_select_devices"
  on devices for select to authenticated using (true);

-- locations: Gerät (anon) darf Standorte einfügen;
--            Besitzer darf lesen
create policy "anon_insert_locations"
  on locations for insert to anon
  with check (
    lat between -90 and 90 and
    lng between -180 and 180 and
    timestamp > 0
  );

create policy "owner_select_locations"
  on locations for select to authenticated using (true);

-- reports: nur Service Role (Edge Function) darf schreiben;
--          Besitzer darf lesen
create policy "owner_select_reports"
  on reports for select to authenticated using (true);

-- owner: nur authentifizierter Besitzer
create policy "owner_all"
  on owner for all to authenticated using (true) with check (true);

-- ── Storage Policies (nach Bucket-Erstellung in Console ausführen) ────────────
-- Fotos: Gerät (anon) darf hochladen; Besitzer liest/löscht
insert into storage.buckets (id, name, public) values ('photos', 'photos', false)
  on conflict do nothing;

insert into storage.buckets (id, name, public) values ('audio', 'audio', false)
  on conflict do nothing;

create policy "anon_upload_photos"
  on storage.objects for insert to anon
  with check (bucket_id = 'photos');

create policy "owner_select_photos"
  on storage.objects for select to authenticated
  using (bucket_id = 'photos');

create policy "owner_delete_photos"
  on storage.objects for delete to authenticated
  using (bucket_id = 'photos');

create policy "anon_upload_audio"
  on storage.objects for insert to anon
  with check (bucket_id = 'audio');

create policy "owner_select_audio"
  on storage.objects for select to authenticated
  using (bucket_id = 'audio');

create policy "owner_delete_audio"
  on storage.objects for delete to authenticated
  using (bucket_id = 'audio');
