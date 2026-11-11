import test from "node:test";
import assert from "node:assert/strict";
import { handle } from "../src/index.js";

const ID = "123e4567-e89b-42d3-a456-426614174000";
const PATH = "/v1beta/models/gemini-3.5-flash-lite:generateContent";

function kv() {
  const m = new Map();
  return { get: async (k) => m.get(k) ?? null, put: async (k, v) => void m.set(k, v), m };
}
const env = (extra = {}) => ({ GEMINI_API_KEY: "SECRET", COUNTERS: kv(), ...extra });
const req = (over = {}) =>
  new Request(`https://p.example${over.path ?? PATH}`, {
    method: over.method ?? "POST",
    headers: { "x-install-id": ID, "content-type": "application/json", ...(over.headers ?? {}) },
    body: over.method === "GET" ? undefined : (over.body ?? '{"x":1}'),
  });
const ok = async (url, init) => new Response('{"candidates":[]}', { status: 200 });

test("пересылает в Google с ключом из секрета и возвращает ответ", async () => {
  let seen;
  const r = await handle(req(), env(), async (url, init) => ((seen = { url, init }), ok()));
  assert.equal(r.status, 200);
  assert.equal(seen.url, `https://generativelanguage.googleapis.com${PATH}`);
  assert.equal(seen.init.headers["x-goog-api-key"], "SECRET");
  assert.equal(await r.text(), '{"candidates":[]}');
});

test("передаёт коды Google как есть", async () => {
  const r = await handle(req(), env(), async () => new Response("{}", { status: 429 }));
  assert.equal(r.status, 429);
});

test("чужая модель и чужой путь отклоняются", async () => {
  assert.equal((await handle(req({ path: "/v1beta/models/gemini-9-pro:generateContent" }), env(), ok)).status, 404);
  assert.equal((await handle(req({ path: "/v1beta/models" }), env(), ok)).status, 404);
  assert.equal((await handle(req({ path: "/v1beta/models/gemini-3.5-flash-lite:streamGenerateContent" }), env(), ok)).status, 404);
});

test("без нормального install id запрос не принимается", async () => {
  assert.equal((await handle(req({ headers: { "x-install-id": "abc" } }), env(), ok)).status, 400);
});

test("большое тело отклоняется", async () => {
  const big = "x".repeat(3 * 1024 * 1024 + 1);
  assert.equal((await handle(req({ body: big }), env(), ok)).status, 413);
});

test("личный лимит: после N запросов 429 с scope user", async () => {
  const e = env({ PER_USER_DAILY: "2" });
  assert.equal((await handle(req(), e, ok)).status, 200);
  assert.equal((await handle(req(), e, ok)).status, 200);
  const r = await handle(req(), e, ok);
  assert.equal(r.status, 429);
  assert.equal((await r.json()).scope, "user");
});

test("общий лимит: другой пользователь тоже получает 429 с scope global", async () => {
  const e = env({ GLOBAL_DAILY: "1" });
  assert.equal((await handle(req(), e, ok)).status, 200);
  const other = req({ headers: { "x-install-id": "223e4567-e89b-42d3-a456-426614174000" } });
  const r = await handle(other, e, ok);
  assert.equal(r.status, 429);
  assert.equal((await r.json()).scope, "global");
});

test("лимит сбрасывается на следующий день", async () => {
  const e = env({ PER_USER_DAILY: "1" });
  assert.equal((await handle(req(), e, ok, new Date("2026-11-10T10:00:00Z"))).status, 200);
  assert.equal((await handle(req(), e, ok, new Date("2026-11-10T11:00:00Z"))).status, 429);
  assert.equal((await handle(req(), e, ok, new Date("2026-11-11T00:01:00Z"))).status, 200);
});

test("без ключа в секрете сервер честно сообщает 500", async () => {
  assert.equal((await handle(req(), env({ GEMINI_API_KEY: "" }), ok)).status, 500);
});

test("недоступный Google даёт 502", async () => {
  const r = await handle(req(), env(), async () => {
    throw new Error("down");
  });
  assert.equal(r.status, 502);
});

test("health", async () => {
  const r = await handle(new Request("https://p.example/health"), env(), ok);
  assert.equal(r.status, 200);
});

test("неудачные ответы Google не расходуют лимит", async () => {
  const e = env({ PER_USER_DAILY: "1" });
  for (const status of [404, 429, 503]) {
    assert.equal((await handle(req(), e, async () => new Response("{}", { status }))).status, status);
  }
  assert.equal((await handle(req(), e, ok)).status, 200);
  assert.equal((await handle(req(), e, ok)).status, 429);
});
