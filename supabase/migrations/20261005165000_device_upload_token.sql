-- ============================================================
-- Iki Anti-Theft Tool
-- Device Upload Authentication
--
-- Stores only the SHA-256 hash of the per-device upload token.
-- The plaintext token must never be stored in the database.
-- ============================================================

alter table public.devices
    add column if not exists upload_token_hash text;

alter table public.devices
    add constraint devices_upload_token_hash_format
    check (
        upload_token_hash is null
        or upload_token_hash ~ '^[0-9a-f]{64}$'
    );

create unique index if not exists idx_devices_upload_token_hash
    on public.devices (upload_token_hash)
    where upload_token_hash is not null;