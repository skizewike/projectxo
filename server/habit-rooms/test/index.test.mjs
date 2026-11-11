import { test } from "node:test";
import assert from "node:assert/strict";
import { DatabaseSync } from "node:sqlite";
import { readFileSync } from "node:fs";
import { handle, cleanup, normalizeCode } from "../src/index.js";

const SCHEMA = readFileSync(new URL("../schema.sql", import.meta.url), "utf8");
const NOW = Date.UTC(2026, 10, 11, 12, 0, 0); // 2026-11-11
const DAY = 24 * 60 * 60 * 1000;

/** Маленькая подмена D1 на встроенной SQLite Node: тот же прикладной интерфейс prepare/bind/first/all/run/batch. */
class Stmt {
  constructor(db, sql, args = []) {
    this.db = db;
    this.sql = sql;
    this.args = args;
  }
  bind(...args) {
    return new Stmt(this.db, this.sql, args);
  }
  async first() {
    return this.db.prepare(this.sql).get(...this.args) ?? null;
  }
  async all() {
    return { results: this.db.prepare(this.sql).all(...this.args).map((r) => ({ ...r })) };
  }
  async run() {
    const r = this.db.prepare(this.sql).run(...this.args);
    return { meta: { changes: r.changes } };
  }
}
class FakeD1 {
  constructor() {
    this.db = new DatabaseSync(":memory:");
    this.db.exec(SCHEMA);
  }
  prepare(sql) {
    return new Stmt(this.db, sql);
  }
  async batch(stmts) {
    const out = [];
    for (const s of stmts) out.push(await s.run());
    return out;
  }
}

const newEnv = (extra = {}) => ({ DB: new FakeD1(), ...extra });

const call = (env, method, path, { body, headers = {}, now = NOW, ip = "1.1.1.1" } = {}) =>
  handle(
    new Request("https://rooms.test" + path, {
      method,
      headers: { "content-type": "application/json", "cf-connecting-ip": ip, ...headers },
      body: body === undefined ? undefined : JSON.stringify(body),
    }),
    env,
    now,
  );

const auth = (m) => ({ "x-member-id": m.memberId, "x-secret": m.secret });
const day = (offset) => new Date(NOW + offset * DAY).toISOString().slice(0, 10);

async function makeRoom(env, extra = {}) {
  const res = await call(env, "POST", "/v1/rooms", { body: { title: "Прогулка 20 минут", freqMask: 127, nick: "Вы", ...extra } });
  assert.equal(res.status, 201);
  return res.json();
}
async function joinRoom(env, code, nick = "Алекс", ip = "2.2.2.2") {
  const res = await call(env, "POST", "/v1/join", { body: { code, nick }, ip });
  return { res, body: res.status === 200 ? await res.clone().json() : null };
}

test("health", async () => {
  const res = await call(newEnv(), "GET", "/health");
  assert.equal(res.status, 200);
  assert.deepEqual(await res.json(), { ok: true });
});

test("создание комнаты: код из 6 знаков, секрет не хранится открытым", async () => {
  const env = newEnv();
  const room = await makeRoom(env);
  assert.match(room.code, /^[A-HJ-NP-Z2-9]{6}$/);
  assert.match(room.secret, /^[0-9a-f]{64}$/);
  assert.equal(room.codeExpires, NOW + 7 * DAY);
  const row = env.DB.db.prepare("SELECT secret_hash FROM member").get();
  assert.notEqual(row.secret_hash, room.secret);
  assert.equal(row.secret_hash.length, 64);
});

test("плохой ввод при создании", async () => {
  const env = newEnv();
  for (const body of [
    { title: "", freqMask: 127, nick: "a" },
    { title: "x", freqMask: 0, nick: "a" },
    { title: "x", freqMask: 128, nick: "a" },
    { title: "x", freqMask: 127, nick: "" },
    { title: "x".repeat(61), freqMask: 127, nick: "a" },
    { title: "x", freqMask: 127, nick: "n".repeat(21) },
    { title: 5, freqMask: 127, nick: "a" },
  ]) {
    const res = await call(env, "POST", "/v1/rooms", { body });
    assert.equal(res.status, 400, JSON.stringify(body));
  }
});

test("вход по коду: любой регистр и дефис, код одноразовый", async () => {
  const env = newEnv();
  const room = await makeRoom(env);
  const spaced = room.code.slice(0, 3).toLowerCase() + "-" + room.code.slice(3);
  const { res, body } = await joinRoom(env, spaced);
  assert.equal(res.status, 200);
  assert.equal(body.roomId, room.roomId);
  assert.equal(body.title, "Прогулка 20 минут");
  assert.equal(body.freqMask, 127);
  assert.equal(body.members.length, 2);
  const again = await joinRoom(env, room.code, "Третий", "3.3.3.3");
  assert.equal(again.res.status, 404);
});

