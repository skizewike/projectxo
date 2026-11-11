package com.projectxo.app.core.data.foodai

import com.projectxo.app.core.model.PhotoDish

/** Что пошло не так при распознавании. Тексты берутся из ресурсов по этому значению. */
enum class PhotoError { NoKey, BadKey, Quota, Network, BadAnswer, Busy, /** Снимок не открылся или не декодировался. */ BadPhoto }

sealed interface PhotoResult {
    data class Ok(val dish: PhotoDish) : PhotoResult
    /** На фото нет еды. */
    data object NotFood : PhotoResult
    data class Fail(val error: PhotoError) : PhotoResult
}

/**
 * Распознавание блюда по фото (Р69). Интерфейс отделён от Gemini: провайдера можно сменить,
 * а в тестах подставить заглушку.
 */
interface FoodPhotoAnalyzer {
    /**
     * @param jpeg фото, уже уменьшенное
     * @param hint подсказка пользователя «что внутри», может быть пустой
     * @param language язык ответа словами, например «Russian»
     */
    suspend fun analyze(jpeg: ByteArray, hint: String, language: String): PhotoResult

    /**
     * То же, но [onUploaded] вызывается, когда снимок целиком ушёл на сервер и остаётся ждать ответ (Р82:
     * до этого момента экран показывает «Отправляем», после него «Считаем»). Заглушки без сети сигнала не дают.
     */
    suspend fun analyze(jpeg: ByteArray, hint: String, language: String, onUploaded: () -> Unit): PhotoResult =
        analyze(jpeg, hint, language)
}
