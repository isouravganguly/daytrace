-- Daytrace phase 1: captures can be desires (things wanted someday), alongside thoughts,
-- learnings and todos. Additive: existing rows and older app versions are unaffected.
alter table public.thoughts drop constraint if exists thoughts_kind_check;
alter table public.thoughts
  add constraint thoughts_kind_check check (kind in ('THOUGHT', 'LEARNING', 'TODO', 'DESIRE'));
