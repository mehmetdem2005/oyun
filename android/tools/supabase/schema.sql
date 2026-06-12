-- Kayıp Krallık çok-oyunculu-lite şeması (Supabase SQL Editor'da çalıştır)
create table if not exists kk_presence (
  id   text primary key,
  name text not null,
  x    integer not null default 0,
  y    integer not null default 0,
  ts   bigint  not null
);
create table if not exists kk_chat (
  id   bigserial primary key,
  who  text not null,
  text text not null,
  ts   bigint not null
);
alter table kk_presence enable row level security;
alter table kk_chat     enable row level security;
create policy "anon insert" on kk_presence for insert to anon with check (true);
create policy "anon update" on kk_presence for update to anon using (true);
create policy "anon select" on kk_presence for select to anon using (true);
create policy "anon insert" on kk_chat     for insert to anon with check (true);
create policy "anon select" on kk_chat     for select to anon using (true);
