# What to build, measured against Nekogram

[Nekogram](https://github.com/Nekogram/Nekogram) is the official Telegram for
Android plus roughly eighty patches. It was read here as a **map of what a
complete client contains** — not as something to borrow from. Measurements
below are from a clone of `master`, September 2026.

## Two findings that decide the approach

**Nekogram cannot be re-skinned into Material Design 3.** There is no Material
layer in it to retheme:

| | Nekogram |
|---|---|
| `com.google.android.material` imports | **0** |
| `androidx.compose` imports | **0** |
| XML layouts in the whole app | **22** |
| Files extending `View` / `ViewGroup` | **694** |
| Java | 3 027 files, 1 754 291 lines |
| C/C++ (ffmpeg, BoringSSL, tgcalls, ExoPlayer) | 4 675 files |

Telegram draws its interface by hand on `Canvas`. Nearly nothing is
declarative, and no Material component is used anywhere. "Make it Material 3"
is therefore not a theming job — it is writing the interface from scratch,
which is exactly what this project already does.

**Its code cannot be copied here.** Nekogram is **GPL-2.0**; using its source
would put this repository under GPL-2.0 too. Ideas travel, lines do not.

So Nekogram's value here is the inventory below, and its behaviour as a
reference when ours has to match.

## What a complete client turns out to contain

129 screens, by area:

| Area | Screens |
|---|---|
| Miscellaneous (pickers, intros, dialogs, widgets) | 61 |
| Settings | 18 |
| Groups and channels | 15 |
| Profile and contacts | 10 |
| Payments, Premium, bots | 6 |
| Media and stickers | 7 |
| Login and security | 7 |
| Chats and conversation | 3 |
| Calls | 2 |

The three conversation screens are misleading: `ChatActivity` alone is tens of
thousands of lines. Most of the work in a client is in very few places.

## Where this project stands

22 Kotlin files, 3 038 lines. `AuthScreen`, `HomeScreen`, `ChatScreen`,
`StoryViewerScreen`, plus `ChatListRow`, `AvatarBubble`, `StoriesRail` and the
theme. TDLib is wired through `JsonClient`; the demo backend covers the UI
offline.

That is the skeleton of the first column below and nothing of the rest.

## The plan, in Material 3 components

Only stock Material 3 — no hand-rolled substitutes for something the library
already ships.

### 1. Conversation — the screen that matters most

| Needed | Material 3 |
|---|---|
| Message list, own vs other | `LazyColumn`, `Surface` with asymmetric `RoundedCornerShape`, `MaterialTheme.colorScheme.primaryContainer` / `surfaceContainer` |
| Composer | `OutlinedTextField` or `TextField`, `IconButton`, `FloatingActionButton` for send |
| Attachment sheet | `ModalBottomSheet` |
| Message actions | `ModalBottomSheet` or `DropdownMenu` |
| Reactions | `FilterChip` row, `AssistChip` |
| Reply / edit banner | `Surface` + `HorizontalDivider` |
| Selection mode | `TopAppBar` swapped for a contextual one, `Checkbox` |
| Unread divider, jump to latest | `HorizontalDivider` with label, small `FloatingActionButton` |
| Pinned message bar | `Surface` under the `TopAppBar` |
| Date separators | `Surface` pill, `labelSmall` |

### 2. Chat list

| Needed | Material 3 |
|---|---|
| Rows | `ListItem` — leading avatar, overline, trailing timestamp |
| Unread badge | `Badge` |
| Swipe actions | `SwipeToDismissBox` |
| Folders | `PrimaryScrollableTabRow` or `FilterChip` row |
| Search | `SearchBar` / `DockedSearchBar` |
| Archive entry | `ListItem` above the list |
| Compose | `FloatingActionButton` |
| Navigation | `NavigationBar`, or `NavigationSuiteScaffold` to adapt to tablets |

### 3. Settings and profile

`Scaffold` + `LargeTopAppBar` with `TopAppBarScrollBehavior`, `ListItem` rows,
`Switch`, `Slider`, `SegmentedButton`, `AlertDialog`, `ModalBottomSheet`.
Material 3 covers this area completely; nothing custom is warranted.

### 4. Media

`AsyncImage` (Coil) in `LazyVerticalGrid`, full-screen viewer as a `Dialog`,
`LinearProgressIndicator` / `CircularProgressIndicator` for transfers,
`Slider` for audio and video position.

### Where custom drawing is actually justified

Only where Material has no equivalent and Telegram genuinely has the thing:
the voice waveform, delivery ticks, the typing indicator, the chat wallpaper.
Everything else should be a stock component.

## Order

1. **Conversation.** Send, receive, replies, editing, deletion, media in
   bubbles. Everything else is worth less until this is right.
2. **Chat list.** Folders, archive, search, swipe actions.
3. **Settings and profile**, which is almost entirely `ListItem` and `Switch`.
4. **Media**: viewer, shared media, downloads.
5. **Notifications**, without which it is not a messenger you can leave closed.
6. **Groups and channels**: members, permissions, invite links.

Not planned: calls (they need `tgcalls`, a second native stack TDLib does not
carry), payments, Premium, and Telegram Business.

## Nekogram's own additions

Cheap once the base holds, and worth having: a configurable double-tap action,
message details, forward without quoting, copy photo / save file / open in
browser, time with seconds, no number rounding, hide stories, hide the
all-chats tab, show RPC errors, prefer IPv6, confirm before sending a voice
message. Its `NekoConfig` carries about sixty such switches.

Blocked on a base we do not have: translation and auto-translate, voice
transcription, a tablet layout, markdown parser options, QR login.

## One thing to check before starting

`app/build.gradle.kts` pins Compose BOM `2025.02.00`. The Material 3
Expressive components — `ButtonGroup`, `FloatingToolbar`, `LoadingIndicator`,
`SplitButton`, the motion schemes — arrived in `material3` 1.4 and are not in
the version that BOM resolves. Bump the BOM and confirm what it gives before
designing around anything Expressive. (Maven was not reachable from the
environment this was written in, so the current version is unverified here.)
