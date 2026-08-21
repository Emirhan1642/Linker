create table if not exists public.user_push_tokens (
  id uuid primary key default gen_random_uuid(),
  user_id text not null,
  fcm_token text not null,
  platform text,
  updated_at timestamptz default now()
);

create unique index if not exists user_push_tokens_user_token_idx
  on public.user_push_tokens (user_id, fcm_token);

create index if not exists idx_user_push_tokens_user_id
  on public.user_push_tokens (user_id);

alter table public.user_push_tokens enable row level security;

drop policy if exists "Users can read their tokens" on public.user_push_tokens;
drop policy if exists "Users can insert their tokens" on public.user_push_tokens;
drop policy if exists "Users can update their tokens" on public.user_push_tokens;
drop policy if exists "Users can delete their tokens" on public.user_push_tokens;

create policy "Users can read their tokens"
  on public.user_push_tokens
  for select
  using (auth.uid()::text = user_id);

create policy "Users can insert their tokens"
  on public.user_push_tokens
  for insert
  with check (auth.uid()::text = user_id);

create policy "Users can update their tokens"
  on public.user_push_tokens
  for update
  using (auth.uid()::text = user_id)
  with check (auth.uid()::text = user_id);

create policy "Users can delete their tokens"
  on public.user_push_tokens
  for delete
  using (auth.uid()::text = user_id);
