package com.telegramyou.app.telegram.model

/**
 * Reporting a message to Telegram (2.0), which is a short conversation
 * with the server rather than a form: asked with nothing chosen, it answers
 * with the reasons it accepts; a reason chosen may open further ones, or ask
 * for a few words; and then it is done. Each answer is one of these.
 */
sealed interface ReportStep {
    /** Pick one of [options]; [title] is the server's question. */
    data class Choose(val title: String, val options: List<ReportOption>) : ReportStep

    /** Say why in words — required unless [optional] — for the reason [optionId]. */
    data class Explain(val optionId: String, val optional: Boolean) : ReportStep

    /** Reported. */
    data object Done : ReportStep
}

/** A reason Telegram accepts, by the id it is answered with (base64, as TDLib's bytes travel). */
data class ReportOption(val id: String, val text: String)
