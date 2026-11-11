-- BrickTimer community: accounts, public profiles with stats, and everyone's builds.
-- Run once in the Supabase dashboard: SQL Editor -> New query -> paste -> Run.

-- One public profile per account. The app keeps the stats up to date after every change.
create table public.profiles (
  id          uuid primary key references auth.users on delete cascade,
  username    text not null check (username ~ '^[A-Za-z0-9_.-]{3,20}$'),
  total_sec   bigint  not null default 0,
  sets_done   int     not null default 0,
  bricks_done int     not null default 0,
  best_spb    real,                         -- fastest seconds per brick over finished sets
  stars       int     not null default 0,
  platinum    int     not null default 0,
  titles      int     not null default 0,
  rank_title  text,
  evening     int     not null default 0,
  morning     int     not null default 0,
  speed       int     not null default 0,
  updated_at  timestamptz not null default now()
);
create unique index profiles_username_key on public.profiles (lower(username));

-- Every set in someone's portfolio, with its build time.
create table public.builds (
  user_id      uuid not null references public.profiles on delete cascade,
  set_num      text not null,
  name         text not null,
  img_url      text,
  num_parts    int  not null default 0,
  year         int  not null default 0,
  theme_id     int  not null default 0,
  theme_name   text not null default '',
  status       text not null check (status in ('PLANNED', 'CURRENT', 'DONE')),
  total_sec    bigint not null default 0,
  sessions     int    not null default 0,
  completed_at timestamptz,
  primary key (user_id, set_num)
);

-- Everyone may read; only the owner may write their own rows.
alter table public.profiles enable row level security;
alter table public.builds   enable row level security;

create policy "profiles readable" on public.profiles for select using (true);
create policy "own profile"      on public.profiles for update using (auth.uid() = id) with check (auth.uid() = id);

create policy "builds readable"  on public.builds for select using (true);
create policy "own builds"       on public.builds for all    using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- The username comes along with sign-up; a taken or invalid name makes the sign-up fail.
create function public.handle_new_user() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  insert into public.profiles (id, username) values (new.id, new.raw_user_meta_data ->> 'username');
  return new;
end;
$$;

create trigger on_auth_user_created after insert on auth.users
  for each row execute function public.handle_new_user();

-- Lets someone delete their own account from the app (required by Google Play).
create function public.delete_account() returns void
language sql security definer set search_path = '' as $$
  delete from auth.users where id = auth.uid();
$$;

revoke execute on function public.delete_account() from anon, public;
grant execute on function public.delete_account() to authenticated;

-- ---------------------------------------------------------------------------
-- Private backup of each builder's full portfolio, every session included, so a
-- reinstall or new phone gets everything back after signing in. Owner-only: unlike
-- profiles and builds, nobody else can read it.
-- Existing project? Run only this part in the SQL Editor.
create table public.backups (
  user_id    uuid primary key references auth.users on delete cascade,
  data       jsonb not null,
  updated_at timestamptz not null default now()
);

alter table public.backups enable row level security;

create policy "own backup" on public.backups for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
