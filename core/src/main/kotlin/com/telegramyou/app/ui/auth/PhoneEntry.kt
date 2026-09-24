package com.telegramyou.app.ui.auth

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale

/** A country in the picker: its flag, its name and its calling code. */
data class PhoneCountry(
    /** ISO 3166 region, "RU". */
    val region: String,
    val name: String,
    /** Without the plus, "7". */
    val callingCode: Int,
    val flag: String
)

/**
 * Everything the phone field knows about phone numbers, from Google's
 * libphonenumber — the same data Android's dialer and Telegram's own clients
 * format with.
 *
 * The field holds [normalize]d text: a plus and at most fifteen digits, the
 * longest a phone number can be. What is drawn is [format]ted as it is typed
 * — "+7 912 345-67-89" — through a visual transformation, so the text the
 * view model keeps and sends is never full of spaces and dashes. [region]
 * gives the flag beside it.
 *
 * Telegram's clients split this into a country field, a code field and a
 * number field. One field with the flag in it is the Material shape of the
 * same thing, and it takes a pasted "+44 20 …" whole.
 */
object PhoneEntry {

    private val util: PhoneNumberUtil = PhoneNumberUtil.getInstance()

    /** The most digits an E.164 number can have. */
    const val MAX_DIGITS = 15

    /** A plus and the digits of [text], however it was typed or pasted. */
    fun normalize(text: String): String =
        "+" + text.filter(Char::isDigit).take(MAX_DIGITS)

    /**
     * [raw] as it should be drawn while being typed: "+79123456789" becomes
     * "+7 912 345-67-89", and a partial "+7912" becomes "+7 912".
     */
    fun format(raw: String): String {
        val digits = raw.filter(Char::isDigit)
        if (digits.isEmpty()) return "+"
        // "ZZ" is libphonenumber's unknown region: the number starts with a
        // plus, so the country comes from the number rather than from a guess.
        val formatter = util.getAsYouTypeFormatter("ZZ")
        formatter.inputDigit('+')
        var out = "+"
        for (digit in digits) out = formatter.inputDigit(digit)
        return out
    }

    /**
     * The country [raw] belongs to, or null while that is not yet known.
     *
     * A complete number is placed exactly — +7 701 … is Kazakhstan, not
     * Russia, though both dial +7. Until it is complete, the calling code's
     * main country stands in, which is what Telegram's own field shows too.
     */
    fun region(raw: String): String? {
        val digits = raw.filter(Char::isDigit)
        if (digits.isEmpty()) return null
        try {
            val number = util.parse("+$digits", "ZZ")
            util.getRegionCodeForNumber(number)
                ?.takeIf { it != UNKNOWN }
                ?.let { return it }
        } catch (_: NumberParseException) {
            // Too short to parse yet; the calling code below still says a lot.
        }
        for (length in 1..minOf(3, digits.length)) {
            val region = util.getRegionCodeForCountryCode(digits.take(length).toInt())
            if (region != UNKNOWN) return region
        }
        return null
    }

    /**
     * Whether [raw] is long enough to be a number anywhere its calling code
     * is used — the bar for sending it. Deliberately not "valid": a number
     * range newer than this copy of the library would be refused for nothing.
     */
    fun isPossible(raw: String): Boolean = try {
        util.isPossibleNumber(util.parse(normalize(raw), "ZZ"))
    } catch (_: NumberParseException) {
        false
    }

    /** What the field starts with for someone in [region]: "+7", or "+". */
    fun startingValue(region: String?): String {
        val code = region?.uppercase()?.let(util::getCountryCodeForRegion) ?: 0
        return if (code == 0) "+" else "+$code"
    }

    /**
     * [raw] with its calling code swapped for [country]'s, keeping what was
     * typed after it — picking a country from the list after typing the
     * number should not throw the number away.
     */
    fun withCountry(raw: String, country: PhoneCountry): String {
        val digits = raw.filter(Char::isDigit)
        val current = region(raw)?.let(util::getCountryCodeForRegion)?.toString()
        val national = if (current != null && digits.startsWith(current)) {
            digits.removePrefix(current)
        } else {
            ""
        }
        return normalize("+${country.callingCode}$national")
    }

    /** Every country with a calling code, by name in [locale]. */
    fun countries(locale: Locale = Locale.getDefault()): List<PhoneCountry> =
        util.supportedRegions
            .map { region ->
                PhoneCountry(
                    region = region,
                    name = Locale("", region).getDisplayCountry(locale).ifBlank { region },
                    callingCode = util.getCountryCodeForRegion(region),
                    flag = flag(region)
                )
            }
            .sortedBy { it.name.lowercase(locale) }

    /** A region's flag, drawn by the emoji font from two regional letters. */
    fun flag(region: String): String {
        if (region.length != 2 || !region.all { it.uppercaseChar() in 'A'..'Z' }) return ""
        return region.uppercase().map { letter ->
            String(Character.toChars(REGIONAL_A + (letter - 'A')))
        }.joinToString("")
    }

    /**
     * Where the caret after [rawOffset] characters of [raw] lands in the
     * [formatted] text. Digits are what the two share, so it is counted in
     * digits; the separators the formatter adds are stepped over.
     */
    fun toFormatted(raw: String, formatted: String, rawOffset: Int): Int {
        val digitsBefore = raw.take(rawOffset).count(Char::isDigit)
        if (digitsBefore == 0) return minOf(rawOffset, 1, formatted.length)
        var seen = 0
        formatted.forEachIndexed { index, char ->
            if (char.isDigit() && ++seen == digitsBefore) return index + 1
        }
        return formatted.length
    }

    /** The reverse of [toFormatted], for a caret the finger put in [formatted]. */
    fun toRaw(raw: String, formatted: String, formattedOffset: Int): Int {
        val digitsBefore = formatted.take(formattedOffset).count(Char::isDigit)
        if (digitsBefore == 0) return minOf(formattedOffset, 1, raw.length)
        return minOf(1 + digitsBefore, raw.length)
    }

    private const val UNKNOWN = "ZZ"
    private const val REGIONAL_A = 0x1F1E6
}

/**
 * Where the login code went, in words, from TDLib's `authenticationCodeType`.
 *
 * The screen used to print the type's own name — "Enter the code from
 * Telegram (authenticationCodeTypeTelegramMessage)" — which is TDLib talking
 * to itself. [phone] is shown formatted.
 */
fun codeDeliveryText(codeType: String, phone: String): String {
    val number = if (phone.isBlank()) "your phone" else PhoneEntry.format(phone)
    return when (codeType) {
        "authenticationCodeTypeTelegramMessage" ->
            "We've sent the code to Telegram on your other device"
        "authenticationCodeTypeSms",
        "authenticationCodeTypeSmsWord",
        "authenticationCodeTypeSmsPhrase",
        "authenticationCodeTypeFirebaseAndroid",
        "authenticationCodeTypeFirebaseIos" -> "We've sent an SMS with the code to $number"
        "authenticationCodeTypeCall" -> "We're calling $number to read you the code"
        "authenticationCodeTypeFlashCall",
        "authenticationCodeTypeMissedCall" ->
            "We'll call $number — the code is in the number that calls"
        "authenticationCodeTypeFragment" -> "The code is in Fragment, for your anonymous number"
        else -> "Enter the code sent to $number"
    }
}

/** "0:25" — what the resend button waits for. */
fun countdownLabel(seconds: Int): String =
    "%d:%02d".format(seconds.coerceAtLeast(0) / 60, seconds.coerceAtLeast(0) % 60)
