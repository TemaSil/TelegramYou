package com.telegramyou.app.telegram.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A poll being written, before it is sent.
 *
 * Kept as typed — blank options and all — so the form can hold an empty row
 * for the next answer; [cleaned] is what goes to the server.
 */
data class PollDraft(
    val question: String = "",
    val options: List<String> = listOf("", ""),
    val isAnonymous: Boolean = true,
    val allowsMultiple: Boolean = false,
    val isQuiz: Boolean = false,
    /** The right answer's position in [options], for a quiz. */
    val correctOption: Int? = null,
    val explanation: String = ""
) {
    /** The answers actually written, trimmed, in order. */
    val filledOptions: List<String> get() = options.map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * What is wrong with it, or null when it can be sent. One problem at a
     * time, in the order a person would fix them.
     */
    val problem: String?
        get() = when {
            question.isBlank() -> "Ask a question"
            question.trim().length > POLL_QUESTION_LIMIT -> "The question is too long"
            filledOptions.size < 2 -> "Add at least two answers"
            filledOptions.any { it.length > POLL_OPTION_LIMIT } -> "An answer is too long"
            filledOptions.map { it.lowercase() }.toSet().size != filledOptions.size -> "Two answers are the same"
            isQuiz && correctIndex == null -> "Choose the right answer"
            else -> null
        }

    val canSend: Boolean get() = problem == null

    /** Whether another answer row may be added. */
    val canAddOption: Boolean get() = options.size < POLL_OPTION_MAX

    /**
     * The right answer's position among [filledOptions] — blank rows above
     * it are dropped when the poll is sent, which moves it up.
     */
    val correctIndex: Int?
        get() {
            val chosen = correctOption ?: return null
            if (options.getOrNull(chosen)?.isNotBlank() != true) return null
            return options.take(chosen).count { it.isNotBlank() }
        }

    fun withOption(index: Int, text: String): PollDraft {
        val edited = options.toMutableList().also { it[index] = text }
        // A row appears under the last one as soon as it is typed into, the
        // way every poll form works, until the limit.
        if (index == edited.lastIndex && text.isNotBlank() && edited.size < POLL_OPTION_MAX) edited += ""
        return copy(options = edited)
    }

    fun withoutOption(index: Int): PollDraft {
        if (options.size <= 2) return copy(options = options.toMutableList().also { it[index] = "" })
        val correct = correctOption?.let { if (it == index) null else if (it > index) it - 1 else it }
        return copy(options = options.filterIndexed { i, _ -> i != index }, correctOption = correct)
    }

    /** A quiz has one right answer, so it cannot allow several. */
    fun asQuiz(quiz: Boolean): PollDraft =
        copy(isQuiz = quiz, allowsMultiple = if (quiz) false else allowsMultiple, correctOption = if (quiz) correctOption else null)
}

const val POLL_QUESTION_LIMIT = 255
const val POLL_OPTION_LIMIT = 100
const val POLL_OPTION_MAX = 10

/**
 * When a scheduled message will go, as epoch seconds: the day the date
 * picker returned — midnight UTC of that day, which is how Material's picker
 * reports one — at [hour]:[minute] in [zone].
 */
fun scheduleInstant(pickedDateUtcMillis: Long, hour: Int, minute: Int, zone: ZoneId): Long {
    val day = Instant.ofEpochMilli(pickedDateUtcMillis).atZone(ZoneId.of("UTC")).toLocalDate()
    return LocalDateTime.of(day, LocalTime.of(hour, minute)).atZone(zone).toEpochSecond()
}

/**
 * Whether Telegram will take a message scheduled for [at]: in the future,
 * and no more than a year out.
 */
fun isValidSchedule(at: Long, nowSeconds: Long): Boolean =
    at > nowSeconds + SCHEDULE_MIN_LEAD_SECONDS && at <= nowSeconds + SCHEDULE_MAX_AHEAD_SECONDS

const val SCHEDULE_MIN_LEAD_SECONDS = 30L
const val SCHEDULE_MAX_AHEAD_SECONDS = 365L * 24 * 60 * 60

/** "Today at 18:00", "Tomorrow at 09:30", "12 Oct at 09:30". */
fun scheduleLabel(at: Long, nowSeconds: Long, zone: ZoneId, locale: Locale = Locale.ENGLISH): String {
    val then = Instant.ofEpochSecond(at).atZone(zone)
    val today = Instant.ofEpochSecond(nowSeconds).atZone(zone).toLocalDate()
    val time = then.format(DateTimeFormatter.ofPattern("HH:mm", locale))
    val day: LocalDate = then.toLocalDate()
    val dayLabel = when (day) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> if (day.year == today.year) then.format(DateTimeFormatter.ofPattern("d MMM", locale))
        else then.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
    }
    return "$dayLabel at $time"
}

/**
 * A music file sent as one — with a title and a performer, played in the
 * bubble — rather than as a document.
 */
data class AudioContent(
    val title: String,
    val performer: String = "",
    val durationSeconds: Int = 0,
    val fileName: String = "",
    /** TDLib's id for the file, which a download is asked for by. */
    val fileId: Int? = null,
    /** The file on this device, once it is here. */
    val path: String? = null
) {
    /** The title, or the file's name when the file carries no tags. */
    val displayTitle: String get() = title.ifBlank { fileName.substringBeforeLast('.').ifBlank { "Audio" } }
}

/** Extensions sent as music rather than as a plain file. */
private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav")

/** Whether a picked file named [name] should go as audio. */
fun isAudioFileName(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS
