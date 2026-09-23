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

> Clicked one of these before the first release existed and got **Page not
> found**? The browser cached that 404. Reload the page ignoring the cache
> (Ctrl+Shift+R, or ⌘+Shift+R on a Mac), or open it in a private window.

**It is the demo build, and that is not a limitation of the build — it is the
only one CI can make.** The whole interface is there, the login code is
`12345`, and nothing leaves the phone. The live client needs a Telegram
`api_id` and `api_hash`, which are deliberately not in this repository, and
TDLib's native libraries, which are far too large for git. Building it is a
local job; see below.

It is debug-signed, so Android will ask whether to allow installing from this
source.

Live Telegram connectivity uses the **official TDLib JSON interface**:
- Docs: https://core.telegram.org/tdlib/getting-started
- Java binding: `org.drinkless.tdlib.JsonClient` (from [tdlib/td](https://github.com/tdlib/td))
- Native: `libtdjsonjava.so` (Android ABIs under `app/src/main/jniLibs/`)

## Screenshots

| Chat list | Conversation | Group | Attachments |
|---|---|---|---|
| <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/03-chats.jpg" width="180" alt="Chat list"> | <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/04-chat.jpg" width="180" alt="Conversation"> | <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/06-group-header.jpg" width="180" alt="Group"> | <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/09-attachments.jpg" width="180" alt="Attachments"> |

| Video | Chat info | New group | Tablet |
|---|---|---|---|
| <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/14-video.jpg" width="180" alt="Video"> | <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/12-chat-info.jpg" width="180" alt="Chat info"> | <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/16-new-group.jpg" width="180" alt="New group"> | <img src="https://raw.githubusercontent.com/TemaSil/TelegramYou/gallery/latest/13-rail.jpg" width="180" alt="Tablet"> |

Not drawn for this page: they are taken by the **UI** workflow, which drives
the demo client on an emulator after every push to `main`, and they change
the moment a screen does. The same screens from **every** build, newest
first, are in the [**gallery**](https://github.com/TemaSil/TelegramYou/tree/gallery#readme)
— which is where to look to see what a release changed.

## What is in it

The demo build is the whole interface, so everything here can be seen without
an account:

- **Chat list** — grouped into containers, search across chats and message
  text, an archive behind its own row, and **folders** as tabs with an unread
  badge on each and as pages you swipe between. The header — name, folders,
  stories — scrolls away with the chats and comes back on the way up. Rows
  swipe to pin or mute where there are no folders to swipe between.
- **Conversation** — replies, edits, forwarding, selection, reactions, voice
  messages with a waveform, a pinned-message bar, an unread divider, and a
  search inside the chat.
- **Media** — photos in bubbles, a full-screen viewer with pinch, pan,
  double-tap and drag-to-dismiss, a grid of every photo in a chat, and a
  carousel of recent photos at the top of the attachment sheet.
- **Chat info** — members, the invite link, and leaving a group.
- **Profile and settings** — name, bio and username edited in place; theme and
  dynamic colour.
- **Notifications** — a foreground service, and replying from the shade.
- **Adaptive** — the navigation becomes a rail where the window is wide and
  tall enough for one.

`ROADMAP.md` says what is not in it yet, and which Material component each
part should use when it arrives.

## Modes

| Mode | When | Behavior |
|------|------|----------|
| **Demo** | `TELEGRAM_API_ID=0` (default) | Offline UI; login code `12345` |
| **TDLib** | real `api_id` + `api_hash` | Full auth → chats → messages → files → stories |

## Getting set up

```bat
gradlew.bat :app:assembleDebug
```

That builds the **demo** client: the whole UI, offline, login code `12345`,
no account and no keys needed. It is enough for most work on the interface.

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

Actions → **Build TDLib** → *Run workflow* compiles OpenSSL and TDLib for
every Android ABI and publishes a `tdlib-java-<sha>` release. It takes a bit
over an hour and only has to be done once, or when TDLib is bumped. Then:

```bash
unzip tdlib-jnilibs-java.zip
cp -r jniLibs/* app/src/main/jniLibs/
```

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

3. Sync Gradle → Run. Auth flow matches TDLib getting-started:
   - `authorizationStateWaitTdlibParameters` → `setTdlibParameters`
   - phone → code → (optional 2FA password) → `authorizationStateReady`
   - then `loadChats` / `getChatHistory` / `sendMessage` / `inputFileLocal`

## Output

APK: `app\build\outputs\apk\debug\TelegramYou-0.2.<build>-debug.apk`

## Project map

```
app/src/main/java/
  org/drinkless/tdlib/JsonClient.java     Official TDLib JSON JNI
  com/telegramyou/app/telegram/tdlib/     TdJsonEngine + TdLibTelegramClient
  com/telegramyou/app/ui/                 Compose Expressive UI
app/src/main/jniLibs/*/libtdjsonjava.so   Native TDLib
```
