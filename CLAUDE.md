# TelegramYou

Android Telegram client in Kotlin and Jetpack Compose, styled after
**Material You Expressive**. Talks to Telegram through the official TDLib
JSON interface.

## This is not the Flutter client

There is a sibling project, **TelegramAss**, which is a separate Flutter
client with an iOS-styled interface. The two share no code and are worked on
in separate sessions. Nothing here should be changed to match it, and work
meant for it does not belong in this repository — if a request is about
Liquid Glass, Cupertino widgets or Dart, it is about the other project.

## Layout

```
app/src/main/java/
  org/drinkless/tdlib/JsonClient.java      official TDLib JSON JNI binding
  com/telegramyou/app/
    TelegramYouApp.kt                      picks the backend from BuildConfig
    telegram/tdlib/TdLibTelegramClient.kt  live backend
    telegram/demo/DemoTelegramClient.kt    offline backend
    ui/                                    Compose screens
app/src/main/jniLibs/<abi>/libtdjsonjava.so
```

minSdk 26, targetSdk 35, applicationId `com.telegramyou.app`.

## Building

```bat
gradlew.bat :app:assembleDebug
```

APK lands at `app\build\outputs\apk\debug\TelegramYou-0.1.0-debug.apk`.

With no credentials configured the app builds the **demo** backend: the whole
UI offline, login code `12345`, no account needed. That covers most interface
work, so do not assume a task needs live credentials.

## Native TDLib

`app/src/main/jniLibs/` is empty in a fresh clone; the `.so` files are far too
big for git. Demo mode does not need them. Live mode does, and they come from
the **Build TDLib** workflow, which is run by hand and takes over an hour.

It builds the **JSONJava** interface on purpose. `JsonClient` calls
`System.loadLibrary("tdjsonjava")` and declares `native` methods, so it needs
`libtdjsonjava.so` and its `Java_org_drinkless_tdlib_JsonClient_*` symbols.
The sibling Flutter project builds the plain **JSON** interface instead —
`libtdjson.so`, no JNI symbols — for `dart:ffi`. The two are not
interchangeable, and copying one into this repository produces an
`UnsatisfiedLinkError`, not a working app. The workflow checks for the symbols
before publishing.

## Credentials — never commit them

`app/build.gradle.kts` reads `TELEGRAM_API_ID` and `TELEGRAM_API_HASH` from
`local.properties`, which is git-ignored and has never been committed. Copy
`local.properties.example` to set up a machine.

**Do not put an api_hash anywhere git tracks**, and do not ask the user to
paste one into a file that does. It cannot be reissued at my.telegram.org, so
a committed hash is public permanently, on an account that cannot be detached
from it. `TELEGRAM_API_ID=0` selects the demo backend, which is the right
answer whenever live access is not actually required.

The same goes for signing keys: `*.jks` is git-ignored and should stay so.

## Working here

- Develop on the branch the session assigns; do not push to `main`, and do
  not open a pull request unless asked.
- The repository has a second developer as of September 2026, so a change
  that only makes sense to somebody who watched it being made needs a comment
  or a README line.
- Verify before reporting. `gradlew.bat :app:assembleDebug` is the check that
  matters; saying a change works without having built it is worse than saying
  it is untested.

## What to build next

`ROADMAP.md` maps what a complete client contains — measured against Nekogram,
which was read as an inventory, not as a source to borrow from — onto the
Material 3 components each part should use. Keep it current.

Two constraints from that reading are worth repeating here. Nekogram is
**GPL-2.0**, so none of its code can come into this repository. And it uses no
Material components and almost no XML at all — 694 hand-drawn `View`
subclasses, 22 layouts in the whole app — so there is nothing in it to
re-theme. It is a reference for behaviour, never for code.

Use stock Material 3 components rather than hand-rolling substitutes. Custom
drawing is justified only where Material has no equivalent and Telegram does
have the thing: the voice waveform, delivery ticks, the typing indicator, the
chat wallpaper.

## History worth knowing

The initial commit — the whole client including the TDLib integration — is
the user's own work from July 2026. Do not describe that integration as
something this project's sessions produced.
