package com.telegramyou.app.telegram.model

/**
 * One answer in a poll, with how the vote stands on it.
 *
 * [percentage] is the server's own rounding, which is chosen so the options
 * add up to a hundred — naive rounding of three equal thirds gives 99. It is
 * only recomputed here, by [pollPercentages], for the moment between our tap
 * and the server's answer.
 */
data class PollOption(
    val text: String,
    val voterCount: Int = 0,
    val percentage: Int = 0,
    /** Whether we are among [voterCount]. */
    val isChosen: Boolean = false
)

/**
 * A poll, as a message carries it.
 *
 * Options are addressed by their position: that is what `setPollAnswer`
 * takes, even though newer TDLib also gives each option a string id.
 */
data class PollContent(
    val id: Long,
    val question: String,
    val options: List<PollOption>,
    val totalVoters: Int = 0,
    val isAnonymous: Boolean = true,
    val allowsMultiple: Boolean = false,
    val allowsRevoting: Boolean = true,
    /** A quiz has right answers and cannot be answered twice. */
    val isQuiz: Boolean = false,
    /**
     * The right answers, by position. Telegram only reveals them once we
     * have answered, so this is empty before that.
     */
    val correctOptions: Set<Int> = emptySet(),
    /** What a quiz says after it has been answered; often empty. */
    val explanation: String = "",
    val isClosed: Boolean = false
) {
    val hasVoted: Boolean get() = options.any { it.isChosen }

    /** Results are shown once we have voted, and to everyone once it closes. */
    val showsResults: Boolean get() = hasVoted || isClosed

    /**
     * Whether our vote can be taken back. Never for a quiz — its answer is
     * final — and not in a poll created with revoting turned off.
     */
    val canRetract: Boolean get() = hasVoted && !isClosed && !isQuiz && allowsRevoting

    /** Whether a tap on an option should do anything at all. */
    val acceptsVotes: Boolean get() = !isClosed && !hasVoted

    /** "Anonymous poll", "Quiz", "Poll is closed" — the line under the question. */
    val kindLabel: String
        get() = when {
            isClosed -> "Final results"
            isQuiz -> if (isAnonymous) "Anonymous quiz" else "Quiz"
            else -> if (isAnonymous) "Anonymous poll" else "Public poll"
        }

    /** "No votes yet", "1 vote", "12 votes". */
    val votersLabel: String
        get() = when (totalVoters) {
            0 -> if (isQuiz) "No answers yet" else "No votes yet"
            1 -> if (isQuiz) "1 answer" else "1 vote"
            else -> "$totalVoters ${if (isQuiz) "answers" else "votes"}"
        }

    /**
     * This poll as it will look once our vote for [chosen] has counted.
     *
     * Drawn straight away, because the server takes a visible moment to
     * answer and a tap that changes nothing until then reads as a missed
     * one. An empty [chosen] takes our vote back. Whatever the server then
     * sends replaces this entirely, so a guess that was off by someone
     * else's vote in the meantime corrects itself.
     */
    fun withVote(chosen: Set<Int>): PollContent {
        val counts = options.mapIndexed { index, option ->
            val was = if (option.isChosen) 1 else 0
            val now = if (index in chosen) 1 else 0
            (option.voterCount - was + now).coerceAtLeast(0)
        }
        val voters = totalVoters - (if (hasVoted) 1 else 0) + (if (chosen.isNotEmpty()) 1 else 0)
        val percentages = pollPercentages(counts, voters)
        return copy(
            options = options.mapIndexed { index, option ->
                option.copy(
                    voterCount = counts[index],
                    percentage = percentages[index],
                    isChosen = index in chosen
                )
            },
            totalVoters = voters.coerceAtLeast(0)
        )
    }
}

/**
 * Whole percentages for [counts] that add up to exactly a hundred, by the
 * largest-remainder method — the one Telegram's own clients use, so a poll
 * reads the same here as on anyone else's phone.
 *
 * All zeros when nobody has voted. A poll that allows several answers is
 * the exception: there each option is a share of the [voters], the shares
 * can add up to more than a hundred, and each is simply rounded.
 */
fun pollPercentages(counts: List<Int>, voters: Int = counts.sum()): List<Int> {
    val total = counts.sum()
    if (total <= 0 || voters <= 0) return counts.map { 0 }
    if (total != voters) return counts.map { Math.round(it * 100.0 / voters).toInt() }
    val exact = counts.map { it * 100.0 / total }
    val floors = exact.map { it.toInt() }.toMutableList()
    var left = 100 - floors.sum()
    // Ties go to the earlier option, which keeps the result stable when a
    // poll is redrawn.
    exact.indices
        .sortedWith(compareByDescending<Int> { exact[it] - floors[it] }.thenBy { it })
        .forEach { index ->
            if (left > 0 && counts[index] > 0) {
                floors[index] += 1
                left -= 1
            }
        }
    return floors
}

/**
 * What a bot's inline button does when pressed.
 *
 * Only what can be done from a chat without a bot platform behind it is
 * modelled; everything else — games, payments, inline queries, Mini Apps
 * opened in place — is [Unsupported] and drawn disabled, so the button is still there to be
 * read and the message keeps its shape.
 */
sealed interface ButtonAction {
    data class OpenUrl(val url: String) : ButtonAction

    /**
     * Sends [data] back to the bot. Kept exactly as TDLib gave it — base64,
     * since the field is bytes — and handed back unchanged.
     */
    data class Callback(val data: String) : ButtonAction
    data class CopyText(val text: String) : ButtonAction
    data object Unsupported : ButtonAction
}

data class InlineButton(val text: String, val action: ButtonAction)

/** One key of a bot's keyboard under the composer. */
data class ReplyKey(
    val text: String,
    /**
     * Whether pressing it sends its text. The keys that share a phone
     * number, a location or pick a chat are shown but not pressable.
     */
    val sendsText: Boolean = true
)

/**
 * A bot's own keyboard, shown in place of — or beside — the ordinary one.
 *
 * A chat has at most one, and it belongs to the chat rather than a message:
 * Telegram keeps it until the bot replaces or removes it.
 */
data class ReplyKeyboard(
    val rows: List<List<ReplyKey>>,
    val placeholder: String = "",
    /** The bot asked for it to go away after one press. */
    val oneTime: Boolean = false
) {
    val isEmpty: Boolean get() = rows.all { it.isEmpty() }
}

/**
 * The bot's reply to a pressed button: a line to show, which may need to
 * be dismissed by hand when [showAlert] is set, and perhaps a link to open.
 */
data class CallbackAnswer(
    val text: String = "",
    val showAlert: Boolean = false,
    val url: String = ""
)
