package com.telegramyou.app.ui.home

import com.telegramyou.app.ui.icons.Symbols
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The four destinations of Home's bottom bar.
 *
 * Tabs rather than routes, and deliberately: a bottom bar's whole promise is
 * that the four are siblings a tap apart, with no back stack between them.
 * Pushing each onto the navigation graph would give every switch an entry to
 * press Back through, which is not how any other app on the phone behaves.
 *
 * Search is here even though it is not a screen. It expands the search bar
 * over the chat list — the same control the magnifier in the app bar used to
 * open — because a person looking for the search function looks along the
 * bottom bar now, and a bar that omits the thing they are looking for sends
 * them hunting.
 */
// Two icons each, as Material 3 asks of a navigation bar: the outline while
// the tab is not the one open, and the same glyph filled when it is — the
// fill is part of how the bar says where you are.
enum class HomeTab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Chats("Chats", Symbols.Chat, Symbols.ChatFilled),
    Search("Search", Symbols.Search, Symbols.SearchFilled),
    // Settings before Profile, with the profile at the end of the bar the
    // way the official client places it — the owner's call.
    Settings("Settings", Symbols.Settings, Symbols.SettingsFilled),
    Profile("Profile", Symbols.Person, Symbols.PersonFilled)
}
