begin;

alter table public.owner add column if not exists android_fcm_token text;
alter table public.owner enable row level security;

-- Android-Token schreibt nur die Edge Function nach verifizierter Besitzer-Anmeldung.
-- Tabellengrants würden die neue Spalte trotz Spaltengrants freigeben.
revoke insert, update, delete on public.owner from public, anon, authenticated;
revoke insert (android_fcm_token), update (android_fcm_token)
  on public.owner from public, anon, authenticated;

-- Bestehende Dashboard-Web-Registrierung bleibt mit ihren bisherigen Feldern nutzbar.
grant insert (id, fcm_token, registered_at, updated_at),
      update (id, fcm_token, registered_at, updated_at)
  on public.owner to authenticated;
grant select on public.owner to authenticated;
grant all on public.owner to service_role;

comment on column public.owner.android_fcm_token is
  'FCM-Token der separaten Iki-Besitzer-App; nur register-owner-push darf registrieren.';

commit;
