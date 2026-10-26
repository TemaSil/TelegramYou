package com.telegramyou.app.telegram.model

import java.time.LocalDate

/**
 * A birthday as Telegram keeps it: the day and month always, the year only
 * when the person chose to show it.
 */
data class Birthdate(val day: Int, val month: Int, val year: Int? = null) {

    /** "14 March 1994", or "14 March" without a year. */
    val label: String
        get() = buildString {
            append(day).append(' ').append(MONTHS[(month - 1).coerceIn(0, 11)])
            year?.let { append(' ').append(it) }
        }

    /** How old they are on [date]; null without a year. */
    fun ageOn(date: LocalDate): Int? {
        val born = year ?: return null
        val hadItThisYear = date.monthValue > month || (date.monthValue == month && date.dayOfMonth >= day)
        return (date.year - born - if (hadItThisYear) 0 else 1).takeIf { it >= 0 }
    }

    /**
     * Whether [date] is the birthday. The 29th of February is kept on the
     * 28th in a year without one, so it still comes round.
     */
    fun fallsOn(date: LocalDate): Boolean =
        (date.monthValue == month && date.dayOfMonth == day) ||
            (month == 2 && day == 29 && !date.isLeapYear && date.monthValue == 2 && date.dayOfMonth == 28)

    /** What a profile's row says: the date, and the age when it is known. */
    fun line(today: LocalDate): String {
        val age = ageOn(today) ?: return label
        return "$label · $age ${if (age == 1) "year" else "years"} old"
    }

    private companion object {
        val MONTHS = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
    }
}

/**
 * A contact whose birthday is near, as TDLib announces them
 * (`updateContactCloseBirthdays`): who, the chat to write to them in, and
 * the date.
 */
data class BirthdayPerson(
    val userId: Long,
    val chatId: Long,
    val name: String,
    val birthdate: Birthdate,
    val avatarColor: Long = userId,
    val photoPath: String? = null
)

/** What the chat list's birthday card says, and about whom. */
data class BirthdayCard(val title: String, val people: List<BirthdayPerson>, val isToday: Boolean)

/**
 * The card for [today], as the official client shows one over the chat
 * list: today's birthdays, or — when there are none — tomorrow's, so there
 * is a day to get ready. Null when neither has any.
 */
fun birthdayCard(people: List<BirthdayPerson>, today: LocalDate): BirthdayCard? {
    val todays = people.filter { it.birthdate.fallsOn(today) }
    if (todays.isNotEmpty()) return BirthdayCard(birthdayTitle(todays, "today"), todays, isToday = true)
    val tomorrows = people.filter { it.birthdate.fallsOn(today.plusDays(1)) }
    if (tomorrows.isNotEmpty()) return BirthdayCard(birthdayTitle(tomorrows, "tomorrow"), tomorrows, isToday = false)
    return null
}

private fun birthdayTitle(people: List<BirthdayPerson>, day: String): String {
    val names = people.map { firstNameOf(it.name) }
    return when (names.size) {
        1 -> if (day == "today") "It's ${names[0]}'s birthday today" else "${names[0]}'s birthday is tomorrow"
        2 -> "${names[0]} and ${names[1]} have birthdays $day"
        else -> "${names[0]}, ${names[1]} and ${names.size - 2} more have birthdays $day"
    }
}

/** The key a dismissed card is remembered by: one day's, so tomorrow's still shows. */
fun birthdayCardKey(today: LocalDate): String = "birthdays-$today"

/**
 * Today's birthdays not yet announced in the shade, so each person is
 * announced once on their day however often the list comes round;
 * [announced] holds the [birthdayNoticeKey]s already posted.
 */
fun birthdaysToAnnounce(people: List<BirthdayPerson>, today: LocalDate, announced: Set<String>): List<BirthdayPerson> =
    people.filter { it.birthdate.fallsOn(today) && birthdayNoticeKey(it.userId, today) !in announced }

/** One person's notice on one day; see [birthdaysToAnnounce]. */
fun birthdayNoticeKey(userId: Long, today: LocalDate): String = "$today:$userId"

/** The notice's own words, one person's: the card's, for them alone. */
fun birthdayNoticeTitle(person: BirthdayPerson): String = "It's ${firstNameOf(person.name)}'s birthday today"
