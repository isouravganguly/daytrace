-- Devices register themselves after the owner signs in, so the user never handles tokens.
create table if not exists daytrace.owners (
  user_id uuid primary key references auth.users (id) on delete cascade,
  created_at timestamptz not null default now()
);
alter table daytrace.owners enable row level security;

alter table daytrace.devices add column if not exists user_id uuid references auth.users (id) on delete set null;
alter table daytrace.devices add column if not exists install_id text;
create unique index if not exists devices_install_id_key on daytrace.devices (install_id) where install_id is not null;

-- Creates this install's device (or rotates its token) and returns a fresh token.
create or replace function daytrace.register_install(
  p_user uuid, p_install_id text, p_name text, p_platform text, p_kind text
) returns text
language plpgsql
security definer
set search_path = daytrace, public
as $$
declare v_token text;
begin
  if not exists (select 1 from owners where user_id = p_user) then
    raise exception 'not an owner' using errcode = '42501';
  end if;
  if coalesce(length(p_install_id), 0) < 16 then
    raise exception 'bad install id' using errcode = '22023';
  end if;
  v_token := replace(gen_random_uuid()::text || gen_random_uuid()::text, '-', '');
  insert into devices (name, platform, kind, token_hash, user_id, install_id)
  values (p_name, p_platform, p_kind, sha256(convert_to(v_token, 'utf8')), p_user, p_install_id)
  on conflict (install_id) where install_id is not null do update set
    name = excluded.name,
    platform = excluded.platform,
    kind = excluded.kind,
    token_hash = excluded.token_hash,
    user_id = excluded.user_id,
    revoked_at = null;
  return v_token;
end $$;

revoke all on function daytrace.register_install(uuid, text, text, text, text) from public, anon, authenticated;

-- Then make your account the owner (once, in the SQL editor):
--   insert into daytrace.owners (user_id) select id from auth.users where email = '<your email>';
