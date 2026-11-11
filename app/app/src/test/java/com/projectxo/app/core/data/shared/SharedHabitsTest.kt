package com.projectxo.app.core.data.shared

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.projectxo.app.core.data.db.DbSharedHabitRepository
import com.projectxo.app.core.data.db.RoomHabitRepository
import com.projectxo.app.core.data.db.XoDatabase
import com.projectxo.app.core.model.Habit
import com.projectxo.app.core.model.HabitFreq
import com.projectxo.app.core.model.SharedError
import com.projectxo.app.core.model.SharedResult
import com.projectxo.app.testing.FakeClock
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress
import java.time.LocalDate

/** Подмена сервера в памяти: те же правила, что в `server/habit-rooms`, для проверки клиента двух телефонов. */
class FakeRoomsServer(private val maxMembers: Int = 2) {
    class Room(val id: String, var code: String, var title: String, val mask: Int) {
        val members = linkedMapOf<String, String>() // id -> nick
        val secrets = mutableMapOf<String, String>()
        val marks = mutableSetOf<Pair<String, LocalDate>>()
    }

    val rooms = mutableMapOf<String, Room>()
    var offline = false
    private var n = 0
    private fun next() = (++n).toString().padStart(8, '0')

    fun api(): RoomsApi = object : RoomsApi {
        override val configured = true
        override suspend fun ping() = !offline

        private fun <T> guard(block: () -> Api<T>): Api<T> = if (offline) Api.Err(SharedError.Network) else block()
        private fun auth(roomId: String, c: Credentials): Room? = rooms[roomId]?.takeIf { it.secrets[c.memberId] == c.secret }

        override suspend fun createRoom(title: String, freqMask: Int, nick: String) = guard {
            val id = "room-${next()}"
            val r = Room(id, "CODE" + next().takeLast(2), title, freqMask)
            val m = "m-${next()}"
            r.members[m] = nick
            r.secrets[m] = "secret-$m"
            rooms[id] = r
            Api.Ok(CreatedRoom(id, Credentials(m, "secret-$m"), r.code, 0L))
        }

        override suspend fun join(code: String, nick: String) = guard {
            val r = rooms.values.firstOrNull { it.code == code } ?: return@guard Api.Err(SharedError.BadCode)
            if (r.members.size >= maxMembers) return@guard Api.Err(SharedError.RoomFull)
            val m = "m-${next()}"
            r.members[m] = nick
            r.secrets[m] = "secret-$m"
            r.code = "USED"
            Api.Ok(JoinedRoom(r.id, Credentials(m, "secret-$m"), r.title, r.mask, r.members.map { MemberDto(it.key, it.value) }))
        }

        override suspend fun room(roomId: String, creds: Credentials) = guard {
            val r = auth(roomId, creds) ?: return@guard Api.Err(SharedError.Gone)
            Api.Ok(RoomDto(r.id, r.title, r.mask, r.members.map { MemberDto(it.key, it.value) }, r.marks.map { MarkDto(it.first, it.second) }))
        }

        override suspend fun putMark(roomId: String, creds: Credentials, day: LocalDate, done: Boolean) = guard {
            val r = auth(roomId, creds) ?: return@guard Api.Err(SharedError.Gone)
            if (done) r.marks += creds.memberId to day else r.marks -= creds.memberId to day
            Api.Ok(Unit)
        }

        override suspend fun newCode(roomId: String, creds: Credentials) = guard {
            val r = auth(roomId, creds) ?: return@guard Api.Err(SharedError.Gone)
            if (r.members.size >= maxMembers) return@guard Api.Err(SharedError.RoomFull)
            r.code = "NEW" + next().takeLast(3)
            Api.Ok(CodeDto(r.code, 1L))
        }

        override suspend fun leave(roomId: String, creds: Credentials) = guard {
            val r = auth(roomId, creds) ?: return@guard Api.Err(SharedError.Gone)
            r.marks.removeAll { it.first == creds.memberId }
            r.members.remove(creds.memberId)
            r.secrets.remove(creds.memberId)
            if (r.members.isEmpty()) rooms.remove(roomId)
            Api.Ok(Unit)
        }
    }
}

