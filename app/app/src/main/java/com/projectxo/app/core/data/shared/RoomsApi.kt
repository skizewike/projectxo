package com.projectxo.app.core.data.shared

import com.projectxo.app.core.model.SharedError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/** Ответ сервера комнат: успех со значением или понятная ошибка. */
sealed interface Api<out T> {
    data class Ok<T>(val value: T) : Api<T>
    data class Err(val error: SharedError) : Api<Nothing>
}

/** Доступ к комнате: номер участника и секрет, которые сервер выдал при создании или входе. */
data class Credentials(val memberId: String, val secret: String)

data class CreatedRoom(val roomId: String, val creds: Credentials, val code: String, val codeExpiresMs: Long)
data class JoinedRoom(val roomId: String, val creds: Credentials, val title: String, val freqMask: Int, val members: List<MemberDto>)
data class MemberDto(val id: String, val nick: String)
data class MarkDto(val memberId: String, val day: LocalDate)
data class RoomDto(val roomId: String, val title: String, val freqMask: Int, val members: List<MemberDto>, val marks: List<MarkDto>)
data class CodeDto(val code: String, val expiresMs: Long)

/** Протокол сервера `server/habit-rooms` (Р79). */
interface RoomsApi {
    val configured: Boolean

    /** Отвечает ли сервер. Раздел «Вместе» показывается, только если да. */
    suspend fun ping(): Boolean

    suspend fun createRoom(title: String, freqMask: Int, nick: String): Api<CreatedRoom>
    suspend fun join(code: String, nick: String): Api<JoinedRoom>
    suspend fun room(roomId: String, creds: Credentials): Api<RoomDto>
    suspend fun putMark(roomId: String, creds: Credentials, day: LocalDate, done: Boolean): Api<Unit>
    suspend fun newCode(roomId: String, creds: Credentials): Api<CodeDto>
    suspend fun leave(roomId: String, creds: Credentials): Api<Unit>
}

/** Обычный HTTP без лишних библиотек, как у «Еды по фото». [baseUrl] пуст — режим «Вместе» выключен. */
class HttpRoomsApi(
    baseUrl: String,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : RoomsApi {
    private val base = baseUrl.trim().trimEnd('/')
    override val configured: Boolean get() = base.isNotEmpty()

    private class Reply(val code: Int, val body: String)

    private fun send(method: String, path: String, body: JSONObject?, creds: Credentials?): Reply {
        val conn = URL(base + path).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = method
            conn.connectTimeout = CONNECT_MS
            conn.readTimeout = READ_MS
            conn.setRequestProperty("Accept", "application/json")
            if (creds != null) {
                conn.setRequestProperty("x-member-id", creds.memberId)
                conn.setRequestProperty("x-secret", creds.secret)
            }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..399) conn.inputStream else conn.errorStream
            Reply(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun <T> call(
        method: String,
        path: String,
        body: JSONObject? = null,
        creds: Credentials? = null,
        join: Boolean = false,
        parse: (JSONObject) -> T,
    ): Api<T> {
        if (!configured) return Api.Err(SharedError.NotConfigured)
        return withContext(io) {
            try {
                val r = send(method, path, body, creds)
                when {
                    r.code in 200..299 -> {
                        val json = if (r.body.isBlank()) JSONObject() else JSONObject(r.body)
                        Api.Ok(parse(json))
                    }
                    r.code == 400 -> Api.Err(SharedError.BadInput)
                    // 401 и 404 значат «комнаты нет» только в ответе нашего сервера (JSON с полем error).
                    // Страница-заглушка хостинга, пока сервер не выложен или лежит, не должна стирать комнату на телефоне.
                    r.code == 401 || r.code == 404 ->
                        if (!isOurError(r.body)) Api.Err(SharedError.Server) else Api.Err(if (join) SharedError.BadCode else SharedError.Gone)
                    r.code == 409 -> Api.Err(SharedError.RoomFull)
                    r.code == 429 -> Api.Err(SharedError.TooMany)
                    else -> Api.Err(SharedError.Server)
                }
            } catch (e: IOException) {
                Api.Err(SharedError.Network)
            } catch (e: org.json.JSONException) {
                Api.Err(SharedError.Server)
            }
        }
    }

    override suspend fun ping(): Boolean {
        if (!configured) return false
        return withContext(io) {
            try {
                val r = send("GET", "/health", null, null)
                r.code == 200 && JSONObject(r.body).optBoolean("ok", false)
            } catch (e: IOException) {
                false
            } catch (e: org.json.JSONException) {
                false
            }
        }
    }

    override suspend fun createRoom(title: String, freqMask: Int, nick: String) =
        call("POST", "/v1/rooms", JSONObject().put("title", title).put("freqMask", freqMask).put("nick", nick)) { j ->
            CreatedRoom(j.getString("roomId"), Credentials(j.getString("memberId"), j.getString("secret")), j.getString("code"), j.getLong("codeExpires"))
        }

    override suspend fun join(code: String, nick: String) =
        call("POST", "/v1/join", JSONObject().put("code", code).put("nick", nick), join = true) { j ->
            JoinedRoom(
                j.getString("roomId"), Credentials(j.getString("memberId"), j.getString("secret")),
                j.getString("title"), j.getInt("freqMask"), members(j),
            )
        }

    override suspend fun room(roomId: String, creds: Credentials) =
        call("GET", "/v1/rooms/$roomId", creds = creds) { j ->
            val room = j.getJSONObject("room")
            val marks = j.getJSONArray("marks")
            RoomDto(
                room.getString("id"), room.getString("title"), room.getInt("freqMask"), members(j),
                (0 until marks.length()).mapNotNull { i ->
                    val m = marks.getJSONObject(i)
                    runCatching { MarkDto(m.getString("member"), LocalDate.parse(m.getString("day"))) }.getOrNull()
                },
            )
        }

    override suspend fun putMark(roomId: String, creds: Credentials, day: LocalDate, done: Boolean) =
        call("PUT", "/v1/rooms/$roomId/marks", JSONObject().put("day", day.toString()).put("value", if (done) 1 else 0), creds) { }

    override suspend fun newCode(roomId: String, creds: Credentials) =
        call("POST", "/v1/rooms/$roomId/code", JSONObject(), creds) { j -> CodeDto(j.getString("code"), j.getLong("codeExpires")) }

    override suspend fun leave(roomId: String, creds: Credentials) = call("DELETE", "/v1/rooms/$roomId/me", null, creds) { }

    private fun isOurError(body: String): Boolean = runCatching { JSONObject(body).has("error") }.getOrDefault(false)

    private fun members(j: JSONObject): List<MemberDto> {
        val arr = j.getJSONArray("members")
        return (0 until arr.length()).map { i -> arr.getJSONObject(i).let { MemberDto(it.getString("id"), it.getString("nick")) } }
    }

    private companion object {
        const val CONNECT_MS = 10_000
        const val READ_MS = 15_000
    }
}
