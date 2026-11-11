// Сервер комнат общих привычек (Р79). Cloudflare Workers + база D1, бесплатный тариф.
// Задача одна: двое друзей на разных телефонах видят отметки друг друга в общей привычке.
// Аккаунтов нет. Человек получает случайный номер и секрет при создании комнаты или входе по коду.
// Хранится: название привычки, псевдонимы, дни, когда отметили. Больше ничего.

const ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // 32 знака без I, O, 0, 1
const CODE_LENGTH = 6;
const DAY_MS = 24 * 60 * 60 * 1000;
const CODE_TTL_MS = 7 * DAY_MS;
const MARK_PAST_DAYS = 14;
const MARK_FUTURE_DAYS = 1;
const READ_DAYS = 60;
const IDLE_DAYS = 90;
const TITLE_MAX = 60;
const NICK_MAX = 20;
const BODY_MAX = 4096;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const SECRET = /^[0-9a-f]{64}$/;
const DAY = /^\d{4}-\d{2}-\d{2}$/;

const json = (status, body) =>
  new Response(body === null ? null : JSON.stringify(body), {
    status,
    headers: body === null ? {} : { "content-type": "application/json" },
  });

const num = (v, fallback) => {
  const n = Number(v);
  return Number.isFinite(n) && n > 0 ? n : fallback;
};

const hex = (bytes) => [...bytes].map((b) => b.toString(16).padStart(2, "0")).join("");
const randomHex = (n) => hex(crypto.getRandomValues(new Uint8Array(n)));
async function sha256(text) {
  return hex(new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text))));
}

function newCode() {
  const bytes = crypto.getRandomValues(new Uint8Array(CODE_LENGTH));
  return [...bytes].map((b) => ALPHABET[b % ALPHABET.length]).join("");
}

/** «7k4-m2q» и «7K4M2Q» — один код. */
export function normalizeCode(raw) {
  return String(raw || "").toUpperCase().replace(/[^A-Z0-9]/g, "");
}

const cleanText = (v, max) => {
  if (typeof v !== "string") return null;
  const t = v.replace(/[\u0000-\u001f\u007f]/g, " ").replace(/\s+/g, " ").trim();
  return t.length >= 1 && [...t].length <= max ? t : null;
};

function dayString(ms) {
  return new Date(ms).toISOString().slice(0, 10);
}

function validDay(day, nowMs) {
  if (!DAY.test(day)) return false;
  const t = Date.parse(day + "T00:00:00Z");
  if (!Number.isFinite(t) || dayString(t) !== day) return false;
  return t >= nowMs - (MARK_PAST_DAYS + 1) * DAY_MS && t <= nowMs + (MARK_FUTURE_DAYS + 1) * DAY_MS;
}

async function readJson(request) {
  const length = Number(request.headers.get("content-length") || 0);
  if (length > BODY_MAX) return { error: json(413, { error: "too large" }) };
  const text = await request.text();
  if (text.length > BODY_MAX) return { error: json(413, { error: "too large" }) };
  try {
    const value = JSON.parse(text || "{}");
    if (value === null || typeof value !== "object" || Array.isArray(value)) throw new Error("shape");
    return { value };
  } catch {
    return { error: json(400, { error: "bad json" }) };
  }
}

/** Счётчик в окне. Возвращает новое значение. */
async function hit(db, key, windowMs, nowMs) {
  const bucket = Math.floor(nowMs / windowMs);
  const row = await db
    .prepare(
      "INSERT INTO counter(key, n, expires) VALUES(?, 1, ?) ON CONFLICT(key) DO UPDATE SET n = n + 1 RETURNING n",
    )
    .bind(`${key}:${bucket}`, (bucket + 2) * windowMs)
    .first();
  return row ? Number(row.n) : 1;
}

async function ipKey(request) {
  const ip = request.headers.get("cf-connecting-ip") || "local";
  return (await sha256("ip:" + ip)).slice(0, 16);
}

async function authorize(request, db, roomId) {
  if (!UUID.test(roomId)) return null;
  const memberId = request.headers.get("x-member-id") || "";
  const secret = request.headers.get("x-secret") || "";
  if (!UUID.test(memberId) || !SECRET.test(secret)) return null;
  const row = await db
    .prepare("SELECT id, nick, secret_hash FROM member WHERE id = ? AND room_id = ?")
    .bind(memberId, roomId)
    .first();
  if (!row) return null;
  const given = await sha256(secret);
  let diff = given.length ^ row.secret_hash.length;
  for (let i = 0; i < given.length; i++) diff |= given.charCodeAt(i) ^ (row.secret_hash.charCodeAt(i) || 0);
  return diff === 0 ? { id: row.id, nick: row.nick } : null;
}