test("неверный и просроченный код", async () => {
  const env = newEnv();
  const room = await makeRoom(env);
  assert.equal((await joinRoom(env, "AAAAAA")).res.status, 404);
  const late = await call(env, "POST", "/v1/join", { body: { code: room.code, nick: "Алекс" }, now: NOW + 8 * DAY });
  assert.equal(late.status, 404);
  assert.equal((await call(env, "POST", "/v1/join", { body: { code: "123", nick: "Алекс" } })).status, 400);
});

test("комната на двоих: третьему нельзя", async () => {
  const env = newEnv();
  const room = await makeRoom(env);
  const first = await joinRoom(env, room.code);
  assert.equal(first.res.status, 200);
  const code = await call(env, "POST", `/v1/rooms/${room.roomId}/code`, { headers: auth(room) });
  assert.equal(code.status, 409);
});

test("перебор кодов: 20 попыток в час с адреса", async () => {
  const env = newEnv();
  for (let i = 0; i < 20; i++) assert.equal((await joinRoom(env, "AAAAAA", "x", "9.9.9.9")).res.status, 404);
  assert.equal((await joinRoom(env, "AAAAAA", "x", "9.9.9.9")).res.status, 429);
  assert.equal((await joinRoom(env, "AAAAAA", "x", "8.8.8.8")).res.status, 404);
  const later = await call(env, "POST", "/v1/join", { body: { code: "AAAAAA", nick: "x" }, ip: "9.9.9.9", now: NOW + 2 * 60 * 60 * 1000 });
  assert.equal(later.status, 404);
});

test("комнат с одного адреса не больше 10 в сутки", async () => {
  const env = newEnv();
  for (let i = 0; i < 10; i++) await makeRoom(env);
  const res = await call(env, "POST", "/v1/rooms", { body: { title: "x", freqMask: 127, nick: "a" } });
  assert.equal(res.status, 429);
});

test("без секрета и чужим секретом — 401", async () => {
  const env = newEnv();
  const room = await makeRoom(env);
  const other = await makeRoom(env);
  assert.equal((await call(env, "GET", `/v1/rooms/${room.roomId}`)).status, 401);
  assert.equal((await call(env, "GET", `/v1/rooms/${room.roomId}`, { headers: auth(other) })).status, 401);
  assert.equal((await call(env, "GET", `/v1/rooms/${room.roomId}`, { headers: { ...auth(room), "x-secret": "0".repeat(64) } })).status, 401);
  assert.equal((await call(env, "GET", `/v1/rooms/${room.roomId}`, { headers: auth(room) })).status, 200);
});

test("отметки: ставить, снимать, видеть чужие", async () => {
  const env = newEnv();
  const a = await makeRoom(env);
  const { body: b } = await joinRoom(env, a.code);
  const put = (who, d, v) => call(env, "PUT", `/v1/rooms/${a.roomId}/marks`, { headers: auth(who), body: { day: d, value: v } });
  assert.equal((await put(a, day(0), 1)).status, 204);
  assert.equal((await put(a, day(-1), 1)).status, 204);
  assert.equal((await put(b, day(0), 1)).status, 204);
  assert.equal((await put(a, day(0), 1)).status, 204); // повтор не ломает
  const read = await (await call(env, "GET", `/v1/rooms/${a.roomId}`, { headers: auth(b) })).json();
  assert.equal(read.members.length, 2);
  assert.equal(read.marks.length, 3);
  assert.equal(read.marks.filter((m) => m.member === b.memberId).length, 1);
  assert.equal((await put(a, day(0), 0)).status, 204);
  const after = await (await call(env, "GET", `/v1/rooms/${a.roomId}`, { headers: auth(a) })).json();
  assert.equal(after.marks.length, 2);
  assert.ok(!JSON.stringify(after).includes(a.secret));
  assert.ok(!JSON.stringify(after).includes("secret"));
});

test("отметка за неправильный день отклоняется", async () => {
  const env = newEnv();
  const a = await makeRoom(env);
  const put = (d, v) => call(env, "PUT", `/v1/rooms/${a.roomId}/marks`, { headers: auth(a), body: { day: d, value: v } });
  assert.equal((await put(day(-30), 1)).status, 400);
  assert.equal((await put(day(5), 1)).status, 400);
  assert.equal((await put("2026-13-40", 1)).status, 400);
  assert.equal((await put("вчера", 1)).status, 400);
  assert.equal((await put(day(0), 2)).status, 400);
  assert.equal((await put(day(-14), 1)).status, 204);
  assert.equal((await put(day(1), 1)).status, 204);
});

