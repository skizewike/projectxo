package com.projectxo.app.core.data.photo

import java.io.File
import java.util.UUID

/**
 * Снимки записей еды (Р71). Лежат только в закрытой папке приложения, в облако не уходят.
 * В базе хранится одно имя файла; снимок удаляется вместе с записью.
 */
class FoodPhotoStore(private val dir: File) {

    /** Сохраняет снимок и возвращает имя файла. */
    fun save(jpeg: ByteArray): String {
        dir.mkdirs()
        val name = "${UUID.randomUUID()}.jpg"
        File(dir, name).writeBytes(jpeg)
        return name
    }

    /** Файл снимка или null, если имени нет, оно подозрительное или файла уже нет. Имена вне формата не принимаются. */
    fun file(name: String?): File? {
        if (name == null || !NAME.matches(name)) return null
        return File(dir, name).takeIf { it.isFile }
    }

    fun delete(name: String?) {
        file(name)?.delete()
    }

    /** «Удалить все данные». */
    fun deleteAll() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private companion object {
        val NAME = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg")
    }
}
