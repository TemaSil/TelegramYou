package com.telegramyou.app.ui.chat

import com.telegramyou.app.i18n.tr

/**
 * What a chat's header and its row say while someone writes (2.1): in a
 * private chat just "typing", since the chat is the person; in a group who
 * it is, as the official client says it — one name, two, or the first and
 * how many more.
 */
fun typingLabel(names: List<String>): String = when (names.size) {
    0 -> tr("typing", "печатает")
    1 -> tr("${names[0]} is typing", "${names[0]} печатает")
    2 -> tr("${names[0]} and ${names[1]} are typing", "${names[0]} и ${names[1]} печатают")
    else -> tr("${names[0]} and ${names.size - 1} others are typing", "${names[0]} и ещё ${names.size - 1} печатают")
}
