# TelegramYou

Android Telegram client with a **Material You Expressive–inspired** UI.

## Download

### ⬇ **[Get the APK](https://github.com/TemaSil/TelegramYou/releases/tag/latest)**

That is the release page, rebuilt from `main` on every green push — the APK is
the file attached at the bottom of it.

If you would rather have the file itself:
[TelegramYou-debug.apk](https://github.com/TemaSil/TelegramYou/releases/download/latest/TelegramYou-debug.apk).
The file name stays the same on every build so this link keeps working; the
release title says which build it is.

Installed once, it updates itself: **Settings → About → Check for updates**
fetches the newest build from the same release and hands it to Android's
installer, and a dot on the Settings tab says when there is one.

> Clicked one of these before the first release existed and got **Page not
> found**? The browser cached that 404. Reload the page ignoring the cache
> (Ctrl+Shift+R, or ⌘+Shift+R on a Mac), or open it in a private window.

**It is the live client: sign in with your own Telegram account.** CI builds
it with the project's `api_id` and `api_hash` from the repository's secrets,
and puts TDLib's native libraries in the APK. The keys are never in the
repository itself; see *Credentials* in CLAUDE.md. A fork without those
secrets builds the demo instead — the whole interface, offline, login code
`12345`.

It is debug-signed, so Android will ask whether to allow installing from this
source.

Live Telegram connectivity uses the **official TDLib JSON interface**:
- Docs: https://core.telegram.org/tdlib/getting-started
- Java binding: `org.drinkless.tdlib.JsonClient` (from [tdlib/td](https://github.com/tdlib/td))
- Native: `libtdjsonjava.so` (Android ABIs under `app/src/main/jniLibs/`)

## Screenshots

| Chat list | Conversation | Group | Video messages |
|---|---|---|---|
| <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/03-chats.jpg" width="180" alt="Chat list"> | <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/04-chat.jpg" width="180" alt="Conversation"> | <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/06-group-header.jpg" width="180" alt="Group"> | <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/31-video-note.jpg" width="180" alt="Video messages"> |

| Search | Stories | Attachments | Chat info |
|---|---|---|---|
| <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/36-search.jpg" width="180" alt="Search"> | <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/21-story.jpg" width="180" alt="Stories"> | <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/09-attachments.jpg" width="180" alt="Attachments"> | <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/12-chat-info.jpg" width="180" alt="Chat info"> |

| Privacy | Devices | Data and storage | For geeks |
|---|---|---|---|
| <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/32-privacy.jpg" width="180" alt="Privacy"> | <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/29-devices.jpg" width="180" alt="Devices"> | <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/30-storage.jpg" width="180" alt="Data and storage"> | <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/33-for-geeks.jpg" width="180" alt="For geeks"> |

| QR login | Email login | Proxy | Tablet |
|---|---|---|---|
| <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/26-qr-login.jpg" width="180" alt="QR login"> | <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/27-email-code.jpg" width="180" alt="Email login"> | <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/23-proxy.jpg" width="180" alt="Proxy"> | <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/13-rail.jpg" width="180" alt="Tablet"> |

| Polls | Bot buttons | Post search | Search in chat |
|---|---|---|---|
| <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/34-poll.jpg" width="180" alt="Polls"> | <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/35-bot.jpg" width="180" alt="Bot buttons"> | <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/37-post-search.jpg" width="180" alt="Post search"> | <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/28-old-search-hit.jpg" width="180" alt="Search in chat"> |

Not drawn for this page: they are taken by the **UI** workflow, which drives
the demo client on an emulator after every push to `main`, and they change
the moment a screen does. The same screens from **every** build, newest
first, are in the [**gallery**](https://github.com/TemaSil/TelegramYou/tree/gallery#readme)
— which is where to look to see what a release changed.

## What is in it

Version 1.0. The demo build is the whole interface, so everything here can be
seen without an account:

- **Material You, properly** — the palette comes from the wallpaper (below
  Android 12, or with it switched off, from one teal seed by the same
  algorithm), Material 3 Expressive components and motion throughout, and
  the name set in Google Sans Flex.
- **Signing in** — phone number with the country guessed from the SIM, the
  code sent on its last digit, two-step password, a **QR code** to scan from
  another phone, Telegram's **login email** steps, and a proxy before there
  is an account.
- **Chat list** — folders as tabs with unread counts and as pages you swipe
  between, stories above them, an archive, search across chats and messages,
  and a floating action button that opens into new message, group, channel
  or joining by link.
- **Conversation** — opens at its latest message with the history already
  there; replies, edits, forwarding, selection, reactions, a pinned-message
  bar, an unread divider, and search that jumps to any message however old.
  A floating composer with the camera, the microphone and a sticker sheet.
- **Search** — a section of its own: the people you write to most, recent
  searches and chats found before, channels to try; then chats, messages,
  groups, channels and bots across Telegram, and posts in public channels
  anywhere.
- **Polls and bots** — polls and quizzes answered in the bubble with the
  results drawn as they land, bots' buttons under their messages, and a
  bot's own keyboard above the composer.
- **Media** — photos and videos out to the bubble's edges, GIFs that play by
  themselves, round video messages played in place, voice messages with a
  waveform, stickers (still and animated), a full-screen viewer and a grid
  of a chat's media.
- **Stories** — a viewer with the rail's own segments and timing.
- **Settings** — theme, dynamic colour, shaped avatars and text size;
  per-chat notifications; **privacy** rules; the **devices** signed in, each
  one endable; **data and storage** with the cache cleared by kind; proxies;
  and updates checked for and installed from inside the app.
- **For geeks** — double-tap actions, seconds in message times, message
  details, save and copy media, forwarding without quoting, hiding stories
  or the All tab, search without the keyboard, and IPv6 — all off until turned on.
- **Notifications** — a foreground service, per-chat mute, and replying
  from the shade.
- **Adaptive** — the navigation becomes a rail where the window is wide and
  tall enough for one.

`ROADMAP.md` says what is not in it yet, and which Material component each
part should use when it arrives.

## Modes

| Mode | When | Behavior |
|------|------|----------|
| **Demo** | no credentials, or `-PdemoClient=true` | Offline UI; login code `12345` |
| **TDLib** | real `api_id` + `api_hash` | Full auth → chats → messages → files → stories |

## Getting set up

```bat
gradlew.bat :app:assembleDebug
```

Without credentials that builds the **demo** client: the whole UI, offline,
login code `12345`, no account and no keys needed. It is enough for most work
on the interface.

Copy `local.properties.example` to `local.properties` if you want to point
Android Studio at a particular SDK, or to go live below.

> `local.properties` is git-ignored and must stay that way. An `api_hash`
> **cannot be reissued** at my.telegram.org — commit one and it is public
> permanently, on an account that cannot be detached from it. Everyone keeps
> their own copy locally.

## Native TDLib

`app/src/main/jniLibs/` is empty in a fresh clone — the `.so` files are tens
of megabytes each and are not kept in git. Without them the app still builds
and runs in demo mode; live mode needs them.

They come from this repository's own release, and one command fetches them:

```bat
gradlew.bat :app:fetchTdlib
```

It downloads `tdlib-jnilibs-java.zip` from the pinned `tdlib-java-<sha>`
release, refuses it unless its sha256 matches the one in
`app/build.gradle.kts`, and unpacks every ABI into `app/src/main/jniLibs/`.
A live build — `TELEGRAM_API_ID` set in `local.properties` — runs it by
itself when the libraries are missing, so usually there is nothing to
remember. The demo build never downloads anything.

The release itself is made by Actions → **Build TDLib** → *Run workflow*,
which compiles OpenSSL and TDLib for every Android ABI. It takes a bit over
an hour and only has to be done when TDLib is bumped; the new tag and the
zip's checksum then go into `app/build.gradle.kts` together.

It builds TDLib's **JSONJava** interface, which produces `libtdjsonjava.so` —
the name `System.loadLibrary("tdjsonjava")` in `JsonClient` looks for, and the
one carrying the `Java_org_drinkless_tdlib_JsonClient_*` symbols its native
methods need. A `libtdjson.so` from a plain JSON build (the kind an FFI caller
wants) exports none of those and cannot be substituted: the app would load and
then fail on the first native call.

## Enable live Telegram API

1. Create an application at [my.telegram.org](https://my.telegram.org) → **API development tools**
2. Put credentials into `local.properties`:

```properties
sdk.dir=C:\\Users\\...\\AppData\\Local\\Android\\Sdk
TELEGRAM_API_ID=12345678
TELEGRAM_API_HASH=your_api_hash_here
```

   The same `api_id` and `api_hash` serve any client of yours — one made
   for another project works here too. On a developer's machine they go in
   `local.properties`; on CI, in the repository's `TELEGRAM_API_ID` and
   `TELEGRAM_API_HASH` secrets. Never in a tracked file: the repository is
   public, and the hash cannot be reissued.
3. With a phone connected, `gradlew.bat :app:installDebug`. The first live
   build downloads TDLib's libraries by itself (see *Native TDLib* above),
   and the debug key is the same as the demo APK's, so it installs over it.
4. Sign in with your number; the code arrives in Telegram. Auth flow matches
   TDLib getting-started:
   - `authorizationStateWaitTdlibParameters` → `setTdlibParameters`
   - phone → code → (optional 2FA password) → `authorizationStateReady`
   - then `loadChats` / `getChatHistory` / `sendMessage` / `inputFileLocal`

## Output

APK: `app\build\outputs\apk\debug\TelegramYou-1.0.<build>-debug.apk`

## Project map

```
app/src/main/java/
  org/drinkless/tdlib/JsonClient.java     Official TDLib JSON JNI
  com/telegramyou/app/telegram/tdlib/     TdJsonEngine + TdLibTelegramClient
  com/telegramyou/app/ui/                 Compose Expressive UI
app/src/main/jniLibs/*/libtdjsonjava.so   Native TDLib
```
