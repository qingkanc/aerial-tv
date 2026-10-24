package app.orchard.tv.ui.home

import com.nlf.calendar.Solar
import java.time.LocalDate

private var cachedDate: LocalDate? = null
private var cachedText: String? = null

/** The TV's calendar day, including leap months; solar terms follow the Chinese calendar (UTC+8). */
// Shared by Home and Control Center, whose composition is recreated on every opening.
// Keep only one day; a clock correction or midnight replaces it immediately.
@Synchronized
internal fun lunarDateText(date: LocalDate): String {
    if (date == cachedDate) return cachedText!!
    val lunar = Solar.fromYmd(date.year, date.monthValue, date.dayOfMonth).lunar
    val text = buildList {
        add("${lunar.monthInChinese}月${lunar.dayInChinese}")
        lunar.jieQi.takeIf { it.isNotEmpty() }?.let(::add)
        addAll(lunar.festivals)
    }.distinct().joinToString(" · ")
    cachedDate = date
    cachedText = text
    return text
}
