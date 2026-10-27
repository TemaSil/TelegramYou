package com.telegramyou.app.telegram.model

/**
 * One of Telegram's privacy settings, as this client offers them: TDLib's
 * name for it, what the row says, and which audiences it can be set to.
 * Telegram does not offer "Nobody" everywhere — finding somebody by their
 * number and adding them to groups stop at contacts.
 */
enum class PrivacySetting(
    val tdType: String,
    private val words: Pair<Pair<String, String>, Pair<String, String>>,
    val audiences: List<PrivacyAudience> = PrivacyAudience.entries
) {
    PhoneNumber(
        "userPrivacySettingShowPhoneNumber",
        ("Phone number" to "Номер телефона") to ("Who can see my phone number" to "Кто видит мой номер")
    ),
    FindByNumber(
        "userPrivacySettingAllowFindingByPhoneNumber",
        ("Find me by my number" to "Поиск по номеру") to ("Who can find me by my number" to "Кто может найти меня по номеру"),
        listOf(PrivacyAudience.Everybody, PrivacyAudience.Contacts)
    ),
    LastSeen(
        "userPrivacySettingShowStatus",
        ("Last seen and online" to "Время захода") to ("Who can see when I was last online" to "Кто видит, когда я был(а) в сети")
    ),
    ProfilePhoto(
        "userPrivacySettingShowProfilePhoto",
        ("Profile photo" to "Фото профиля") to ("Who can see my profile photo" to "Кто видит моё фото")
    ),
    Bio("userPrivacySettingShowBio", ("Bio" to "О себе") to ("Who can see my bio" to "Кто видит «О себе»")),
    Forwards(
        "userPrivacySettingShowLinkInForwardedMessages",
        ("Forwarded messages" to "Пересылка сообщений") to
            ("Who can link to my account when forwarding my messages" to "Кто может ссылаться на мой аккаунт при пересылке"),
    ),
    Calls("userPrivacySettingAllowCalls", ("Calls" to "Звонки") to ("Who can call me" to "Кто может мне звонить")),
    Invites(
        "userPrivacySettingAllowChatInvites",
        ("Groups and channels" to "Группы и каналы") to ("Who can add me to groups and channels" to "Кто может добавлять меня в группы и каналы"),
        listOf(PrivacyAudience.Everybody, PrivacyAudience.Contacts)
    );

    /** The row's name and its dialog's question, in the language in use. */
    val title: String get() = com.telegramyou.app.i18n.tr(words.first.first, words.first.second)
    val question: String get() = com.telegramyou.app.i18n.tr(words.second.first, words.second.second)
}

enum class PrivacyAudience(private val labelEn: String, private val labelRu: String) {
    Everybody("Everybody", "Все"),
    Contacts("My contacts", "Мои контакты"),
    Nobody("Nobody", "Никто");

    /** Read each time, in the language in use. */
    val label: String get() = com.telegramyou.app.i18n.tr(labelEn, labelRu)
}

/**
 * A rule that names people or chats rather than a whole audience — "always
 * allow these three", "never these two chats". This client does not edit
 * them, but it must not lose them: [raw] is the rule exactly as TDLib gave
 * it, written back unchanged whenever the audience is changed here.
 */
data class PrivacyException(val allow: Boolean, val count: Int, val raw: String)

/** A setting's rules, reduced to the audience and the exceptions on top of it. */
data class PrivacyRules(
    val audience: PrivacyAudience,
    val exceptions: List<PrivacyException> = emptyList()
) {
    /** "My contacts", "Everybody (−2)", "Nobody (+3, −1)". */
    val summary: String
        get() {
            val allowed = exceptions.filter { it.allow }.sumOf { it.count }
            val denied = exceptions.filterNot { it.allow }.sumOf { it.count }
            val parts = listOfNotNull(
                allowed.takeIf { it > 0 }?.let { "+$it" },
                denied.takeIf { it > 0 }?.let { "−$it" }
            )
            return if (parts.isEmpty()) audience.label else "${audience.label} (${parts.joinToString(", ")})"
        }
}

/**
 * TDLib's rule list to [PrivacyRules]. The rules are tried in order and the
 * first that matches decides, which is why Telegram writes exceptions first
 * and the audience last; the audience is read from the whole-audience rules
 * and everything naming someone is an exception. [rules] is each rule's
 * type, how many people or chats it names, and its JSON.
 */
fun privacyRulesOf(rules: List<Triple<String, Int, String>>): PrivacyRules {
    val types = rules.map { it.first }
    val audience = when {
        "userPrivacySettingRuleAllowAll" in types -> PrivacyAudience.Everybody
        "userPrivacySettingRuleAllowContacts" in types -> PrivacyAudience.Contacts
        else -> PrivacyAudience.Nobody
    }
    val exceptions = rules.filter { it.first !in AUDIENCE_RULES }.map { (type, count, raw) ->
        PrivacyException(allow = type.startsWith("userPrivacySettingRuleAllow"), count = count, raw = raw)
    }
    return PrivacyRules(audience, exceptions)
}

/**
 * The rule types for [audience], after the exceptions: allow all; allow
 * contacts and refuse the rest; refuse all.
 */
fun audienceRules(audience: PrivacyAudience): List<String> = when (audience) {
    PrivacyAudience.Everybody -> listOf("userPrivacySettingRuleAllowAll")
    PrivacyAudience.Contacts -> listOf("userPrivacySettingRuleAllowContacts", "userPrivacySettingRuleRestrictAll")
    PrivacyAudience.Nobody -> listOf("userPrivacySettingRuleRestrictAll")
}

private val AUDIENCE_RULES = setOf(
    "userPrivacySettingRuleAllowAll",
    "userPrivacySettingRuleAllowContacts",
    "userPrivacySettingRuleRestrictAll"
)
