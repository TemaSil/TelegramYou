package com.telegramyou.app.ui.chat

/**
 * What a chat's header and its row say while someone writes (2.1): in a
 * private chat just "typing", since the chat is the person; in a group who
 * it is, as the official client says it — one name, two, or the first and
 * how many more.
 */
fun typingLabel(names: List<String>): String = when (names.size) {
    0 -> "typing"
    1 -> "${names[0]} is typing"
    2 -> "${names[0]} and ${names[1]} are typing"
    else -> "${names[0]} and ${names.size - 1} others are typing"
}
