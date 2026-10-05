-- v2: two-way sync, LEARNING kind, AI title/tags, soft delete, server-owned change cursor.

-- NOTE is replaced by LEARNING.
alter table public.thoughts drop constraint if exists thoughts_kind_check;
update public.thoughts set kind = 'LEARNING' where kind = 'NOTE';
alter table public.thoughts
  add constraint thoughts_kind_check check (kind in ('THOUGHT', 'LEARNING', 'TODO'));

alter table public.thoughts
  add column if not exists kind_source text not null default 'AUTO'
    check (kind_source in ('AUTO', 'USER')),
  add column if not exists input text not null default 'TEXT'
    check (input in ('TEXT', 'VOICE')),
  add column if not exists title text,
  add column if not exists tags text[] not null default '{}',
  add column if not exists deleted_at timestamptz,
  -- Set by the server when AI enrichment finished; null means "needs enrichment".
  add column if not exists enriched_at timestamptz,
  -- Set by the server on every write. Devices pull changes newer than their cursor.
  add column if not exists server_updated_at timestamptz not null default now();

create or replace function public.thoughts_touch()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.server_updated_at := now();
  -- Edited text needs fresh AI title, tags and embedding.
  if tg_op = 'UPDATE' and new.body is distinct from old.body then
    new.enriched_at := null;
  end if;
  return new;
end;
$$;

drop trigger if exists thoughts_touch on public.thoughts;
create trigger thoughts_touch
before insert or update on public.thoughts
for each row execute function public.thoughts_touch();

create index if not exists thoughts_user_server_updated_idx
on public.thoughts (user_id, server_updated_at);

create index if not exists thoughts_needs_enrichment_idx
on public.thoughts (user_id, created_at)
where enriched_at is null and deleted_at is null;

-- The old partial index hid completed items; lists now filter on the device instead.
drop index if exists public.thoughts_active_user_created_idx;

-- Semantic search now covers done todos too, but never deleted rows.
drop function if exists public.match_thoughts(extensions.vector, integer);
create function public.match_thoughts(
  query_embedding extensions.vector(768),
  match_count integer default 12
)
returns table (id uuid, similarity double precision)
language sql stable
security invoker
set search_path = ''
as $$
  select
    thoughts.id,
    1 - (thoughts.embedding OPERATOR(extensions.<=>) query_embedding) as similarity
  from public.thoughts
  where thoughts.user_id = (select auth.uid())
    and thoughts.deleted_at is null
    and thoughts.embedding is not null
  order by thoughts.embedding OPERATOR(extensions.<=>) query_embedding
  limit least(match_count, 50);
$$;

grant execute on function public.match_thoughts(extensions.vector, integer) to authenticated;