test("чтение отдаёт отметки за 60 дней", async () => {
  const env = newEnv();
  const a = await makeRoom(env);
  env.DB.db.prepare("INSERT INTO mark VALUES(?, ?, ?)").run(a.roomId, a.memberId, day(-61));
  env.DB.db.prepare("INSERT INTO mark VALUES(?, ?, ?)").run(a.roomId, a.memberId, day(-59));
  const read = await (await call(env, "GET", `/v1/rooms/${a.roomId}`, { headers: auth(a) })).json();
  assert.deepEqual(read.marks.map((m) => m.day), [day(-59)]);
});

test("новый код для комнаты, где место есть", async () => {
  const env = newEnv({ MAX_MEMBERS: "3" });
  const a = await makeRoom(env);
  const { body: b } = await joinRoom(env, a.code);
  assert.ok(b);
  const res = await call(env, "POST", `/v1/rooms/${a.roomId}/code`, { headers: auth(a) });
  assert.equal(res.status, 200);
  const fresh = await res.json();
  assert.match(fresh.code, /^[A-HJ-NP-Z2-9]{6}$/);
  assert.equal((await joinRoom(env, fresh.code, "Третий", "4.4.4.4")).res.status, 200);
  assert.equal((await joinRoom(env, "AAAAAA", "x", "5.5.5.5")).res.status, 404);
});

test("выход: свои отметки стираются, последний уходит — комната удаляется", async () => {
  const env = newEnv();
  const a = await makeRoom(env);
  const { body: b } = await joinRoom(env, a.code);
  const put = (who, d) => call(env, "PUT", `/v1/rooms/${a.roomId}/marks`, { headers: auth(who), body: { day: d, value: 1 } });
  await put(a, day(0));
  await put(b, day(0));
  assert.equal((await call(env, "DELETE", `/v1/rooms/${a.roomId}/me`, { headers: auth(a) })).status, 204);
  assert.equal((await call(env, "GET", `/v1/rooms/${a.roomId}`, { headers: auth(a) })).status, 401);
  const read = await (await call(env, "GET", `/v1/rooms/${a.roomId}`, { headers: auth(b) })).json();
  assert.equal(read.members.length, 1);
  assert.equal(read.marks.length, 1);
  assert.equal((await call(env, "DELETE", `/v1/rooms/${a.roomId}/me`, { headers: auth(b) })).status, 204);
  assert.equal(env.DB.db.prepare("SELECT COUNT(*) AS n FROM room").get().n, 0);
  assert.equal(env.DB.db.prepare("SELECT COUNT(*) AS n FROM mark").get().n, 0);
  assert.equal(env.DB.db.prepare("SELECT COUNT(*) AS n FROM member").get().n, 0);
});

test("уборка: старые комнаты и счётчики удаляются, свежие остаются", async () => {
  const env = newEnv();
  const old = await makeRoom(env);
  const fresh = await call(env, "POST", "/v1/rooms", { body: { title: "x", freqMask: 1, nick: "n" }, now: NOW + 80 * DAY, ip: "7.7.7.7" });
  const freshRoom = await fresh.json();
  await cleanup(env, NOW + 100 * DAY);
  const ids = env.DB.db.prepare("SELECT id FROM room").all().map((r) => r.id);
  assert.deepEqual(ids, [freshRoom.roomId]);
  assert.equal(env.DB.db.prepare("SELECT COUNT(*) AS n FROM member WHERE room_id = ?").get(old.roomId).n, 0);
});

test("неизвестные пути и методы", async () => {
  const env = newEnv();
  assert.equal((await call(env, "GET", "/")).status, 404);
  assert.equal((await call(env, "GET", "/v1/rooms")).status, 404);
  assert.equal((await call(env, "PATCH", "/v1/rooms/00000000-0000-4000-8000-000000000000")).status, 404);
  assert.equal((await call(env, "GET", "/v1/rooms/не-id")).status, 404);
});

test("слишком большое или битое тело", async () => {
  const env = newEnv();
  const big = await handle(
    new Request("https://rooms.test/v1/rooms", { method: "POST", body: "x".repeat(5000), headers: { "cf-connecting-ip": "1.1.1.1" } }),
    env,
    NOW,
  );
  assert.equal(big.status, 413);
  const bad = await handle(new Request("https://rooms.test/v1/rooms", { method: "POST", body: "{", headers: { "cf-connecting-ip": "1.1.1.1" } }), env, NOW);
  assert.equal(bad.status, 400);
});

test("нормализация кода", () => {
  assert.equal(normalizeCode(" 7k4-m2q "), "7K4M2Q");
  assert.equal(normalizeCode(null), "");
});