/** Один телефон: своя база, свои привычки, один сервер на двоих. */
private class Phone(server: FakeRoomsServer) {
    val db: XoDatabase = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), XoDatabase::class.java)
        .allowMainThreadQueries().build()
    val habits = RoomHabitRepository(db.habitDao())
    val clock = FakeClock(nowMs = 5_000L, today = LocalDate.of(2026, 11, 11))
    val shared = DbSharedHabitRepository(db.sharedDao(), server.api(), habits, clock)
    fun close() = db.close()
}

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class SharedHabitsTest {
    private val today = LocalDate.of(2026, 11, 11)
    private lateinit var server: FakeRoomsServer
    private lateinit var a: Phone
    private lateinit var b: Phone

    @Before fun setUp() {
        server = FakeRoomsServer()
        a = Phone(server)
        b = Phone(server)
    }

    @After fun tearDown() {
        a.close()
        b.close()
    }

    private suspend fun <T> ok(r: SharedResult<T>): T = (r as SharedResult.Ok).value

    @Test fun twoPhonesSeeEachOthersChecks() = runBlocking {
        val habitId = a.habits.add(Habit(0, "Прогулка", HabitFreq.Daily))
        val habit = a.habits.habits().first().single()
        val roomA = ok(a.shared.create(habit, "Вы", today))
        assertEquals(habitId, roomA.habitId)
        assertNotNull(roomA.code)

        // Друг входит по коду: у него появляется такая же привычка.
        val roomB = ok(b.shared.join(roomA.code!!.lowercase(), " Алекс ", today))
        val bHabit = b.habits.habits().first().single()
        assertEquals("Прогулка", bHabit.name)
        assertEquals(HabitFreq.Daily, bHabit.freq)
        assertEquals(bHabit.id, roomB.habitId)

        // Каждый отмечает себя на своём телефоне.
        a.habits.setCheck(habitId, today, 1)
        a.habits.setCheck(habitId, today.minusDays(1), 1)
        a.shared.syncHabit(habitId, today)
        b.habits.setCheck(bHabit.id, today, 1)
        b.shared.syncHabit(bHabit.id, today)
        a.shared.sync(roomA.roomId, today)

        val stateA = a.shared.state(roomA.roomId).first()!!
        assertEquals(2, stateA.members.size)
        assertEquals("Алекс", stateA.friend?.nick)
        assertEquals(setOf(today), stateA.daysOf(stateA.friend?.memberId))
        assertEquals(setOf(today, today.minusDays(1)), stateA.daysOf(stateA.me?.memberId))

        val stateB = b.shared.state(roomB.roomId).first()!!
        assertEquals("Вы", stateB.friend?.nick)
        assertEquals(setOf(today, today.minusDays(1)), stateB.daysOf(stateB.friend?.memberId))
        assertNull(a.shared.rooms().first().single().code) // друг вошёл — код больше не нужен
    }

    @Test fun uncheckingGoesToTheFriendToo() = runBlocking {
        val id = a.habits.add(Habit(0, "Йога", HabitFreq.Days(0b0010101)))
        val roomA = ok(a.shared.create(a.habits.habits().first().single(), "Вы", today))
        val roomB = ok(b.shared.join(roomA.code!!, "Алекс", today))
        assertEquals(HabitFreq.Days(0b0010101), b.habits.habits().first().single().freq)
        a.habits.setCheck(id, today, 1)
        a.shared.syncHabit(id, today)
        b.shared.sync(roomB.roomId, today)
        assertEquals(1, b.shared.state(roomB.roomId).first()!!.daysOf(roomA.memberId).size)
        a.habits.setCheck(id, today, 0)
        a.shared.syncHabit(id, today)
        b.shared.sync(roomB.roomId, today)
        assertTrue(b.shared.state(roomB.roomId).first()!!.daysOf(roomA.memberId).isEmpty())
    }

    @Test fun offlineChecksArePushedLater() = runBlocking {
        val id = a.habits.add(Habit(0, "Вода", HabitFreq.Daily))
        val room = ok(a.shared.create(a.habits.habits().first().single(), "Вы", today))
        server.offline = true
        a.habits.setCheck(id, today, 1)
        val r = a.shared.sync(room.roomId, today)
        assertEquals(SharedResult.Fail(SharedError.Network), r)
        assertNotNull(a.shared.state(room.roomId).first()) // комната на телефоне цела
        server.offline = false
        ok(a.shared.sync(room.roomId, today))
        assertEquals(1, server.rooms.values.single().marks.size)
    }

    @Test fun wrongOrUsedCodeAndFullRoom() = runBlocking {
        a.habits.add(Habit(0, "Йога", HabitFreq.Daily))
        val room = ok(a.shared.create(a.habits.habits().first().single(), "Вы", today))
        assertEquals(SharedResult.Fail(SharedError.BadCode), b.shared.join("ZZZZZZ", "Алекс", today))
        assertEquals(SharedResult.Fail(SharedError.BadInput), b.shared.join("12", "Алекс", today))
        assertEquals(SharedResult.Fail(SharedError.BadInput), b.shared.join(room.code!!, "  ", today))
        assertTrue(b.habits.habits().first().isEmpty()) // при ошибке лишней привычки нет
        ok(b.shared.join(room.code!!, "Алекс", today))
        val c = Phone(server)
        assertEquals(SharedResult.Fail(SharedError.BadCode), c.shared.join(room.code!!, "Третий", today))
        c.close()
    }

    @Test fun oneHabitCannotBeSharedTwice() = runBlocking {
        a.habits.add(Habit(0, "Йога", HabitFreq.Daily))
        val habit = a.habits.habits().first().single()
        ok(a.shared.create(habit, "Вы", today))
        assertEquals(SharedResult.Fail(SharedError.BadInput), a.shared.create(habit, "Вы", today))
        assertEquals(1, server.rooms.size)
    }

    @Test fun leavingKeepsTheHabitAndRemovesMarksOnTheServer() = runBlocking {
        val id = a.habits.add(Habit(0, "Йога", HabitFreq.Daily))
        val roomA = ok(a.shared.create(a.habits.habits().first().single(), "Вы", today))
        val roomB = ok(b.shared.join(roomA.code!!, "Алекс", today))
        a.habits.setCheck(id, today, 1)
        a.shared.syncHabit(id, today)
        a.shared.leave(roomA.roomId)
        assertTrue(a.shared.rooms().first().isEmpty())
        assertEquals(1, a.habits.habits().first().size) // привычка осталась обычной
        assertTrue(server.rooms.values.single().marks.isEmpty())
        // Друг остаётся один и видит комнату.
        ok(b.shared.sync(roomB.roomId, today))
        assertEquals(1, b.shared.state(roomB.roomId).first()!!.members.size)
    }

    @Test fun deletingTheHabitLeavesTheRoom() = runBlocking {
        val id = a.habits.add(Habit(0, "Йога", HabitFreq.Daily))
        ok(a.shared.create(a.habits.habits().first().single(), "Вы", today))
        a.shared.habitDeleted(id)
        assertTrue(a.shared.rooms().first().isEmpty())
        assertTrue(server.rooms.isEmpty())
    }

    @Test fun roomGoneOnServerIsRemovedLocally() = runBlocking {
        a.habits.add(Habit(0, "Йога", HabitFreq.Daily))
        val room = ok(a.shared.create(a.habits.habits().first().single(), "Вы", today))
        server.rooms.clear()
        assertEquals(SharedResult.Fail(SharedError.Gone), a.shared.sync(room.roomId, today))
        assertTrue(a.shared.rooms().first().isEmpty())
        assertEquals(1, a.habits.habits().first().size)
    }

    @Test fun syncOfRemovedHabitDropsTheRoom() = runBlocking {
        val id = a.habits.add(Habit(0, "Йога", HabitFreq.Daily))
        val room = ok(a.shared.create(a.habits.habits().first().single(), "Вы", today))
        a.habits.delete(id)
        assertEquals(SharedResult.Fail(SharedError.Gone), a.shared.sync(room.roomId, today))
        assertTrue(a.shared.rooms().first().isEmpty())
    }

    @Test fun newCodeReplacesOldOne() = runBlocking {
        a.habits.add(Habit(0, "Йога", HabitFreq.Daily))
        val room = ok(a.shared.create(a.habits.habits().first().single(), "Вы", today))
        val fresh = ok(a.shared.newCode(room.roomId))
        assertTrue(fresh.startsWith("NEW"))
        assertEquals(fresh, a.shared.rooms().first().single().code)
    }

    @Test fun counterHabitNeedsFullTargetToCount() = runBlocking {
        val id = a.habits.add(Habit(0, "Вода 3 раза", HabitFreq.Daily, target = 3))
        val room = ok(a.shared.create(a.habits.habits().first().single(), "Вы", today))
        a.habits.setCheck(id, today, 2)
        a.shared.syncHabit(id, today)
        assertTrue(server.rooms.values.single().marks.isEmpty())
        a.habits.setCheck(id, today, 3)
        a.shared.syncHabit(id, today)
        assertEquals(1, server.rooms.values.single().marks.size)
        assertNotNull(room)
    }

    @Test fun roomsSurviveWithoutConfiguredServer() = runBlocking {
        val off = DbSharedHabitRepository(a.db.sharedDao(), HttpRoomsApi(""), a.habits, a.clock)
        assertFalse(off.available)
        a.habits.add(Habit(0, "Йога", HabitFreq.Daily))
        assertEquals(SharedResult.Fail(SharedError.NotConfigured), off.create(a.habits.habits().first().single(), "Вы", today))
    }
}

