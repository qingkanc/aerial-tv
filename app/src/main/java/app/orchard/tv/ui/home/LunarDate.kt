package app.orchard.tv.ui.home

import com.nlf.calendar.Solar
import java.time.LocalDate

/** The TV's calendar day, including leap months; solar terms follow the Chinese calendar (UTC+8). */
internal fun lunarDateText(date: LocalDate): String {
    val lunar = Solar.fromYmd(date.year, date.monthValue, date.dayOfMonth).lunar
    return buildList {
        add("${lunar.monthInChinese}月${lunar.dayInChinese}")
        lunar.jieQi.takeIf { it.isNotEmpty() }?.let(::add)
        addAll(lunar.festivals)
    }.distinct().joinToString(" · ")
}
