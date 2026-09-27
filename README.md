# TelegramYou

Android Telegram client with a **Material You Expressive–inspired** UI.

## Download

### ⬇ **[Get TelegramYou](https://github.com/TemaSil/TelegramYou/releases/latest)**

That is the newest release — the APK is the file attached at the bottom of
it, or straight to the file:
[TelegramYou.apk](https://github.com/TemaSil/TelegramYou/releases/latest/download/TelegramYou.apk).
Releases are made when a version is ready, not on every push, and each keeps
its own page (`v1.1`, `v1.2`…).

For testing there is also
[TelegramYou-debug.apk](https://github.com/TemaSil/TelegramYou/releases/download/latest/TelegramYou-debug.apk),
rebuilt from `main` on every green push: slower, with the demo in it, signed
with a debug key. It installs beside the release as a separate app.

Installed once, either updates itself: **Settings → About → Check for
updates** fetches the newest of its own kind — the release from the newest
release, the debug build from the newest push — and hands it to Android's
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

Android will ask whether to allow installing from this source.

Live Telegram connectivity uses the **official TDLib JSON interface**:
- Docs: https://core.telegram.org/tdlib/getting-started
- Java binding: `org.drinkless.tdlib.JsonClient` (from [tdlib/td](https://github.com/tdlib/td))
- Native: `libtdjsonjava.so` (Android ABIs under `app/src/main/jniLibs/`)

## Screenshots

| Chats | Conversation | Group |
|---|---|---|
| <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/03-chats.jpg" width="250" alt="Chats"> | <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/04-chat.jpg" width="250" alt="Conversation"> | <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/06-group-header.jpg" width="250" alt="Group"> |

| Search | Profile | Settings |
|---|---|---|
| <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/36-search.jpg" width="250" alt="Search"> | <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/42-profile.jpg" width="250" alt="Profile"> | <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/41-settings.jpg" width="250" alt="Settings"> |

Not drawn for this page: they are taken by the **UI** workflow, which drives
the demo client on an emulator after every push to `main`, and they change
the moment a screen does. The same screens from **every** build, newest
first, are in the [**gallery**](https://github.com/TemaSil/TelegramYou/tree/gallery#readme),
with every other screen too — which is where to look to see what a
release changed.

## What is in it

Version 1.1. The demo build is the whole interface, so everything here can be
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
- **Polls and bots** — polls and quizzes written and answered in the
  bubble with the results drawn as they land, messages scheduled for later,
  music files played in place, bots' buttons under their messages, and a
  bot's own keyboard above the composer.
- **Media** — photos and videos out to the bubble's edges, GIFs that play by
  themselves, round video messages played in place, voice messages with a
  waveform, stickers (still and animated), a full-screen viewer and a grid
  of a chat's media.
- **Stories** — a viewer with the rail's own segments and timing.
- **Settings** — laid out like Android's own, in rounded groups, icons
  only where a row opens another screen: theme, dynamic colour, shaped avatars
  and text size; per-chat notifications; **privacy** rules; the **devices**
  signed in, each one endable; **data and storage** with the cache cleared
  by kind; proxies; and an **App update** screen, like Android's System
  update, saying in a few lines what the incoming update brings.
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

APK: `app\build\outputs\apk\debug\TelegramYou-<version>-debug.apk`

## Project map

```
app/src/main/java/
  org/drinkless/tdlib/JsonClient.java     Official TDLib JSON JNI
  com/telegramyou/app/telegram/tdlib/     TdJsonEngine + TdLibTelegramClient
  com/telegramyou/app/ui/                 Compose Expressive UI
app/src/main/jniLibs/*/libtdjsonjava.so   Native TDLib
```
