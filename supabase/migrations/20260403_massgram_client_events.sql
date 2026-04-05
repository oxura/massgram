create table if not exists public.massgram_client_events (
    id bigserial primary key,
    event_id uuid not null unique,
    dedupe_key text not null unique,
    user_id bigint not null check (user_id > 0),
    event_type text not null,
    severity text not null check (severity in ('fatal', 'error', 'warning')),
    fingerprint text not null,
    title text,
    screen text,
    dialog_id bigint,
    app_version text,
    version_code bigint,
    build_channel text not null default 'stable' check (build_channel in ('stable', 'beta')),
    device_model text,
    os_version text,
    breadcrumbs_json jsonb not null default '[]'::jsonb,
    stacktrace_json jsonb not null default '[]'::jsonb,
    context_json jsonb not null default '{}'::jsonb,
    occurred_at timestamptz not null,
    received_at timestamptz not null default timezone('utc', now())
);

create index if not exists massgram_client_events_received_idx on public.massgram_client_events (received_at desc);
create index if not exists massgram_client_events_fingerprint_version_idx on public.massgram_client_events (fingerprint, version_code desc);
create index if not exists massgram_client_events_user_time_idx on public.massgram_client_events (user_id, received_at desc);
create index if not exists massgram_client_events_severity_time_idx on public.massgram_client_events (severity, received_at desc);

alter table public.massgram_client_events enable row level security;

revoke all on public.massgram_client_events from public;
revoke all on public.massgram_client_events from anon;
revoke all on public.massgram_client_events from authenticated;

create or replace function public.massgram_issue_summary()
returns table (
    crash_users_24h bigint,
    new_issues_24h bigint,
    top_fingerprint text,
    top_title text,
    top_count bigint
)
language sql
security definer
set search_path = public
as $$
    with last_24h as (
        select *
        from public.massgram_client_events
        where received_at >= timezone('utc', now()) - interval '24 hours'
    ),
    grouped as (
        select
            fingerprint,
            max(title) as title,
            count(*)::bigint as total_events
        from last_24h
        group by fingerprint
    ),
    top_issue as (
        select fingerprint, title, total_events
        from grouped
        order by total_events desc, fingerprint asc
        limit 1
    )
    select
        count(distinct user_id) filter (where severity = 'fatal')::bigint as crash_users_24h,
        count(distinct fingerprint)::bigint as new_issues_24h,
        (select fingerprint from top_issue),
        (select title from top_issue),
        coalesce((select total_events from top_issue), 0)::bigint
    from last_24h;
$$;

create or replace function public.massgram_issue_list(search_query text default '', max_rows integer default 100)
returns table (
    fingerprint text,
    title text,
    severity text,
    screen text,
    total_events bigint,
    unique_users bigint,
    last_occurred_at timestamptz,
    app_version text,
    build_channel text,
    device_model text
)
language sql
security definer
set search_path = public
as $$
    with filtered as (
        select *
        from public.massgram_client_events e
        where coalesce(search_query, '') = ''
           or lower(coalesce(e.title, '')) like '%' || lower(search_query) || '%'
           or lower(coalesce(e.fingerprint, '')) like '%' || lower(search_query) || '%'
           or lower(coalesce(e.screen, '')) like '%' || lower(search_query) || '%'
           or lower(coalesce(e.app_version, '')) like '%' || lower(search_query) || '%'
           or cast(e.user_id as text) = search_query
    ),
    grouped as (
        select
            fingerprint,
            count(*)::bigint as total_events,
            count(distinct user_id)::bigint as unique_users,
            max(received_at) as last_occurred_at
        from filtered
        group by fingerprint
    ),
    latest as (
        select distinct on (fingerprint)
            fingerprint,
            title,
            severity,
            screen,
            app_version,
            build_channel,
            device_model,
            received_at
        from filtered
        order by fingerprint, received_at desc
    )
    select
        grouped.fingerprint,
        latest.title,
        latest.severity,
        latest.screen,
        grouped.total_events,
        grouped.unique_users,
        grouped.last_occurred_at,
        latest.app_version,
        latest.build_channel,
        latest.device_model
    from grouped
    join latest using (fingerprint)
    order by grouped.last_occurred_at desc
    limit greatest(coalesce(max_rows, 100), 1);
$$;

create or replace function public.massgram_issue_detail_summary(target_fingerprint text)
returns table (
    fingerprint text,
    title text,
    severity text,
    screen text,
    total_events bigint,
    unique_users bigint,
    first_occurred_at timestamptz,
    last_occurred_at timestamptz,
    affected_versions text[],
    affected_devices text[],
    sample_stacktrace jsonb,
    sample_breadcrumbs jsonb,
    sample_context jsonb
)
language sql
security definer
set search_path = public
as $$
    with issue_events as (
        select *
        from public.massgram_client_events
        where fingerprint = target_fingerprint
    ),
    latest as (
        select *
        from issue_events
        order by received_at desc
        limit 1
    )
    select
        target_fingerprint,
        (select title from latest),
        (select severity from latest),
        (select screen from latest),
        count(*)::bigint as total_events,
        count(distinct user_id)::bigint as unique_users,
        min(received_at) as first_occurred_at,
        max(received_at) as last_occurred_at,
        array_remove(array_agg(distinct nullif(app_version, '')), null) as affected_versions,
        array_remove(array_agg(distinct nullif(device_model, '')), null) as affected_devices,
        coalesce((select stacktrace_json from latest), '[]'::jsonb),
        coalesce((select breadcrumbs_json from latest), '[]'::jsonb),
        coalesce((select context_json from latest), '{}'::jsonb)
    from issue_events;
$$;

create or replace function public.massgram_issue_detail_events(target_fingerprint text, max_rows integer default 20)
returns table (
    user_id bigint,
    username text,
    display_name text,
    app_version text,
    build_channel text,
    occurred_at timestamptz,
    dialog_id bigint
)
language sql
security definer
set search_path = public
as $$
    select
        e.user_id,
        c.username,
        c.display_name,
        e.app_version,
        e.build_channel,
        e.received_at as occurred_at,
        e.dialog_id
    from public.massgram_client_events e
    left join public.massgram_clients c on c.user_id = e.user_id
    where e.fingerprint = target_fingerprint
    order by e.received_at desc
    limit greatest(coalesce(max_rows, 20), 1);
$$;

revoke all on function public.massgram_issue_summary() from public;
revoke all on function public.massgram_issue_summary() from anon;
revoke all on function public.massgram_issue_summary() from authenticated;
revoke all on function public.massgram_issue_list(text, integer) from public;
revoke all on function public.massgram_issue_list(text, integer) from anon;
revoke all on function public.massgram_issue_list(text, integer) from authenticated;
revoke all on function public.massgram_issue_detail_summary(text) from public;
revoke all on function public.massgram_issue_detail_summary(text) from anon;
revoke all on function public.massgram_issue_detail_summary(text) from authenticated;
revoke all on function public.massgram_issue_detail_events(text, integer) from public;
revoke all on function public.massgram_issue_detail_events(text, integer) from anon;
revoke all on function public.massgram_issue_detail_events(text, integer) from authenticated;