async function membersOf(db, roomId) {
  const { results } = await db.prepare("SELECT id, nick FROM member WHERE room_id = ? ORDER BY joined, id").bind(roomId).all();
  return results.map((r) => ({ id: r.id, nick: r.nick }));
}

async function createRoom(request, env, nowMs) {
  const { value, error } = await readJson(request);
  if (error) return error;
  const title = cleanText(value.title, TITLE_MAX);
  const nick = cleanText(value.nick, NICK_MAX);
  const mask = Number(value.freqMask);
  if (!title || !nick || !Number.isInteger(mask) || mask < 1 || mask > 127) return json(400, { error: "bad input" });
  if ((await hit(env.DB, `create:${await ipKey(request)}`, DAY_MS, nowMs)) > num(env.CREATE_PER_DAY, 10)) {
    return json(429, { error: "too many rooms" });
  }
  const roomId = crypto.randomUUID();
  const memberId = crypto.randomUUID();
  const secret = randomHex(32);
  const secretHash = await sha256(secret);
  for (let attempt = 0; attempt < 5; attempt++) {
    const code = newCode();
    const taken = await env.DB.prepare("SELECT 1 AS x FROM room WHERE code = ? AND code_expires > ?").bind(code, nowMs).first();
    if (taken) continue;
    await env.DB.batch([
      env.DB
        .prepare("INSERT INTO room(id, code, code_expires, title, freq_mask, created, last_active) VALUES(?, ?, ?, ?, ?, ?, ?)")
        .bind(roomId, code, nowMs + CODE_TTL_MS, title, mask, nowMs, nowMs),
      env.DB
        .prepare("INSERT INTO member(id, room_id, nick, secret_hash, joined) VALUES(?, ?, ?, ?, ?)")
        .bind(memberId, roomId, nick, secretHash, nowMs),
    ]);
    return json(201, { roomId, memberId, secret, code, codeExpires: nowMs + CODE_TTL_MS });
  }
  return json(503, { error: "try again" });
}

async function joinRoom(request, env, nowMs) {
  const { value, error } = await readJson(request);
  if (error) return error;
  const code = normalizeCode(value.code);
  const nick = cleanText(value.nick, NICK_MAX);
  if (code.length !== CODE_LENGTH || !nick) return json(400, { error: "bad input" });
  // Перебор кодов: не больше 20 попыток в час с одного адреса.
  if ((await hit(env.DB, `join:${await ipKey(request)}`, 60 * 60 * 1000, nowMs)) > num(env.JOIN_PER_HOUR, 20)) {
    return json(429, { error: "too many tries" });
  }
  const room = await env.DB
    .prepare("SELECT id, title, freq_mask FROM room WHERE code = ? AND code_expires > ?")
    .bind(code, nowMs)
    .first();
  if (!room) return json(404, { error: "bad code" });
  const members = await membersOf(env.DB, room.id);
  if (members.length >= num(env.MAX_MEMBERS, 2)) return json(409, { error: "room full" });
  const memberId = crypto.randomUUID();
  const secret = randomHex(32);
  await env.DB.batch([
    env.DB
      .prepare("INSERT INTO member(id, room_id, nick, secret_hash, joined) VALUES(?, ?, ?, ?, ?)")
      .bind(memberId, room.id, nick, await sha256(secret), nowMs),
    // Код одноразовый: после входа он больше не работает.
    env.DB.prepare("UPDATE room SET code_expires = 0, last_active = ? WHERE id = ?").bind(nowMs, room.id),
  ]);
  return json(200, {
    roomId: room.id,
    memberId,
    secret,
    title: room.title,
    freqMask: room.freq_mask,
    members: [...members, { id: memberId, nick }],
  });
}

async function readRoom(request, env, roomId, nowMs) {
  const me = await authorize(request, env.DB, roomId);
  if (!me) return json(401, { error: "unauthorized" });
  const room = await env.DB.prepare("SELECT id, title, freq_mask FROM room WHERE id = ?").bind(roomId).first();
  if (!room) return json(404, { error: "not found" });
  const since = dayString(nowMs - READ_DAYS * DAY_MS);
  const { results } = await env.DB
    .prepare("SELECT member_id, day FROM mark WHERE room_id = ? AND day >= ? ORDER BY day")
    .bind(roomId, since)
    .all();
  await env.DB.prepare("UPDATE room SET last_active = ? WHERE id = ?").bind(nowMs, roomId).run();
  return json(200, {
    room: { id: room.id, title: room.title, freqMask: room.freq_mask },
    members: await membersOf(env.DB, roomId),
    marks: results.map((r) => ({ member: r.member_id, day: r.day })),
    now: nowMs,
  });
}