/** Клиент HTTP против настоящего локального сервера: коды ответов, заголовки, тело. */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class HttpRoomsApiTest {
    private lateinit var server: HttpServer
    private var reply: Pair<Int, String> = 200 to "{}"
    private val seen = mutableListOf<String>()
    private val seenHeaders = mutableListOf<Map<String, String?>>()
    private var lastBody = ""

    @Before fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            seen += ex.requestMethod + " " + ex.requestURI.path
            seenHeaders += mapOf("id" to ex.requestHeaders.getFirst("x-member-id"), "secret" to ex.requestHeaders.getFirst("x-secret"))
            lastBody = ex.requestBody.readBytes().decodeToString()
            val bytes = reply.second.toByteArray()
            ex.sendResponseHeaders(reply.first, if (reply.first == 204) -1 else bytes.size.toLong())
            if (reply.first != 204) ex.responseBody.use { it.write(bytes) } else ex.close()
        }
        server.start()
    }

    @After fun stop() = server.stop(0)

    private fun api() = HttpRoomsApi("http://127.0.0.1:${server.address.port}/", Dispatchers.Unconfined)
    private val creds = Credentials("00000000-0000-4000-8000-000000000001", "s".repeat(64))

    @Test fun createRoomParsesAnswer() = runBlocking {
        reply = 201 to """{"roomId":"r1","memberId":"m1","secret":"sec","code":"ABC234","codeExpires":99}"""
        val r = api().createRoom("Прогулка", 127, "Вы") as Api.Ok
        assertEquals("POST /v1/rooms", seen.single())
        assertEquals(CreatedRoom("r1", Credentials("m1", "sec"), "ABC234", 99L), r.value)
        assertTrue(lastBody.contains("\"freqMask\":127"))
        assertTrue(lastBody.contains("Прогулка"))
    }

    @Test fun joinMapsErrors() = runBlocking {
        reply = 404 to """{"error":"bad code"}"""
        assertEquals(Api.Err(SharedError.BadCode), api().join("ABC234", "Алекс"))
        // Страница-заглушка хостинга вместо ответа сервера: это не «неверный код».
        reply = 404 to "<html>There is nothing here yet</html>"
        assertEquals(Api.Err(SharedError.Server), api().join("ABC234", "Алекс"))
        reply = 409 to "{}"
        assertEquals(Api.Err(SharedError.RoomFull), api().join("ABC234", "Алекс"))
        reply = 429 to "{}"
        assertEquals(Api.Err(SharedError.TooMany), api().join("ABC234", "Алекс"))
        reply = 400 to "{}"
        assertEquals(Api.Err(SharedError.BadInput), api().join("ABC234", "Алекс"))
        reply = 500 to "oops"
        assertEquals(Api.Err(SharedError.Server), api().join("ABC234", "Алекс"))
    }

    @Test fun roomSendsCredentialsAndParsesMarks() = runBlocking {
        reply = 200 to """{"room":{"id":"r1","title":"T","freqMask":21},"members":[{"id":"m1","nick":"Вы"}],"marks":[{"member":"m1","day":"2026-11-11"},{"member":"m1","day":"плохо"}],"now":1}"""
        val r = api().room("r1", creds) as Api.Ok
        assertEquals("GET /v1/rooms/r1", seen.single())
        assertEquals(creds.memberId, seenHeaders.single()["id"])
        assertEquals(creds.secret, seenHeaders.single()["secret"])
        assertEquals(listOf(MarkDto("m1", LocalDate.of(2026, 11, 11))), r.value.marks) // битая дата пропущена
        assertEquals(21, r.value.freqMask)
    }

    @Test fun markAndLeaveAndGone() = runBlocking {
        reply = 204 to ""
        assertEquals(Api.Ok(Unit), api().putMark("r1", creds, LocalDate.of(2026, 11, 11), true))
        assertTrue(lastBody.contains("\"day\":\"2026-11-11\"") && lastBody.contains("\"value\":1"))
        assertEquals(Api.Ok(Unit), api().leave("r1", creds))
        assertEquals("DELETE /v1/rooms/r1/me", seen.last())
        reply = 401 to """{"error":"unauthorized"}"""
        assertEquals(Api.Err(SharedError.Gone), api().room("r1", creds))
        reply = 404 to """{"error":"not found"}"""
        assertEquals(Api.Err(SharedError.Gone), api().room("r1", creds))
    }

    @Test fun hostingPlaceholderDoesNotLookLikeMissingRoom() = runBlocking {
        reply = 404 to "<html>There is nothing here yet</html>"
        assertEquals(Api.Err(SharedError.Server), api().room("r1", creds))
        reply = 401 to ""
        assertEquals(Api.Err(SharedError.Server), api().room("r1", creds))
    }

    @Test fun badAnswerBodyIsServerError() = runBlocking {
        reply = 200 to "не json"
        assertEquals(Api.Err(SharedError.Server), api().room("r1", creds))
    }

    @Test fun pingNeedsHealthyAnswer() = runBlocking {
        reply = 200 to """{"ok":true}"""
        assertTrue(api().ping())
        assertEquals("GET /health", seen.last())
        reply = 200 to """{"ok":false}"""
        assertFalse(api().ping())
        reply = 500 to "x"
        assertFalse(api().ping())
        reply = 200 to "не json"
        assertFalse(api().ping())
        assertFalse(HttpRoomsApi("").ping())
    }

    @Test fun networkDownAndNotConfigured() = runBlocking {
        val port = server.address.port
        server.stop(0)
        assertEquals(Api.Err(SharedError.Network), HttpRoomsApi("http://127.0.0.1:$port", Dispatchers.Unconfined).room("r1", creds))
        val off = HttpRoomsApi("  ")
        assertFalse(off.configured)
        assertEquals(Api.Err(SharedError.NotConfigured), off.createRoom("x", 1, "y"))
        assertFalse(HttpRoomsApi("http://127.0.0.1:$port", Dispatchers.Unconfined).ping())
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also { it.start() } // чтобы @After не упал
    }
}
