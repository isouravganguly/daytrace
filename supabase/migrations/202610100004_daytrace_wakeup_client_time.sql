-- A wake-up tapped offline should keep the tap time, not the sync time. The device sends when
-- it was tapped; it's trusted only within the last 12 hours (otherwise server time is used).
create or replace function daytrace.log_wakeup(p_token text, p_at timestamptz)
returns table(woke_at timestamptz, already_logged boolean)
language plpgsql
security definer
set search_path = daytrace, public
as $$
declare v_device uuid; v_existing timestamptz; v_at timestamptz;
begin
  select id into v_device from devices
  where token_hash = sha256(convert_to(p_token, 'utf8')) and revoked_at is null;
  if v_device is null then
    raise exception 'invalid token' using errcode = '28000';
  end if;

  v_at := case
    when p_at is not null and p_at between now() - interval '12 hours' and now() + interval '5 minutes' then p_at
    else now()
  end;

  select w.woke_at into v_existing from wakeups w
  where w.woke_at > v_at - interval '4 hours' and w.woke_at < v_at + interval '4 hours'
  order by w.woke_at limit 1;

  if v_existing is not null then
    return query select v_existing, true;
  else
    insert into wakeups (device_id, woke_at) values (v_device, v_at) returning wakeups.woke_at into v_existing;
    return query select v_existing, false;
  end if;
end $$;

revoke all on function daytrace.log_wakeup(text, timestamptz) from public, anon, authenticated;
