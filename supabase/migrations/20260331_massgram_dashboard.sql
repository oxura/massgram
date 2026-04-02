create table if not exists public.massgram_clients (
    user_id bigint primary key check (user_id > 0),
    username text,
    first_name text,
    last_name text,
    display_name text not null,
    app_version text,
    version_code integer,
    package_name text,
    build_channel text not null default 'stable' check (build_channel in ('stable', 'beta')),
    device_model text,
    os_version text,
    locale text,
    first_seen_at timestamptz not null default timezone('utc', now()),
    last_seen_at timestamptz not null default timezone('utc', now()),
    last_heartbeat_at timestamptz not null default timezone('utc', now())
);

create index if not exists massgram_clients_last_seen_idx on public.massgram_clients (last_seen_at desc);
create index if not exists massgram_clients_username_idx on public.massgram_clients (lower(username));
create index if not exists massgram_clients_display_name_idx on public.massgram_clients (lower(display_name));

alter table public.massgram_clients enable row level security;

revoke all on public.massgram_clients from public;
revoke all on public.massgram_clients from anon;
revoke all on public.massgram_clients from authenticated;

create or replace function public.massgram_dashboard_summary()
returns table (
    total_users bigint,
    active_users bigint,
    offline_users bigint,
    beta_users bigint
)
language sql
security definer
set search_path = public
as $$
    select
        count(*)::bigint as total_users,
        count(*) filter (where last_seen_at >= timezone('utc', now()) - interval '2 minutes')::bigint as active_users,
        count(*) filter (where last_seen_at < timezone('utc', now()) - interval '2 minutes')::bigint as offline_users,
        count(*) filter (where build_channel = 'beta')::bigint as beta_users
    from public.massgram_clients;
$$;

revoke all on function public.massgram_dashboard_summary() from public;
revoke all on function public.massgram_dashboard_summary() from anon;
revoke all on function public.massgram_dashboard_summary() from authenticated;
