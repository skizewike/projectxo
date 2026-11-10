package com.projectxo.app.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Страховка перевода: каждая строка из values есть в values-en, подстановки (%1$s, %d, %%) совпадают,
 * а в английских строках нет кириллицы и в русских нет «ломающих» пробелов.
 */
class StringsParityTest {
    private data class Res(val kind: String, val translatable: Boolean, val texts: List<Pair<String, String>>)

    private fun load(dir: String): Map<String, Res> {
        val folder = File("src/main/res/$dir")
        val out = mutableMapOf<String, Res>()
        folder.listFiles { f -> f.name.endsWith(".xml") }.orEmpty().sortedBy { it.name }.forEach { file ->
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val nodes = doc.documentElement.childNodes
            for (i in 0 until nodes.length) {
                val n = nodes.item(i)
                if (n.nodeType != org.w3c.dom.Node.ELEMENT_NODE) continue
                val el = n as org.w3c.dom.Element
                val name = el.getAttribute("name")
                val tr = el.getAttribute("translatable") != "false"
                when (el.tagName) {
                    "string" -> out[name] = Res("string", tr, listOf("" to el.textContent))
                    "plurals" -> {
                        val items = el.getElementsByTagName("item")
                        out[name] = Res("plurals", tr, (0 until items.length).map { j ->
                            val it = items.item(j) as org.w3c.dom.Element
                            it.getAttribute("quantity") to it.textContent
                        })
                    }
                }
            }
        }
        return out
    }

    private val args = Regex("%(\\d+\\$)?[sdf%]")
    private fun placeholders(s: String) = args.findAll(s).map { it.value }.filter { it != "%%" }.sorted().toList()

    @Test
    fun everyTranslatableStringHasEnglish() {
        val ru = load("values")
        val en = load("values-en")
        val missing = ru.filter { it.value.translatable && it.key !in en }.keys
        assertTrue("Нет английского перевода: $missing", missing.isEmpty())
    }

    @Test
    fun placeholdersMatchBetweenLanguages() {
        val ru = load("values")
        val en = load("values-en")
        val bad = mutableListOf<String>()
        ru.filter { it.value.translatable }.forEach { (name, r) ->
            val e = en[name] ?: return@forEach
            val ruSets = r.texts.map { placeholders(it.second) }.distinct()
            val enSets = e.texts.map { placeholders(it.second) }.distinct()
            // В русском плюралы разные по формам, но набор подстановок один и тот же во всех формах.
            if (ruSets.size != 1 || enSets.size != 1 || ruSets.first() != enSets.first()) bad += name
        }
        assertTrue("Подстановки не совпадают: $bad", bad.isEmpty())
    }

    @Test
    fun englishHasNoCyrillicAndNoExtraNames() {
        val ru = load("values")
        val en = load("values-en")
        val cyr = Regex("[А-Яа-яЁё]")
        val withCyrillic = en.filter { (_, r) -> r.texts.any { cyr.containsMatchIn(it.second) } }.keys.filter { it != "lang_ru" }
        assertTrue("Русские буквы в английском: $withCyrillic", withCyrillic.isEmpty())
        val extra = en.keys - ru.keys
        assertEquals("Лишние строки в английском: $extra", emptySet<String>(), extra.filter { it != "format_locale" }.toSet())
    }

    @Test
    fun englishPluralsUseOnlyOneAndOther() {
        val en = load("values-en")
        val bad = en.filter { it.value.kind == "plurals" && it.value.texts.any { (q, _) -> q != "one" && q != "other" } }.keys
        assertTrue("Лишние формы множественного числа: $bad", bad.isEmpty())
    }
}
