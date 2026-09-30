<div align="center">

# TelegramYou

**Telegram that looks and behaves like Android.**

Material 3 Expressive, colour from your wallpaper, the platform's own motion —
a Telegram client built from stock Android parts instead of drawn by hand.

[![Latest release](https://img.shields.io/github/v/release/TemaSil/TelegramYou?label=release&style=for-the-badge)](https://github.com/TemaSil/TelegramYou/releases/latest)
![Android 8+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)

### ⬇ [Download the APK](https://github.com/TemaSil/TelegramYou/releases/latest/download/TelegramYou.apk)

<sub>or open the <a href="https://github.com/TemaSil/TelegramYou/releases/latest">latest release</a> ·
sign in with your own Telegram account · updates itself from Settings → About</sub>

<br>

<img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/03-chats.jpg" width="240" alt="Chat list">&nbsp;
<img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/04-chat.jpg" width="240" alt="Conversation">&nbsp;
<img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/57b-panel-expanded.jpg" width="240" alt="Emoji, GIFs and stickers">

</div>

---

## Why

The official Android client draws its entire interface by hand — hundreds of
custom views, no Material components — and now imitates another platform's
glass. Whatever it looks like, it is never Android.

TelegramYou is the other bet. **You** is Material You: the palette comes from
your wallpaper, so the app looks like *your* phone. The components are the
ones every other Android app uses, with their metrics, state layers and
accessibility. Custom drawing is kept for the few things Material has no
answer to — the voice waveform, delivery ticks, the typing indicator and the
chat wallpaper.

## What it does

<table>
<tr>
<td width="50%" valign="top">

### 💬 Conversations
Replies, edits, forwarding and multi-select with Material checkboxes.
Reactions — including Telegram's animated and Premium ones. Pinned
messages, an unread divider, search that jumps to any message however old,
and swipe to reply. Animated emoji, Premium custom emoji in the text,
formatting, link previews, polls and quizzes, bot buttons and keyboards,
scheduled messages.

</td>
<td width="50%" valign="top">

### 😀 Emoji, GIFs, stickers
One panel in the keyboard's place — Android's own emoji picker, GIF search
and your saved GIFs, sticker sets with animated and video stickers — and it
pulls up to three quarters of the screen by its handle. A lone emoji is
sent big.

</td>
</tr>
<tr>
<td valign="top">

### 🖼 Media
Photos and videos out to the bubble's edges that sharpen from a blurred
preview as they load, a full-screen viewer you swipe through, GIFs and round
video messages that play in place, voice messages with a waveform, music,
files, contacts and places as cards.

</td>
<td valign="top">

### 🎨 Yours
Dynamic colour or one of eight accents, light, dark and pure black, chat
colours taken from the other person's photo, nine wallpapers, three tones
for your messages, bubble corners and message text size, shaped avatars,
two-line previews and a calmer motion setting.

</td>
</tr>
<tr>
<td valign="top">

### 🗂 Chat list
Folders as tabs you swipe between — created, edited and reordered in the
app — stories above them, an archive, and a search section of its own:
people you write to, recent searches, chats, messages, channels, bots and
posts across Telegram.

</td>
<td valign="top">

### 🔒 Private
App lock with a PIN or your fingerprint, hidden from recent apps. Privacy
rules, blocked people, every signed-in device endable, data and storage
cleared by kind, proxies, per-chat notifications and replies from the shade.

</td>
</tr>
</table>

Signing in takes a phone number (the country guessed from the SIM), a QR
code from another phone, Telegram's login email and two-step passwords. On
a wide screen the navigation becomes a rail.

## New in 1.6.8

- **The player as a bottom sheet** — Material's modal sheet, opened straight to full height, closed by its handle, a pull down or Back.
- **Swipe the mini player away** to stop the music, as a notification is swiped away. Swiping it to change track, new in 1.6.7, read as dismissing it on a phone.
- **A smoother composer** — the reply banner and the attachment chip fold away rather than vanish, so the mini player and the capsule settle instead of jumping.
- **The player's buttons as an Expressive button group** — the one under the finger widens and the others give way.
- **Mini player at the top** (Appearance → Music), off by default — for whoever preferred it under the title, as until 1.6.7.
- **Fixed:** a track sent with a caption drew the caption over the track.

## New in 1.6.7

- **The mini player at the foot of the screen** — over the navigation bar, and over the composer in a chat, where the thumb is. Swipe it left for the next track and right for the one before. A voice message keeps its bar at the top, now with the same thin progress line and a speed button that sits level.
- **The player rises from below** and falls back when pulled down, in place of the container transform of 1.6.6.
- **Open chat goes to the track** — from the player or the library, the chat opens at the track's own message, lit for a moment.
- **The cover breathes** — on the beat it morphs a little into a scalloped square from Material's shape library and settles back, instead of the rippling edges that read as twitching.
- **Large titles for Music and Search**, folding as the page scrolls, as Settings has.

## New in 1.6.6

- **The player in the shade and on the lock screen** — it never was: the media session was not the playback service's own, and Android shows only the service's.
- **The player opens out of the mini player** as one container, the way a chat opens out of its row, and **closes by pulling it down** from anywhere on it.
- **A slimmer mini player** — its progress is a thin line along the bottom edge instead of a wave on a row of its own.
- **Titles on one line** — a long one runs across rather than wrapping, so the controls no longer move from track to track.
- **Music library** (For geeks → Experiments) — a Music tab on the bottom bar, "My music" opens it, and Saved Messages leads its front page.
- **A quieter connection notice** — the "connected" notification Android requires for the background connection is at minimum importance: no status-bar icon, folded at the foot of the shade, and it no longer comes back each time the app opens.

## New in 1.6.5

- **Fixed: the app closing when a track started or paused.** The player's cover springs slightly past its size as it plays; that spring was on its padding, which cannot go below zero. It is a scale now.

## New in 1.6.4

- **Up next** — Play next and Add to queue on any track, from a chat, My music or the library, before the queue goes on.
- **Voice messages, properly** — a run of them plays one after another, keeps going when you leave the chat with a bar to pause, change speed or stop, and 1×, 1.5× or 2× is remembered.
- **No gaps** — the next track is fetched while this one plays.
- **A player shaped like Material means it** — play is the wide one, previous and next squared-off beside it, repeat and speed as pills, the sleep timer and the queue as chips. The cover's corners are squarer, and its edges ripple a little on the beat (Appearance → Motion to turn it off).
- **Last crash** (For geeks → Diagnostics) — if the app closes on its own, what happened is kept on the phone to read and copy into a report. Never sent anywhere.
- **Music library** (For geeks → Experiments) — every chat's music as albums, artists and playlists, what people in your chats sent, and a reaction onto a track's message from the player.

## New in 1.6.3

- **Shared media in tabs** — media, files, music, voice, links and GIFs, back to the first thing sent; files open in the app that reads them.
- **Downloads** — everything you downloaded in one place, on the main menu: pause, resume or cancel, a notification while it runs, save a copy to the phone's Downloads, and files cleared from storage stay listed to fetch again.
- **A music player** in Material 3 Expressive: a mini player that follows you, a full player in each track's own colours, and the chat's whole music as a queue that never jumps.
- **My music** — every chat's tracks in one place, searchable, and a Music tab in search.
- **Play it your way** — in order, reversed or shuffled, repeat one or all, 0.5× to 2×; an album plays as it was posted, and shuffle and repeat are in the shade and on the lock screen too.
- **Music makes room** — it turns down under a voice message or a round video and comes back after, and pauses while you record or watch a video full screen, then plays on.
- **Sleep timer, equaliser, Saved Messages as a library, offline** — save a track and play on from your saved music; a whole chat's music onto the phone; long tracks carry on where they stopped.

## New in 1.6.2

- **Music files** play with a slider to drag anywhere in the track.
- **Your own story** in the stories row, opening like anyone's; a picked video previews before posting.

## New in 1.6.1

- **Admins and permissions** — admins with the rights and title you choose; stop someone writing or remove them; what members may do; a search over the members.
- **Invite links** with a name, a time limit, a number of people or admin approval — and join requests.
- **Forum topics** — a forum opens onto its topics; read, write, start, rename, close; a draft per topic.

## New in 1.6

- **Post your own story** from *My story* — a photo or a video, for everyone, contacts or close friends.
- **Video speed** — 0.5×, 1.5× or 2×, kept for the next video.
- **Picture-in-picture** — a video keeps playing in a small window when you leave.
- **Calmer chat backgrounds** — Dots, Sparkles or Grid, and a solid top bar.

## New in 1.5

- **App lock** — a PIN or your fingerprint in front of the app.
- **Contacts and places** as cards; send a contact from the paperclip.
- **Group invite links** — share, revoke, create.
- **Animated emoji**, Premium custom emoji in text, *Add to GIFs*.
- **Wallpapers** in a grid, with Zigzag, Crosses, Rings and Sparkles.
- **Selecting messages** the Material way — checkboxes and the whole row.
- **A taller emoji panel**, larger reactions, blurred previews while media loads.

Every release and its notes: [Releases](https://github.com/TemaSil/TelegramYou/releases).

## Screenshots

| Music player | Queue | Shared files |
|:---:|:---:|:---:|
| <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/78-player.jpg" width="240" alt="Music player"> | <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/79-queue.jpg" width="240" alt="Queue"> | <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/76-shared-files.jpg" width="240" alt="Shared files"> |

| Chat background | App lock | Selecting |
|:---:|:---:|:---:|
| <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/52c-appearance-chat-background.jpg" width="240" alt="Chat background"> | <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/62-app-lock.jpg" width="240" alt="App lock"> | <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/65-selection.jpg" width="240" alt="Selecting messages"> |

| Contacts and places | Group | Search |
|:---:|:---:|:---:|
| <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/63-contact-and-place.jpg" width="240" alt="Contacts and places"> | <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/06-group-header.jpg" width="240" alt="Group"> | <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/36-search.jpg" width="240" alt="Search"> |

| Forum topics | Admins | Invite links |
|:---:|:---:|:---:|
| <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/73-forum-topics.jpg" width="240" alt="Forum topics"> | <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/70-group-members.jpg" width="240" alt="Admins"> | <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/72-invite-links.jpg" width="240" alt="Invite links"> |

| Polls | Profile | Settings |
|:---:|:---:|:---:|
| <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/34-poll.jpg" width="240" alt="Polls"> | <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/42-profile.jpg" width="240" alt="Profile"> | <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/41-settings.jpg" width="240" alt="Settings"> |

None of these is drawn for this page. The **UI** workflow drives the app on
an emulator after every push to `main` and photographs what it sees, so they
change the moment a screen does. The same screens from **every** build are in
the [**gallery**](https://github.com/TemaSil/TelegramYou/tree/gallery#readme).

## Installing

1. Download [**TelegramYou.apk**](https://github.com/TemaSil/TelegramYou/releases/latest/download/TelegramYou.apk)
   and open it; Android asks once whether to allow installing from this source.
2. Sign in with your Telegram account — it is the live client, talking to
   Telegram through the official [TDLib](https://core.telegram.org/tdlib).
3. Later versions arrive by themselves: **Settings → About → Check for
   updates**, and a dot on the Settings tab says when there is one.

For testing there is also
[TelegramYou-debug.apk](https://github.com/TemaSil/TelegramYou/releases/download/latest/TelegramYou-debug.apk),
rebuilt from `main` on every green push, with an offline demo inside. It
installs beside the release as a separate app.

> A download link says **Page not found**? The browser cached a 404 from
> before the release existed — reload ignoring the cache (Ctrl+Shift+R, or
> ⌘+Shift+R on a Mac) or use a private window.

---

## For developers

The stack is deliberately the newest: Kotlin 2.4, Jetpack Compose,
`material3` 1.5 alpha (the only way to reach Material 3 Expressive), Android
Gradle plugin 9. `ROADMAP.md` maps what a complete client contains onto the
Material component each part should use; `ARCHITECTURE.md` describes the
layers; `CLAUDE.md` holds the working rules.

<details>
<summary><b>Building</b></summary>

```bat
gradlew.bat :app:assembleDebug
```

Without credentials that builds the **demo** client: the whole interface,
offline, login code `12345`, no account and no keys. It is enough for most
work on the interface. The APK lands at
`app\build\outputs\apk\debug\TelegramYou-<version>-debug.apk`.

The pure-Kotlin module needs neither the SDK nor Google's Maven:

```
gradlew :core:check --configure-on-demand
```

| Mode | When | Behaviour |
|------|------|-----------|
| **Demo** | no credentials, or `-PdemoClient=true` | Offline UI; login code `12345` |
| **TDLib** | `api_id` + `api_hash` configured | Live Telegram |

</details>

<details>
<summary><b>Going live</b></summary>

1. Create an application at [my.telegram.org](https://my.telegram.org) →
   **API development tools**.
2. Copy `local.properties.example` to `local.properties` and fill in:

   ```properties
   TELEGRAM_API_ID=12345678
   TELEGRAM_API_HASH=your_api_hash_here
   ```

3. With a phone connected, `gradlew.bat :app:installDebug`. The first live
   build downloads TDLib's libraries by itself.

> `local.properties` is git-ignored and must stay that way. An `api_hash`
> **cannot be reissued** — commit one and it is public for good. On CI the
> keys live only in the repository's `TELEGRAM_API_ID` and
> `TELEGRAM_API_HASH` secrets; a fork without them builds the demo.

</details>

<details>
<summary><b>Native TDLib</b></summary>

`app/src/main/jniLibs/` is empty in a fresh clone — the `.so` files are tens
of megabytes each. One command fetches them from this repository's pinned
`tdlib-java-<sha>` release, checks the sha256 and unpacks every ABI:

```bat
gradlew.bat :app:fetchTdlib
```

A live build runs it by itself when the libraries are missing; the demo never
downloads anything. The release is made by Actions → **Build TDLib**, which
compiles OpenSSL and TDLib for every Android ABI in a bit over an hour.

It builds TDLib's **JSONJava** interface — `libtdjsonjava.so`, which
`org.drinkless.tdlib.JsonClient` loads. A plain JSON build's `libtdjson.so`
cannot be substituted.

</details>

<details>
<summary><b>Project map</b></summary>

```
core/src/main/kotlin/                      plain Kotlin, no Android, tested
app/src/main/java/
  org/drinkless/tdlib/JsonClient.java      official TDLib JSON JNI binding
  com/telegramyou/app/
    telegram/tdlib/                        live backend
    telegram/demo/                         offline backend
    ui/                                    Compose screens
app/src/main/jniLibs/<abi>/libtdjsonjava.so
```

</details>
