package com.projectxo.app.core.data.foodai

import com.projectxo.app.core.model.Confidence
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class FoodPhotoAiTest {
    private val good = """
        ```json
        {"is_food": true, "dish": "Гречка с курицей", "confidence": "low", "clarify": "Сколько масла?",
         "items": [
           {"name": "Гречка", "grams": 180, "kcal_100": 110, "protein_100": 4.2, "fat_100": 1.1, "carbs_100": 21, "added_fat": false},
           {"name": "Курица", "grams": 120, "kcal_100": 20, "protein_100": 31, "fat_100": 3.6, "carbs_100": 0, "added_fat": false},
           {"name": "без граммов", "kcal_100": 1}
         ],
         "health_score": 14, "health_reasons": ["Много белка", "", "Овощей мало", "Третья", "Четвёртая лишняя"]}
        ```
    """.trimIndent()

    // ——— разбор ответа ———

    @Test
    fun parse_readsDishAndFixesNumbers() {
        val ok = PhotoParser.parse(good) as PhotoResult.Ok
        val dish = ok.dish
        assertEquals("Гречка с курицей", dish.name)
        assertEquals(Confidence.Low, dish.confidence)
        assertEquals("Сколько масла?", dish.clarify)
        assertEquals(2, dish.items.size) // строка без граммов пропущена
        // ИИ написал 20 ккал на 100 г курицы, а макро дают 4·31 + 9·3,6 = 156,4: берём по макро.
        assertEquals(156.4, dish.items[1].kcal100, 0.01)
        assertEquals(10, dish.healthScore) // 14 обрезано до 10
        assertEquals(listOf("Много белка", "Овощей мало", "Третья"), dish.healthReasons)
    }

    @Test
    fun parse_notFood() {
        assertEquals(PhotoResult.NotFood, PhotoParser.parse("""{"is_food": false}"""))
    }

    @Test
    fun parse_garbageIsBadAnswer() {
        assertEquals(PhotoResult.Fail(PhotoError.BadAnswer), PhotoParser.parse("не json"))
        assertEquals(PhotoResult.Fail(PhotoError.BadAnswer), PhotoParser.parse("""{"is_food": true, "items": []}"""))
        assertEquals(PhotoResult.Fail(PhotoError.BadAnswer), PhotoParser.parse("""{"dish": "x"}"""))
    }

    @Test
    fun parse_nullClarifyStringIsNoQuestion() {
        val ok = PhotoParser.parse(
            """{"dish":"Яблоко","clarify":"null","items":[{"name":"Яблоко","grams":150,"kcal_100":52,"protein_100":0.3,"fat_100":0.2,"carbs_100":14}]}""",
        ) as PhotoResult.Ok
        assertEquals(null, ok.dish.clarify)
        assertEquals(Confidence.Medium, ok.dish.confidence)
    }

    // ——— запрос к серверу (локальный сервер вместо Google) ———

    private lateinit var server: HttpServer
    private val hits = mutableListOf<String>()
    private val seenKeys = mutableListOf<String?>()
    private var answers = mapOf<String, Pair<Int, String>>()

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            val model = ex.requestURI.path.substringAfterLast('/').substringBefore(':')
            hits += model
            seenKeys += ex.requestHeaders.getFirst("x-goog-api-key")
            val (code, body) = answers[model] ?: (404 to "{}")
            val bytes = body.toByteArray()
            ex.sendResponseHeaders(code, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @After
    fun stopServer() = server.stop(0)

    private fun analyzer(key: String = "K".repeat(39), models: List<String>) =
        GeminiFoodAnalyzer({ key }, Dispatchers.Unconfined, models, "http://127.0.0.1:${server.address.port}")

    private fun reply(text: String) =
        200 to """{"candidates":[{"content":{"parts":[{"text":${org.json.JSONObject.quote(text)}}]}}]}"""

    @Test
    fun analyze_noKey_failsWithoutNetwork() = runBlocking {
        val r = analyzer(key = " ", models = listOf("a")).analyze(byteArrayOf(1), "", "Russian")
        assertEquals(PhotoResult.Fail(PhotoError.NoKey), r)
        assertTrue(hits.isEmpty())
    }

    @Test
    fun analyze_quotaOnFirstModel_fallsBackToNext() = runBlocking {
        answers = mapOf("m1" to (429 to "{}"), "m2" to (404 to "{}"), "m3" to reply(good))
        val r = analyzer(models = listOf("m1", "m2", "m3")).analyze(byteArrayOf(1, 2, 3), "гречка", "Russian")
        assertTrue(r is PhotoResult.Ok)
        assertEquals(listOf("m1", "m2", "m3"), hits)
        assertTrue(seenKeys.all { it == "K".repeat(39) }) // ключ только в заголовке
    }

    @Test
    fun analyze_allModelsOutOfQuota_reportsQuota() = runBlocking {
        answers = mapOf("m1" to (429 to "{}"), "m2" to (429 to "{}"))
        val r = analyzer(models = listOf("m1", "m2")).analyze(byteArrayOf(1), "", "Russian")
        assertEquals(PhotoResult.Fail(PhotoError.Quota), r)
    }

    @Test
    fun analyze_badKey_stopsAtOnce() = runBlocking {
        answers = mapOf("m1" to (400 to """{"error":{"message":"API key not valid","details":[{"reason":"API_KEY_INVALID"}]}}"""))
        val r = analyzer(models = listOf("m1", "m2")).analyze(byteArrayOf(1), "", "Russian")
        assertEquals(PhotoResult.Fail(PhotoError.BadKey), r)
        assertEquals(listOf("m1"), hits)
    }

    @Test
    fun analyze_brokenAnswerOnFirstModel_triesNext() = runBlocking {
        answers = mapOf("m1" to reply("извините, не могу"), "m2" to reply(good))
        val r = analyzer(models = listOf("m1", "m2")).analyze(byteArrayOf(1), "", "Russian")
        assertTrue(r is PhotoResult.Ok)
        assertEquals(listOf("m1", "m2"), hits)
    }

    @Test
    fun analyze_serverDown_isNetworkError() = runBlocking {
        val port = server.address.port
        server.stop(0)
        val r = GeminiFoodAnalyzer({ "K".repeat(39) }, Dispatchers.Unconfined, listOf("m1"), "http://127.0.0.1:$port")
            .analyze(byteArrayOf(1), "", "Russian")
        assertEquals(PhotoResult.Fail(PhotoError.Network), r)
    }

    private fun viaProxy(key: String, models: List<String>) = GeminiFoodAnalyzer(
        { key }, Dispatchers.Unconfined, models, "http://127.0.0.1:1", "http://127.0.0.1:${server.address.port}/", { "123e4567-e89b-42d3-a456-426614174000" },
    )

    @Test
    fun proxy_usedWhenNoOwnKey_sendsInstallIdInsteadOfKey() = runBlocking {
        var headers: com.sun.net.httpserver.Headers? = null
        server.removeContext("/")
        server.createContext("/") { ex ->
            headers = ex.requestHeaders
            hits += ex.requestURI.path
            val bytes = reply(good).second.toByteArray()
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        val r = viaProxy("", listOf("m1")).analyze(byteArrayOf(1), "", "Russian")
        assertTrue(r is PhotoResult.Ok)
        assertEquals(listOf("/v1beta/models/m1:generateContent"), hits)
        assertEquals("123e4567-e89b-42d3-a456-426614174000", headers!!.getFirst("x-install-id"))
        assertEquals(null, headers!!.getFirst("x-goog-api-key"))
    }

    @Test
    fun proxy_ownKeyWinsOverProxy() = runBlocking {
        // Свой ключ идёт прямо в «Google» (здесь локальный сервер-endpoint), посредник не трогается.
        answers = mapOf("m1" to reply(good))
        val a = GeminiFoodAnalyzer(
            { "K".repeat(39) }, Dispatchers.Unconfined, listOf("m1"), "http://127.0.0.1:${server.address.port}",
            "http://127.0.0.1:1", { "id" },
        )
        assertTrue(a.analyze(byteArrayOf(1), "", "Russian") is PhotoResult.Ok)
        assertEquals("K".repeat(39), seenKeys.single())
    }

    @Test
    fun proxy_dailyLimitIs429_reportedAsQuota() = runBlocking {
        answers = mapOf("m1" to (429 to """{"error":"user daily limit"}"""))
        val r = viaProxy("", listOf("m1")).analyze(byteArrayOf(1), "", "Russian")
        assertEquals(PhotoResult.Fail(PhotoError.Quota), r)
    }

    @Test
    fun prompt_namesLanguageAndSchema() {
        val p = GeminiFoodAnalyzer.prompt("Russian")
        assertTrue(p.contains("in Russian"))
        assertTrue(p.contains("kcal_100") && p.contains("is_food"))
    }
}
