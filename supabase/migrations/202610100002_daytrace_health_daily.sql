-- Daytrace phase 2a: daily health aggregates from Health Connect (steps, walks, sleep, meals).
-- One row per device per day, upserted, so re-sending is safe. Same device-token auth as ingest().
create table if not exists daytrace.health_daily (
  device_id uuid not null references daytrace.devices (id) on delete cascade,
  day date not null,
  steps integer not null default 0 check (steps >= 0),
  walk_minutes integer not null default 0 check (walk_minutes >= 0),
  exercise_minutes integer not null default 0 check (exercise_minutes >= 0),
  sleep_minutes integer not null default 0 check (sleep_minutes >= 0),
  sleep_start timestamptz,
  sleep_end timestamptz,
  meals text[] not null default '{}',
  calories integer check (calories >= 0),
  source text not null default 'health_connect',
  updated_at timestamptz not null default now(),
  primary key (device_id, day)
);

-- Only reachable through the security-definer function below.
alter table daytrace.health_daily enable row level security;

create or replace function daytrace.ingest_health(p_token text, p_days jsonb)
returns integer
language plpgsql
security definer
set search_path = daytrace, public
as $$
declare v_device uuid; v_n int;
begin
  select id into v_device from devices
  where token_hash = sha256(convert_to(p_token, 'utf8')) and revoked_at is null;
  if v_device is null then
    raise exception 'invalid token' using errcode = '28000';
  end if;
  if jsonb_array_length(p_days) > 400 then
    raise exception 'too many days' using errcode = '22023';
  end if;

  insert into health_daily (device_id, day, steps, walk_minutes, exercise_minutes, sleep_minutes,
                            sleep_start, sleep_end, meals, calories, updated_at)
  select v_device, d.day, coalesce(d.steps, 0), coalesce(d.walk_minutes, 0), coalesce(d.exercise_minutes, 0),
         coalesce(d.sleep_minutes, 0), d.sleep_start, d.sleep_end, coalesce(d.meals, '{}'), d.calories, now()
  from jsonb_to_recordset(p_days) as d(day date, steps int, walk_minutes int, exercise_minutes int,
       sleep_minutes int, sleep_start timestamptz, sleep_end timestamptz, meals text[], calories int)
  where d.day is not null
  on conflict (device_id, day) do update set
    steps = excluded.steps,
    walk_minutes = excluded.walk_minutes,
    exercise_minutes = excluded.exercise_minutes,
    sleep_minutes = excluded.sleep_minutes,
    sleep_start = excluded.sleep_start,
    sleep_end = excluded.sleep_end,
    meals = excluded.meals,
    calories = excluded.calories,
    updated_at = now();
  get diagnostics v_n = row_count;

  update devices set last_seen_at = now() where id = v_device;
  return v_n;
end $$;

revoke all on function daytrace.ingest_health(text, jsonb) from public, anon, authenticated;
