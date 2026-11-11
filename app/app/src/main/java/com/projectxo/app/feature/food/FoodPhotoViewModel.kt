package com.projectxo.app.feature.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projectxo.app.core.data.AppClock
import com.projectxo.app.core.data.FoodDraft
import com.projectxo.app.core.data.FoodRepository
import com.projectxo.app.core.data.SettingsRepository
import com.projectxo.app.core.data.foodai.FoodPhotoAnalyzer
import com.projectxo.app.core.data.foodai.PhotoError
import com.projectxo.app.core.data.foodai.PhotoResult
import com.projectxo.app.core.model.Confidence
import com.projectxo.app.core.model.FoodPhotoMath
import com.projectxo.app.core.model.FoodRules
import com.projectxo.app.core.model.OilChoice
import com.projectxo.app.core.model.PhotoDish
import com.projectxo.app.core.model.PhotoItem
import com.projectxo.app.core.model.PhotoTotals
import com.projectxo.app.core.model.PortionChoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PhotoStep { Pick, Working, Result, Failed }

/** Состояние экрана «Еда по фото». Итоги не хранятся: они считаются из [items]. */
data class FoodPhotoState(
    val step: PhotoStep = PhotoStep.Pick,
    val hasKey: Boolean = false,
    val keyError: Boolean = false,
    val hint: String = "",
    val dish: PhotoDish? = null,
    /** Рабочий список: ответ ИИ плюс правки пользователя. */
    val items: List<PhotoItem> = emptyList(),
    val oil: OilChoice = OilChoice.AsPhoto,
    val portion: PortionChoice = PortionChoice.AsPhoto,
    val customGrams: String = "",
    val editing: Boolean = false,
    val error: PhotoError? = null,
    val notFood: Boolean = false,
    /** Снимок, по которому идёт распознавание: виден на экране ожидания и результата (Р71). */
    val photo: ByteArray? = null,
    /** Снимок ушёл на сервер, идёт распознавание (Р82): до этого экран показывает «Отправляем». */
    val uploaded: Boolean = false,
) {
    val totals: PhotoTotals get() = FoodPhotoMath.totals(items)
    val range: Pair<Int, Int> get() = FoodPhotoMath.range(totals.kcal, dish?.confidence ?: Confidence.Medium)
    val canSave: Boolean get() = totals.kcal > 0
}

/**
 * Еда по фото (Р69, вариант 03 «Уточнение»). Фото распознаёт [analyzer], ответы на вопросы
 * и правки граммов пересчитываются здесь же, без нового запроса. Запись идёт в [appScope], как у обычной еды.
 */