async function putMark(request, env, roomId, nowMs) {
  const me = await authorize(request, env.DB, roomId);
  if (!me) return json(401, { error: "unauthorized" });
  const { value, error } = await readJson(request);
  if (error) return error;
  const day = String(value.day || "");
  if (!validDay(day, nowMs) || (value.value !== 0 && value.value !== 1)) return json(400, { error: "bad input" });
  if ((await hit(env.DB, `mark:${me.id}`, DAY_MS, nowMs)) > num(env.MARKS_PER_DAY, 400)) return json(429, { error: "too many marks" });
  const stmt =
    value.value === 1
      ? env.DB.prepare("INSERT OR IGNORE INTO mark(room_id, member_id, day) VALUES(?, ?, ?)")
      : env.DB.prepare("DELETE FROM mark WHERE room_id = ? AND member_id = ? AND day = ?");
  await env.DB.batch([stmt.bind(roomId, me.id, day), env.DB.prepare("UPDATE room SET last_active = ? WHERE id = ?").bind(nowMs, roomId)]);
  return json(204, null);
}

async function newRoomCode(request, env, roomId, nowMs) {
  const me = await authorize(request, env.DB, roomId);
  if (!me) return json(401, { error: "unauthorized" });
  const members = await membersOf(env.DB, roomId);
  if (members.length >= num(env.MAX_MEMBERS, 2)) return json(409, { error: "room full" });
  for (let attempt = 0; attempt < 5; attempt++) {
    const code = newCode();
    const taken = await env.DB.prepare("SELECT 1 AS x FROM room WHERE code = ? AND code_expires > ?").bind(code, nowMs).first();
    if (taken) continue;
    await env.DB.prepare("UPDATE room SET code = ?, code_expires = ?, last_active = ? WHERE id = ?").bind(code, nowMs + CODE_TTL_MS, nowMs, roomId).run();
    return json(200, { code, codeExpires: nowMs + CODE_TTL_MS });
  }
  return json(503, { error: "try again" });
}

async function leave(request, env, roomId, nowMs) {
  const me = await authorize(request, env.DB, roomId);
  if (!me) return json(401, { error: "unauthorized" });
  await env.DB.batch([
    env.DB.prepare("DELETE FROM mark WHERE room_id = ? AND member_id = ?").bind(roomId, me.id),
    env.DB.prepare("DELETE FROM member WHERE id = ?").bind(me.id),
  ]);
  const left = await env.DB.prepare("SELECT COUNT(*) AS n FROM member WHERE room_id = ?").bind(roomId).first();
  if (Number(left.n) === 0) {
    await env.DB.batch([
      env.DB.prepare("DELETE FROM mark WHERE room_id = ?").bind(roomId),
      env.DB.prepare("DELETE FROM room WHERE id = ?").bind(roomId),
    ]);
  }
  return json(204, null);
}

/**
 * @param {Request} request
 * @param {{DB: D1Database, MAX_MEMBERS?: string, CREATE_PER_DAY?: string, JOIN_PER_HOUR?: string, MARKS_PER_DAY?: string}} env
 * @param {number} nowMs подменяется в тестах
 */
export async function handle(request, env, nowMs = Date.now()) {
  const url = new URL(request.url);
  const path = url.pathname;
  const method = request.method;
  if (method === "GET" && path === "/health") return json(200, { ok: true });
  if (method === "POST" && path === "/v1/rooms") return createRoom(request, env, nowMs);
  if (method === "POST" && path === "/v1/join") return joinRoom(request, env, nowMs);
  const m = /^\/v1\/rooms\/([0-9a-f-]{36})(\/[a-z]+(?:\/[a-z]+)?)?$/.exec(path);
  if (m) {
    const [, id, rest] = m;
    if (method === "GET" && !rest) return readRoom(request, env, id, nowMs);
    if (method === "PUT" && rest === "/marks") return putMark(request, env, id, nowMs);
    if (method === "POST" && rest === "/code") return newRoomCode(request, env, id, nowMs);
    if (method === "DELETE" && rest === "/me") return leave(request, env, id, nowMs);
  }
  return json(404, { error: "not found" });
}

/** Раз в сутки: убрать комнаты, где никто не отмечался 90 дней, и просроченные счётчики. */
export async function cleanup(env, nowMs = Date.now()) {
  const limit = nowMs - IDLE_DAYS * DAY_MS;
  await env.DB.batch([
    env.DB.prepare("DELETE FROM mark WHERE room_id IN (SELECT id FROM room WHERE last_active < ?)").bind(limit),
    env.DB.prepare("DELETE FROM member WHERE room_id IN (SELECT id FROM room WHERE last_active < ?)").bind(limit),
    env.DB.prepare("DELETE FROM room WHERE last_active < ?").bind(limit),
    env.DB.prepare("DELETE FROM counter WHERE expires < ?").bind(nowMs),
  ]);
}

export default {
  fetch: (request, env) => handle(request, env),
  scheduled: (_event, env, ctx) => ctx.waitUntil(cleanup(env)),
};
