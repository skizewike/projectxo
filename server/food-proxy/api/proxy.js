// Вход для Vercel: тот же посредник, что и для Cloudflare (src/index.js), только счётчики лежат в Redis (Upstash),
// а не в Cloudflare KV. Vercel запускает функцию в США (см. vercel.json), оттуда Gemini доступен.
import { handle } from "../src/index.js";

const URL_ = process.env.KV_REST_API_URL || process.env.UPSTASH_REDIS_REST_URL;
const TOKEN = process.env.KV_REST_API_TOKEN || process.env.UPSTASH_REDIS_REST_TOKEN;

const memory = new Map(); // запасной вариант без Redis: считает только внутри одного экземпляра функции

async function redis(command) {
  const r = await fetch(URL_, {
    method: "POST",
    headers: { authorization: `Bearer ${TOKEN}`, "content-type": "application/json" },
    body: JSON.stringify(command),
  });
  if (!r.ok) throw new Error(`redis ${r.status}`);
  return (await r.json()).result;
}

const counters = {
  get: async (k) => (URL_ && TOKEN ? redis(["GET", k]) : (memory.get(k) ?? null)),
  put: async (k, v, opts = {}) => {
    if (URL_ && TOKEN) await redis(["SET", k, v, "EX", String(opts.expirationTtl ?? 129600)]);
    else memory.set(k, v);
  },
};

export default {
  fetch: (request) =>
    handle(request, {
      GEMINI_API_KEY: process.env.GEMINI_API_KEY,
      COUNTERS: counters,
      PER_USER_DAILY: process.env.PER_USER_DAILY,
      GLOBAL_DAILY: process.env.GLOBAL_DAILY,
    }),
};