class FoodPhotoViewModel(
    private val analyzer: FoodPhotoAnalyzer,
    private val settings: SettingsRepository,
    private val food: FoodRepository,
    private val clock: AppClock,
    private val appScope: CoroutineScope,
    /** Есть общий посредник (Р70): тогда ключ пользователя не обязателен. */
    private val shared: Boolean = false,
    /** Куда сохранить снимок при записи (Р71). null — запись без фото. */
    private val photos: com.projectxo.app.core.data.photo.FoodPhotoStore? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(FoodPhotoState(hasKey = shared || settings.settings.value.foodAiKey.isNotBlank()))
    val state: StateFlow<FoodPhotoState> = _state.asStateFlow()

    /** Первое блюдо от ИИ: нужно, чтобы «Как на фото» возвращало исходные значения. */
    private var original: List<PhotoItem> = emptyList()
    /** Снимок, по которому получен результат: сохраняется вместе с записью. */
    private var lastJpeg: ByteArray? = null
    private var job: Job? = null
    private var fatName: String = ""

    fun setFatName(name: String) { fatName = name }

    fun setHint(text: String) = _state.update { it.copy(hint = text.take(MAX_HINT)) }

    /** Ключ с пробелами и переводами строк (вставили из письма) чистится; явно неверный не сохраняется. */
    fun saveKey(raw: String) {
        val key = raw.filterNot(Char::isWhitespace)
        if (key.length !in KEY_LENGTH) {
            _state.update { it.copy(keyError = true) }
            return
        }
        settings.update { it.copy(foodAiKey = key) }
        _state.update { it.copy(hasKey = true, keyError = false, error = null, step = PhotoStep.Pick) }
    }

    fun removeKey() {
        settings.update { it.copy(foodAiKey = "") }
        _state.update { it.copy(hasKey = shared, step = PhotoStep.Pick) }
    }

    /** [language] — язык ответа словами; [jpeg] отдаёт готовое фото или null, если открыть его не вышло. */
    fun analyze(language: String, jpeg: suspend () -> ByteArray?) {
        if (_state.value.step == PhotoStep.Working) return
        job?.cancel()
        _state.update { it.copy(step = PhotoStep.Working, error = null, notFood = false, uploaded = false) }
        job = viewModelScope.launch {
            val bytes = jpeg()
            lastJpeg = bytes
            _state.update { it.copy(photo = bytes) }
            if (bytes == null) {
                fail(PhotoError.BadPhoto)
                return@launch
            }
            when (val r = analyzer.analyze(bytes, _state.value.hint, language) { _state.update { it.copy(uploaded = true) } }) {
                is PhotoResult.Ok -> {
                    original = r.dish.items
                    _state.update {
                        it.copy(
                            step = PhotoStep.Result, dish = r.dish, items = r.dish.items, oil = OilChoice.AsPhoto,
                            portion = PortionChoice.AsPhoto, customGrams = "", editing = false,
                        )
                    }
                }
                PhotoResult.NotFood -> _state.update { it.copy(step = PhotoStep.Failed, notFood = true, error = null) }
                is PhotoResult.Fail -> {
                    if (r.error == PhotoError.BadKey) settings.update { it.copy(foodAiKey = "") }
                    fail(r.error)
                }
            }
        }
    }

    /**
     * «Повторить» после ошибки: тот же снимок из памяти. Временный файл камеры к этому моменту уже удалён,
     * поэтому заново читать его нельзя.
     */
    fun retry(language: String) {
        val bytes = lastJpeg
        if (bytes == null) retake() else analyze(language) { bytes }
    }

    private fun fail(error: PhotoError) = _state.update {
        it.copy(step = PhotoStep.Failed, error = error, hasKey = if (error == PhotoError.BadKey) shared else it.hasKey)
    }

    /**
     * Экран закрыт: всё забываем, чтобы следующее открытие началось с выбора фото, а снимок не висел в памяти.
     * Поворот экрана сюда не попадает.
     */
    fun reset() {
        job?.cancel()
        original = emptyList()
        lastJpeg = null
        _state.value = FoodPhotoState(hasKey = shared || settings.settings.value.foodAiKey.isNotBlank())
    }

    /** Назад к выбору фото (подсказка остаётся). */
    fun retake() {
        job?.cancel()
        lastJpeg = null
        _state.update { it.copy(step = PhotoStep.Pick, error = null, notFood = false, photo = null) }
    }

    fun chooseOil(choice: OilChoice) = _state.update { st ->
        val fat = choice.fatG ?: original.filter { it.isAddedFat }.sumOf { it.grams }
        st.copy(oil = choice, items = FoodPhotoMath.withFat(st.items, fat, fatName))
    }

    fun choosePortion(choice: PortionChoice) = _state.update { st ->
        when (choice) {
            PortionChoice.AsPhoto -> st.copy(portion = choice, items = restorePortion(st.items), customGrams = "")
            PortionChoice.Custom -> st.copy(portion = choice)
            else -> st.copy(portion = choice, customGrams = "", items = FoodPhotoMath.withPortion(st.items, choice.grams!!))
        }
    }

    /** «Как на фото»: порция из ответа ИИ, а жир при готовке остаётся тем, что выбрал пользователь. */
    private fun restorePortion(current: List<PhotoItem>): List<PhotoItem> {
        val fat = current.filter { it.isAddedFat }
        return original.filterNot { it.isAddedFat } + fat
    }

    fun setCustomGrams(text: String) = _state.update { st ->
        val digits = text.filter(Char::isDigit).take(4)
        val g = digits.toIntOrNull()
        val ok = g != null && g in FoodPhotoMath.MIN_CUSTOM_G..FoodPhotoMath.MAX_CUSTOM_G
        st.copy(
            customGrams = digits, portion = PortionChoice.Custom,
            items = if (ok) FoodPhotoMath.withPortion(restorePortion(st.items), g) else st.items,
        )
    }

    fun setEditing(on: Boolean) = _state.update { it.copy(editing = on) }

    /** Правка граммов руками: выбранная порция больше не верна, подсветка снимается. */
    fun step(index: Int, sign: Int) = _state.update { st ->
        val item = st.items.getOrNull(index) ?: return@update st
        st.copy(items = FoodPhotoMath.step(st.items, index, sign * FoodPhotoMath.stepSize(item)), portion = PortionChoice.AsPhoto, customGrams = "")
    }

    /** Записать результат в «Еду». Возвращает false, если записывать нечего. */
    fun save(unnamed: String): Boolean {
        val st = _state.value
        if (!st.canSave) return false
        val draft: FoodDraft = FoodPhotoMath.toDraft(st.dish?.name.orEmpty(), st.items, unnamed)
        if (!FoodRules.isValid(draft)) return false
        val at = clock.nowMs()
        val today = clock.today()
        val photo = lastJpeg
        val score = st.dish?.healthScore
        appScope.launch {
            val file = photo?.let { photos?.save(it) }
            food.add(draft.copy(photoFile = file, healthScore = score), at, today)
        }
        return true
    }

    private companion object {
        const val MAX_HINT = 200
        val KEY_LENGTH = 30..80
    }
}
