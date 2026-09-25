import { PGlite } from '@electric-sql/pglite';
import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
const db = new PGlite();
await db.exec(`
  create role anon; create role authenticated; create role service_role bypassrls;
  create table owner(id text primary key default 'dashboard', fcm_token text, registered_at bigint, updated_at timestamptz default now());
  alter table owner enable row level security;
  create policy owner_all on owner for all to authenticated using(true) with check(true);
  grant all on owner to anon, authenticated, service_role;
  insert into owner(id, fcm_token) values ('dashboard', 'original-web-token');
`);
const migration = readFileSync(new URL('../../supabase/migrations/20260923150620_owner_android_push.sql', import.meta.url), 'utf8');
await db.exec(migration);
await db.exec(migration);
await db.exec(`set role service_role; update owner set android_fcm_token='native-token' where id='dashboard'; reset role;`);
await db.exec(`set role authenticated;
  insert into owner(id,fcm_token,registered_at) values ('dashboard','updated-web-token',123)
  on conflict(id) do update set id=excluded.id,fcm_token=excluded.fcm_token,registered_at=excluded.registered_at;
  reset role;`);
for (const role of ['anon', 'authenticated']) {
  await db.exec('set role ' + role);
  await assert.rejects(db.exec("update owner set android_fcm_token='attacker' where id='dashboard'"));
  await assert.rejects(db.exec("insert into owner(id,android_fcm_token) values('other','attacker')"));
  await assert.rejects(db.exec("delete from owner where id='dashboard'"));
  await db.exec('reset role');
}
const result = await db.query("select fcm_token,android_fcm_token from owner where id='dashboard'");
assert.deepEqual(result.rows, [{ fcm_token: 'updated-web-token', android_fcm_token: 'native-token' }]);
await db.close();
console.log('Migration: wiederholbar; Web-Upsert funktioniert; native Spalte und Löschen für Clients gesperrt.');
