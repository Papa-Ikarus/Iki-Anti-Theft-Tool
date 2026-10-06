-- ============================================================
-- Iki Anti-Theft Tool
-- Notification Events
--
-- Stores notification activity metadata from Iki devices.
-- No notification message content is stored.
-- Clients do not access this table directly.
-- Writes and reads are performed through trusted Edge Functions.
-- ============================================================

create table public.notification_events (
    id uuid primary key default gen_random_uuid(),

    device_id text not null
        references public.devices(id),

    app_name text not null,
    package_name text not null,

    event_type text not null
        check (event_type in (
            'posted',
            'removed'
        )),

    notification_id integer,
    notification_key text,

    event_timestamp bigint not null,
    post_timestamp bigint,

    created_at timestamptz not null default now()
);


-- ------------------------------------------------------------
-- Indexes
-- ------------------------------------------------------------

-- Notification history for one device, newest first
create index idx_notification_events_device_time
    on public.notification_events (device_id, event_timestamp desc);

-- Useful for filtering by application
create index idx_notification_events_device_package
    on public.notification_events (device_id, package_name);


-- ------------------------------------------------------------
-- Row Level Security
-- ------------------------------------------------------------

alter table public.notification_events enable row level security;


-- ------------------------------------------------------------
-- Data API privileges
--
-- IMPORTANT:
-- No direct access for anon/authenticated clients.
-- Device uploads and owner operations go through Edge Functions.
-- ------------------------------------------------------------

revoke all on table public.notification_events from public;
revoke all on table public.notification_events from anon;
revoke all on table public.notification_events from authenticated;

grant select, insert, update, delete
    on table public.notification_events
    to service_role;