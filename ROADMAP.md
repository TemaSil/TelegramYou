# What to build, measured against Nekogram

[Nekogram](https://github.com/Nekogram/Nekogram) is the official Telegram for
Android plus roughly eighty patches. It was read here as a **map of what a
complete client contains** — not as something to borrow from. Measurements
below are from a clone of `master`, September 2026.

Tick a box only when the thing works in the app, not when the code exists.

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

## What a complete client turns out to contain

129 screens: 61 miscellaneous (pickers, intros, widgets), 18 settings, 15
groups and channels, 10 profile and contacts, 7 media, 7 login and security,
6 payments and bots, 3 conversation, 2 calls. The three conversation screens
are misleading — `ChatActivity` alone is tens of thousands of lines. Most of
the work in a client sits in very few places, which is why the order below
starts where it does.

## Stack

Kotlin + Jetpack Compose + Material 3. These are not alternatives to one
another: Kotlin is the language, Compose is the UI toolkit written in it. The
alternative would be XML and Views, which is what Telegram uses and what rules
its code out as a reference for anything but behaviour.

Use stock Material 3 components. Custom drawing is justified only where
Material has no equivalent and Telegram genuinely has the thing.

- [ ] Bump the Compose BOM — `2025.02.00` predates `material3` 1.4, so
      `ButtonGroup`, `FloatingToolbar`, `LoadingIndicator`, `SplitButton` and
      the motion schemes are unavailable. Confirm what the new one resolves
      before designing around anything Expressive.

## Where this was left, 14 September 2026

Eight of the nineteen conversation items are in and CI is green on every
commit. The next two are blocked on the same thing, which is the first thing
to do:

**`TelegramClient` can only send.** There is no `deleteMessage` and no
`editMessage`, so the long-press menu — which already exists and holds Reply
and Copy — has nothing to put beside them. Adding those two to the interface,
both backends and the menu is the next change, and it is the first one made to
enable a feature rather than to draw one.

After that, in order: swipe-to-reply (UI only, closes the `[~]` on Reply), then
reactions, which needs another client method again.

One thing to check: the **Build TDLib** run started this evening was still
compiling OpenSSL after 43 minutes. If it finished, it published a
`tdlib-java-<sha>` release; unpack it into `app/src/main/jniLibs/` and live
mode works. If it failed, the log will say where.

## 1. Conversation

The screen everything else depends on. 366 lines today: a `TopAppBar`, a
`LazyColumn` of bubbles, a `TextField` composer.

- [x] Message list, own vs other — `LazyColumn`, `Surface`
- [x] Composer with send — `TextField`, `IconButton`
- [x] Attachment draft chip
- [x] Bubble shape: asymmetric `RoundedCornerShape`, tail on the last of a run
- [x] Date separators — `Surface` pill, `labelSmall`
- [x] Sender name and avatar in groups
- [x] Delivery state — `Icons.Rounded.Done` / `DoneAll`; Material ships both, so nothing is drawn by hand
- [~] Reply: banner over the composer and quoted block in bubble; swipe-to-reply still missing
- [ ] Edit and delete — `DropdownMenu` or `ModalBottomSheet` on long press
- [ ] Reactions — `FilterChip` row under the bubble, picker in a sheet
- [~] Copy via long-press `DropdownMenu`; forward and select still missing
- [ ] Attachment sheet — `ModalBottomSheet` with gallery, camera, file
- [ ] Photos and video in bubbles, full-screen viewer as a `Dialog`
- [ ] Voice messages: record on hold, play with a waveform (custom draw)
- [ ] Unread divider and jump-to-latest `FloatingActionButton`
- [ ] Pinned message bar — `Surface` under the `TopAppBar`
- [ ] Typing indicator (custom draw)
- [ ] Link previews — `Card` under the text
- [ ] In-chat search with jump to the hit
- [ ] Load older messages on scroll

## 2. Chat list

- [x] Rows — `ListItem`
- [x] Stories rail
- [ ] Unread badge — `Badge`
- [ ] Swipe actions: mute, pin, archive, delete — `SwipeToDismissBox`
- [ ] Folders — `PrimaryScrollableTabRow`, from the account's own folders
- [ ] Archive: entry row and its own screen
- [ ] Search — `SearchBar`, server-side across chats and messages
- [ ] Compose — `FloatingActionButton` into a contact picker
- [ ] Pin, mute, mark read from a long-press `DropdownMenu`
- [ ] Adaptive navigation — `NavigationSuiteScaffold` for tablets

## 3. Settings and profile

Material 3 covers this area completely; nothing custom is warranted.

- [ ] Settings list — `Scaffold`, `LargeTopAppBar`, `ListItem`, `Switch`
- [ ] Profile: view and edit name, bio, username
- [ ] Appearance: theme, dynamic colour, text size — `SegmentedButton`, `Slider`
- [ ] Notifications settings
- [ ] Privacy, active sessions, sign out
- [ ] Language — Russian and English
- [ ] Data and storage, cache size

## 4. Media

- [ ] Image loading — Coil `AsyncImage`
- [ ] Shared media grid — `LazyVerticalGrid`
- [ ] Full-screen viewer with zoom and drag-to-dismiss
- [ ] Download and upload progress — `LinearProgressIndicator`
- [ ] Audio and video playback — `Slider` for position
- [ ] Stickers, animated stickers, custom emoji

## 5. Notifications

Without these it is not a messenger you can leave closed.

- [ ] Foreground service holding the TDLib connection
- [ ] A notification per chat, tap opens the conversation
- [ ] Mute respected, open chat stays silent
- [ ] Reply from the notification

## 6. Groups and channels

- [ ] Member list
- [ ] Join and leave
- [ ] Permissions and admins
- [ ] Invite links
- [ ] Create a group or channel

## Not planned

Calls need `tgcalls`, a second native stack TDLib does not carry. Payments,
Premium and Telegram Business are out of scope.

## Nekogram's own additions

Cheap once the base holds; its `NekoConfig` carries about sixty switches.

- [ ] Configurable double-tap action
- [ ] Message details — date, id, sender
- [ ] Forward without quoting
- [ ] Copy photo, save file, open in browser
- [ ] Time with seconds, no number rounding
- [ ] Hide stories, hide the all-chats tab
- [ ] Show RPC errors, prefer IPv6

Blocked on a base we do not have: translation and auto-translate, voice
transcription, a tablet layout, markdown parser options, QR login.

## Infrastructure

- [x] TDLib wired through `JsonClient`, demo backend for offline work
- [x] Build TDLib workflow (`JSONJava`) publishing the native libraries
- [ ] Unpack a built `libtdjsonjava.so` into `app/src/main/jniLibs/`
- [x] CI that builds the APK on push
- [x] Tests over the pure logic (message grouping); backends still untested
