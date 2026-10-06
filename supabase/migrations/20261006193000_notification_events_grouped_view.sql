-- ============================================================
-- Iki Anti-Theft Tool
-- Group raw notification "posted" callbacks for presentation.
--
-- Raw events remain unchanged in notification_events.
-- Multiple callbacks from the same Android notification that
-- occur within 250 ms are represented as one display event.
-- ============================================================

create or replace view public.notification_events_grouped
with (security_invoker = true)
as
with posted_events as (
    select
        id,
        device_id,
        app_name,
        package_name,
        notification_id,
        notification_key,
        event_timestamp,
        post_timestamp,
        created_at,

        lag(post_timestamp) over (
            partition by
                device_id,
                package_name,
                notification_id
            order by
                post_timestamp,
                event_timestamp,
                created_at
        ) as previous_post_timestamp

    from public.notification_events
    where event_type = 'posted'
),

group_boundaries as (
    select
        *,

        case
            when previous_post_timestamp is null then 1

            when post_timestamp - previous_post_timestamp > 250
                then 1

            else 0
        end as starts_new_group

    from posted_events
),

numbered_groups as (
    select
        *,

        sum(starts_new_group) over (
            partition by
                device_id,
                package_name,
                notification_id
            order by
                post_timestamp,
                event_timestamp,
                created_at
            rows between unbounded preceding and current row
        ) as notification_group

    from group_boundaries
)

select
    device_id,
    package_name,

    max(app_name) as app_name,

    notification_id,

    notification_group,

    min(event_timestamp) as event_timestamp,
    min(post_timestamp) as post_timestamp,

    count(*) as raw_event_count,

    min(created_at) as created_at

from numbered_groups

group by
    device_id,
    package_name,
    notification_id,
    notification_group;