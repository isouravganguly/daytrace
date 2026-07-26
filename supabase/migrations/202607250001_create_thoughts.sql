create extension if not exists vector with schema extensions;

create table public.thoughts (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  body text not null check (char_length(body) > 0),
  kind text not null check (kind in ('NOTE', 'TODO', 'THOUGHT')),
  space text not null check (space in ('PERSONAL', 'WORK')),
  created_at timestamptz not null,
  updated_at timestamptz not null,
  completed_at timestamptz,
  embedding extensions.vector(768)
);

alter table public.thoughts enable row level security;

create policy "Users own their thoughts"
on public.thoughts for all
to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

-- Covers the auth.users foreign key and ownership lookups, including cascades.
create index thoughts_user_id_idx
on public.thoughts (user_id);

-- The app's lists intentionally hide completed items.
create index thoughts_active_user_created_idx
on public.thoughts (user_id, created_at desc)
where completed_at is null;

create index thoughts_embedding_idx
on public.thoughts using hnsw (embedding vector_cosine_ops);

create or replace function public.match_thoughts(
  query_embedding extensions.vector(768),
  match_count integer default 10
)
returns table (
  id uuid,
  body text,
  kind text,
  space text,
  similarity double precision
)
language sql stable
security invoker
set search_path = ''
as $$
  select
    thoughts.id,
    thoughts.body,
    thoughts.kind,
    thoughts.space,
    1 - (thoughts.embedding OPERATOR(extensions.<=>) query_embedding) as similarity
  from public.thoughts
  where thoughts.user_id = (select auth.uid())
    and thoughts.completed_at is null
    and thoughts.embedding is not null
  order by thoughts.embedding OPERATOR(extensions.<=>) query_embedding
  limit least(match_count, 50);
$$;

revoke all on table public.thoughts from anon;
grant select, insert, update, delete on table public.thoughts to authenticated;
grant execute on function public.match_thoughts(extensions.vector, integer)
to authenticated;
