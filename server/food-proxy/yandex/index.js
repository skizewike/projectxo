// Ретранслятор «Еда по фото» для Яндекс Облака (Cloud Functions, Node.js 18+, точка входа index.handler).
// Адрес *.yandexcloud.net открывается из России. Посредник (Cloudflare) держит ключ Gemini и счётчики; из Яндекс Облака до Vercel запрос не доходит (тайм-аут), до Cloudflare доходит.
// Здесь ничего не хранится и ключа нет: функция только пересылает запрос и ответ.

const TARGET = process.env.TARGET_URL || "https://xo-food-proxy.projectxo.workers.dev";
const PATH = /^\/v1beta\/models\/[a-z0-9.\-]+:generateContent$/;

const reply = (statusCode, body) => ({
  statusCode,
  headers: { "content-type": "application/json" },
  body: typeof body === "string" ? body : JSON.stringify(body),
});

const header = (headers, name) => {
  const key = Object.keys(headers || {}).find((k) => k.toLowerCase() === name);
  return key ? headers[key] : "";
};

module.exports.handler = async (event) => {
  // Через API-шлюз настоящий путь лежит в pathParameters.proxy, а event.path там равен шаблону из спецификации.
  const proxied = event.pathParameters && event.pathParameters.proxy;
  const path = (proxied ? "/" + proxied : String(event.url || event.path || "/")).split("?")[0];
  if (path === "/health" || path === "/") return reply(200, { ok: true });
  if (event.httpMethod !== "POST" || !PATH.test(path)) return reply(404, { error: "not found" });

  const body = event.isBase64Encoded ? Buffer.from(event.body || "", "base64") : event.body || "";
  try {
    const r = await fetch(`${TARGET}${path}`, {
      method: "POST",
      headers: { "content-type": "application/json", "x-install-id": header(event.headers, "x-install-id") },
      body,
    });
    return reply(r.status, await r.text());
  } catch (e) {
    const why = String((e && e.cause && (e.cause.code || e.cause.message)) || (e && e.message) || e);
    return reply(502, { error: "upstream unreachable", why });
  }
};
