-- ============================================================
-- Iki Anti-Theft Tool
-- Remote Command Status
--
-- Server-side tracking for remote commands.
-- Clients do not access this table directly.
-- Writes and reads are performed through trusted Edge Functions.
-- ============================================================

create table public.commands (
    id uuid primary key,

    device_id text not null
        references public.devices(id),

    command text not null
        check (command in (
            'photo',
            'audio',
            'location',
            'usage'
        )),

    status text not null default 'pending'
        check (status in (
            'pending',
            'sent',
            'received',
            'running',
            'success',
            'error',
            'timeout'
        )),

    created_at timestamptz not null default now(),

    sent_at timestamptz,
    received_at timestamptz,
    started_at timestamptz,
    finished_at timestamptz,

    updated_at timestamptz not null default now(),

    error_code text,
    error_message text
);


-- ------------------------------------------------------------
-- Indexes
-- ------------------------------------------------------------

-- Command history for one device, newest first
create index idx_commands_device_created
    on public.commands (device_id, created_at desc);

-- Useful for finding pending/non-terminal commands
create index idx_commands_status
    on public.commands (status);


-- ------------------------------------------------------------
-- Row Level Security
-- ------------------------------------------------------------

alter table public.commands enable row level security;


-- ------------------------------------------------------------
-- Data API privileges
--
-- IMPORTANT:
-- No direct access for anon/authenticated clients.
-- Remote app ACKs and owner operations go through Edge Functions.
-- ------------------------------------------------------------

revoke all on table public.commands from public;
revoke all on table public.commands from anon;
revoke all on table public.commands from authenticated;

grant select, insert, update, delete
    on table public.commands
    to service_role;