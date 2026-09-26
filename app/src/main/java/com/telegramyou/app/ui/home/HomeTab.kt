package com.telegramyou.app.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
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
enum class HomeTab(val label: String, val icon: ImageVector) {
    Chats("Chats", Icons.AutoMirrored.Rounded.Chat),
    Search("Search", Icons.Rounded.Search),
    // Settings before Profile, with the profile at the end of the bar the
    // way the official client places it — the owner's call.
    Settings("Settings", Icons.Rounded.Settings),
    Profile("Profile", Icons.Rounded.Person)
}
