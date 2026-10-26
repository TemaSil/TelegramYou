package com.telegramyou.app.telegram.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BirthdaysTest {

    private val today = LocalDate.of(2026, 10, 26)

    @Test
    fun aDateReadsWithItsYearOnlyWhenShown() {
        assertEquals("14 March 1994", Birthdate(14, 3, 1994).label)
        assertEquals("14 March", Birthdate(14, 3).label)
    }

    @Test
    fun theAgeTurnsOnTheDayItself() {
        val born = Birthdate(26, 10, 1990)
        assertEquals(36, born.ageOn(today))
        assertEquals(35, born.ageOn(today.minusDays(1)))
        assertNull(Birthdate(26, 10).ageOn(today))
        assertEquals("26 October 1990 · 36 years old", born.line(today))
        assertEquals("26 October", Birthdate(26, 10).line(today))
    }

    @Test
    fun theTwentyNinthOfFebruaryComesRoundOnTheTwentyEighth() {
        val leapling = Birthdate(29, 2)
        assertTrue(leapling.fallsOn(LocalDate.of(2027, 2, 28)))
        assertFalse(leapling.fallsOn(LocalDate.of(2028, 2, 28)))
        assertTrue(leapling.fallsOn(LocalDate.of(2028, 2, 29)))
    }

    @Test
    fun theCardNamesTodayFirstThenTomorrow() {
        val lina = BirthdayPerson(11, 2, "Lina Park", Birthdate(26, 10))
        val artem = BirthdayPerson(12, 5, "Artem S", Birthdate(27, 10))
        val today = birthdayCard(listOf(lina, artem), this.today)!!
        assertTrue(today.isToday)
        assertEquals("It's Lina's birthday today", today.title)
        assertEquals(listOf(lina), today.people)

        val tomorrow = birthdayCard(listOf(artem), this.today)!!
        assertFalse(tomorrow.isToday)
        assertEquals("Artem's birthday is tomorrow", tomorrow.title)

        assertNull(birthdayCard(listOf(BirthdayPerson(13, 6, "Mira", Birthdate(1, 1))), this.today))
    }

    @Test
    fun manyBirthdaysAreCountedNotListed() {
        val people = listOf("Lina Park", "Artem S", "Mira Solano", "Pavel Gromov").mapIndexed { i, name ->
            BirthdayPerson(i.toLong(), i.toLong(), name, Birthdate(26, 10))
        }
        assertEquals("Lina and Artem have birthdays today", birthdayCard(people.take(2), today)!!.title)
        assertEquals("Lina, Artem and 2 more have birthdays today", birthdayCard(people, today)!!.title)
    }

    @Test
    fun aDismissedCardIsForgottenTheNextDay() {
        assertEquals("birthdays-2026-10-26", birthdayCardKey(today))
        assertTrue(birthdayCardKey(today) != birthdayCardKey(today.plusDays(1)))
    }

    @Test
    fun eachBirthdayIsAnnouncedOnceOnItsDay() {
        val lina = BirthdayPerson(11, 2, "Lina Park", Birthdate(26, 10))
        val artem = BirthdayPerson(12, 5, "Artem S", Birthdate(27, 10))
        assertEquals(listOf(lina), birthdaysToAnnounce(listOf(lina, artem), today, emptySet()))
        val announced = setOf(birthdayNoticeKey(lina.userId, today))
        assertEquals(emptyList<BirthdayPerson>(), birthdaysToAnnounce(listOf(lina, artem), today, announced))
        // Next year's is another day's notice.
        val nextYear = today.plusYears(1)
        assertEquals(listOf(lina), birthdaysToAnnounce(listOf(lina), nextYear, announced))
        assertEquals("It's Lina's birthday today", birthdayNoticeTitle(lina))
    }
}
