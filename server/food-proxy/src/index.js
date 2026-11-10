// Посредник «Еда по фото» (Р70). Приложение шлёт сюда запрос в формате Gemini generateContent,
// посредник добавляет общий ключ (он лежит только здесь, в секрете GEMINI_API_KEY) и пересылает в Google.
// Ничего не сохраняет: ни фото, ни ответы. В хранилище KV лежат только счётчики запросов.

const MODELS = new Set([
  "gemini-3.5-flash-lite",
  "gemini-3.1-flash-lite",
  "gemini-3-flash-preview",
  "gemini-2.5-flash-lite",
]);
const PATH = /^\/v1beta\/models\/([a-z0-9.\-]+):generateContent$/;
const ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const GOOGLE = "https://generativelanguage.googleapis.com";
const MAX_BODY = 3 * 1024 * 1024; // фото в приложении уже уменьшено до ~1280 px, обычно 150-400 КБ
const DAY_TTL = 60 * 60 * 36;

const json = (status, body) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });

const num = (v, fallback) => {
  const n = Number(v);
  return Number.isFinite(n) && n > 0 ? n : fallback;
};

/** Счётчик за сутки (UTC). Возвращает новое значение. Не атомарный: для защиты от перебора хватает. */
async function bump(kv, key) {
  const current = Number(await kv.get(key)) || 0;
  await kv.put(key, String(current + 1), { expirationTtl: DAY_TTL });
  return current + 1;
}

/**
 * @param {Request} request
 * @param {{GEMINI_API_KEY?: string, COUNTERS: KVNamespace, PER_USER_DAILY?: string, GLOBAL_DAILY?: string}} env
 * @param {typeof fetch} fetchFn подменяется в тестах
 * @param {Date} now подменяется в тестах
 */
export async function handle(request, env, fetchFn = fetch, now = new Date()) {
  const url = new URL(request.url);
  if (request.method === "GET" && url.pathname === "/health") return json(200, { ok: true });

  const m = PATH.exec(url.pathname);
  if (request.method !== "POST" || !m) return json(404, { error: "not found" });
  if (!MODELS.has(m[1])) return json(404, { error: "unknown model" });
  if (!env.GEMINI_API_KEY) return json(500, { error: "server not configured" });

  const install = request.headers.get("x-install-id") || "";
  if (!ID.test(install)) return json(400, { error: "bad install id" });

  const length = Number(request.headers.get("content-length") || 0);
  if (length > MAX_BODY) return json(413, { error: "too large" });
  const body = await request.arrayBuffer();
  if (body.byteLength === 0 || body.byteLength > MAX_BODY) return json(413, { error: "bad size" });

  const day = now.toISOString().slice(0, 10);
  const perUser = num(env.PER_USER_DAILY, 15);
  const global = num(env.GLOBAL_DAILY, 300);
  // Сначала личный счётчик, потом общий: чужие запросы не съедают общий лимит у того, кто уже исчерпал свой.
  if ((await bump(env.COUNTERS, `u:${day}:${install}`)) > perUser) {
    return json(429, { error: "user daily limit", scope: "user" });
  }
  if ((await bump(env.COUNTERS, `g:${day}`)) > global) {
    return json(429, { error: "global daily limit", scope: "global" });
  }

  let upstream;
  try {
    upstream = await fetchFn(`${GOOGLE}${url.pathname}`, {
      method: "POST",
      headers: { "content-type": "application/json", "x-goog-api-key": env.GEMINI_API_KEY },
      body,
    });
  } catch {
    return json(502, { error: "upstream unreachable" });
  }
  // Ответ Google отдаём как есть: приложение уже умеет читать его коды (429, 404, 5xx).
  const text = await upstream.text();
  return new Response(text, { status: upstream.status, headers: { "content-type": "application/json" } });
}

export default { fetch: (request, env) => handle(request, env) };
