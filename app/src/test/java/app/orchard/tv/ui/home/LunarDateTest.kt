package app.orchard.tv.ui.home

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class LunarDateTest {
    @Test fun homeAndReopenedPanelShareTheSameDay() {
        val date = LocalDate.of(2026, 10, 24)
        val home = lunarDateText(date)
        repeat(100) {
            org.junit.Assert.assertSame(home, lunarDateText(date))
        }
    }

    @Test fun midnightAndClockCorrectionReplaceTheCachedDay() {
        val before = LocalDate.of(2026, 10, 23)
        val after = before.plusDays(1)
        assertEquals("九月十四 · 霜降", lunarDateText(before))
        assertEquals("九月十五", lunarDateText(after))
        assertEquals("九月十四 · 霜降", lunarDateText(before))
    }

    @Test fun ordinaryDate() {
        assertEquals("九月十五", lunarDateText(LocalDate.of(2026, 10, 24)))
    }

    @Test fun traditionalFestivals() {
        assertEquals("正月初一 · 春节", lunarDateText(LocalDate.of(2026, 2, 17)))
        assertEquals("五月初五 · 端午节", lunarDateText(LocalDate.of(2026, 6, 19)))
        assertEquals("八月十五 · 中秋节", lunarDateText(LocalDate.of(2026, 9, 25)))
    }

    @Test fun newYearsEveInLongAndShortMonths() {
        assertEquals("腊月三十 · 除夕", lunarDateText(LocalDate.of(2024, 2, 9)))
        assertEquals("腊月廿九 · 除夕", lunarDateText(LocalDate.of(2025, 1, 28)))
    }

    @Test fun leapMonthDoesNotRepeatFestival() {
        assertEquals("闰二月初二", lunarDateText(LocalDate.of(2023, 3, 23)))
    }

    @Test fun solarTermDoesNotLeakIntoNextDay() {
        assertEquals("九月十四 · 霜降", lunarDateText(LocalDate.of(2026, 10, 23)))
        assertEquals("九月十五", lunarDateText(LocalDate.of(2026, 10, 24)))
        assertEquals("二月十八 · 清明", lunarDateText(LocalDate.of(2026, 4, 5)))
    }

    // Reference: https://www.hko.gov.hk/tc/gts/time/calendar/text/files/T2026c.txt
    @Test fun allSolarTermsMatchObservatoryCalendar() {
        val expected = mapOf(
            LocalDate.of(2026, 1, 5) to "小寒",
            LocalDate.of(2026, 1, 20) to "大寒",
            LocalDate.of(2026, 2, 4) to "立春",
            LocalDate.of(2026, 2, 18) to "雨水",
            LocalDate.of(2026, 3, 5) to "惊蛰",
            LocalDate.of(2026, 3, 20) to "春分",
            LocalDate.of(2026, 4, 5) to "清明",
            LocalDate.of(2026, 4, 20) to "谷雨",
            LocalDate.of(2026, 5, 5) to "立夏",
            LocalDate.of(2026, 5, 21) to "小满",
            LocalDate.of(2026, 6, 5) to "芒种",
            LocalDate.of(2026, 6, 21) to "夏至",
            LocalDate.of(2026, 7, 7) to "小暑",
            LocalDate.of(2026, 7, 23) to "大暑",
            LocalDate.of(2026, 8, 7) to "立秋",
            LocalDate.of(2026, 8, 23) to "处暑",
            LocalDate.of(2026, 9, 7) to "白露",
            LocalDate.of(2026, 9, 23) to "秋分",
            LocalDate.of(2026, 10, 8) to "寒露",
            LocalDate.of(2026, 10, 23) to "霜降",
            LocalDate.of(2026, 11, 7) to "立冬",
            LocalDate.of(2026, 11, 22) to "小雪",
            LocalDate.of(2026, 12, 7) to "大雪",
            LocalDate.of(2026, 12, 22) to "冬至",
        )
        expected.forEach { (date, term) ->
            org.junit.Assert.assertTrue("$date: $term", lunarDateText(date).split(" · ").contains(term))
        }
    }
}
