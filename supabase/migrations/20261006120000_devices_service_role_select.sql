-- ============================================================
-- Iki Anti-Theft Tool
-- Allow trusted Edge Functions to authenticate devices.
--
-- Required by notification-event to verify the per-device
-- upload token hash stored in public.devices.
-- ============================================================

grant select
    on table public.devices
    to service_role;