# TelegramYou

Android Telegram client in Kotlin and Jetpack Compose, styled after
**Material You Expressive**. Talks to Telegram through the official TDLib
JSON interface.

## What this client is for

**A Telegram that looks and behaves like Android, because nothing else does.**

The name says it. **You** is Material You — the dynamic colour Android takes
from the wallpaper, so the client looks like *that* phone rather than like
Telegram's idea of a phone. It is the whole thesis in one word, and it is why
`dynamicLightColorScheme` / `dynamicDarkColorScheme` are the default rather
than the hardcoded teal palette, which is only the fallback below Android 12.

The official client brings its own conventions to every platform it ships
on, and on Android that now includes imitating Liquid Glass — an effect from
someone else's design language, redrawn by hand. Telegram draws its entire
interface on `Canvas`: 694 `View` subclasses, zero Material components, 22
XML layouts in the whole app. Whatever it looks like, it is never the
platform.

This project is the opposite bet. Stock Material 3 Expressive, dynamic
colour from the user's wallpaper, the platform's own motion, components that
behave the way every other Android app behaves. Bare Android, in a Telegram
client.

Three consequences, and they decide arguments:

- **Reach for the stock component first.** If Material ships it, use it. A
  hand-built row that imitates `ListItem` is not neutral — it loses the
  spec's metrics, its state layers and its accessibility, and it has already
  happened here once.
- **Custom drawing needs a reason Material cannot meet.** The voice waveform,
  delivery ticks, the typing indicator, the chat wallpaper. That is close to
  the whole list. A wavy loading ring drawn on a Canvas was not on it —
  Material ships `LoadingIndicator` and `WavyProgressIndicator`.
- **Never imitate another platform's materials.** No glass, no blur standing
  in for depth, no iOS idioms. That is the thing this client exists not to
  do. (The sibling Flutter project, TelegramAss, is deliberately the other
  way round — iOS-styled with Liquid Glass. Do not let the two bleed.)

`ROADMAP.md` records what Expressive actually contains and which of it our
pinned alpha exposes. `ARCHITECTURE.md` records the layers.

## This is not the Flutter client

There is a sibling project, **TelegramAss**, which is a separate Flutter
client with an iOS-styled interface. The two share no code and are worked on
in separate sessions. Nothing here should be changed to match it, and work
meant for it does not belong in this repository — if a request is about
Liquid Glass, Cupertino widgets or Dart, it is about the other project.

## Layout

```
core/src/main/kotlin/                      plain Kotlin/JVM, no Android
  com/telegramyou/app/telegram/model/      data classes both backends share
  com/telegramyou/app/ui/chat/             grouping and drag maths, pure
app/src/main/java/
  org/drinkless/tdlib/JsonClient.java      official TDLib JSON JNI binding
  com/telegramyou/app/
    TelegramYouApp.kt                      picks the backend from BuildConfig
    telegram/tdlib/TdLibTelegramClient.kt  live backend
    telegram/demo/DemoTelegramClient.kt    offline backend
    ui/                                    Compose screens
app/src/main/jniLibs/<abi>/libtdjsonjava.so
```

`:core` exists so that something can be compiled without the Android SDK —
see `ARCHITECTURE.md`. New code that does not need Android goes there, with
a test.

minSdk 26, targetSdk 35, compileSdk 37, applicationId `com.telegramyou.app`.
compileSdk is ahead of targetSdk on purpose: Compose 1.12 is built against 37
and will not link below it, while raising targetSdk would opt the app into
newer platform behaviour changes, which is a separate decision.

Android Gradle plugin 9, Gradle 9, Kotlin 2.4. The stack is this new because
**Material 3 Expressive is not public in any stable `material3`** —
`MaterialExpressiveTheme`, `MotionScheme` and `LoadingIndicator` are all
`internal` in 1.4.0, the newest stable. Reaching them means
`material3:1.5.0-alpha*`, that alpha declares Compose core 1.12, and 1.12
requires compileSdk 37 and AGP 9. The whole stack moves together or not at
all; `material3` is therefore pinned past the BOM, deliberately, on an alpha.

Three consequences worth remembering, each of which cost a red build:

- **No `org.jetbrains.kotlin.android`.** AGP 9 carries Kotlin itself and
  refuses to load alongside the standalone plugin. `kotlinOptions` is gone
  with it; the Kotlin `jvmTarget` follows `compileOptions`. The Compose
  compiler plugin is still applied separately.
- **The APK name comes from `base.archivesName`.** AGP 9 deleted the old
  variant API the previous naming block reached into.
- **Do not name the compile platform in `setup-android`.** `sdkmanager`
  refuses `platforms;android-37` by name even though `--list` shows it in the
  stable channel. AGP installs what `compileSdk` needs by itself.

## Deliberately on the newest of everything — keep it that way

This project runs the **newest alpha `material3`** and the **newest Android
toolchain**, on purpose. That is not carelessness: Material 3 Expressive is
`internal` in every stable `material3`, so the alpha is the only way to have
the thing this client exists to show. Having accepted an alpha there, staying
current everywhere else costs little and keeps the app on what Android
actually looks like today.

**Check for newer versions at the start of a session, and when anything here
feels dated.** The Build workflow prints what Google's Maven and Maven
Central offer on every run — compose-bom, material3, AGP, Kotlin, and the
screenshot plugin — as check-run annotations titled `Available versions`.
Read those rather than guessing: this environment cannot reach
`dl.google.com` at all, so a version invented from memory is a red build.

When bumping, remember the chain runs one way and the whole stack moves
together:

```
material3 alpha  →  Compose core  →  compileSdk  →  AGP  →  Gradle
```

A newer `material3:1.5.0-alphaNN` declares a Compose core; that core sets the
minimum compileSdk; that compileSdk sets the minimum AGP; that AGP sets the
minimum Gradle. Reading the alpha's POM first (the workflow prints its
dependencies too) tells you how far the rest has to move before you try.

Two things to hold on to while doing it:

- **An alpha under every screen is a real risk, taken knowingly.** The way
  back is `compose-bom 2025.09.01` with stable `material3` 1.4.0, which built
  green — but it has no Expressive at all.
- **Green means it compiles.** An alpha can change behaviour without changing
  a signature, and nothing in CI renders a screen. After a bump, look at the
  screenshots and at the app before calling it fine.

## Building

```bat
gradlew.bat :app:assembleDebug
```

APK lands at `app\build\outputs\apk\debug\TelegramYou-0.1.0-debug.apk`.

The pure module needs neither the SDK nor Google's Maven, so its tests run
anywhere — including environments where `:app` cannot even be configured:

```
gradlew :core:check --configure-on-demand
```

Keep the flag. Without it Gradle configures `:app` too, and that resolves AGP
from `dl.google.com`.

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
`UnsatisfiedLinkError`, not a working app.

The workflow checks the built library exports `JNI_OnLoad` before publishing —
not `Java_org_drinkless_*`. TDLib binds its natives with `RegisterNatives`
from inside `JNI_OnLoad`, so a correct library exports no `Java_*` symbols at
all. Checking for those rejects a good build.

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

## Who checks what

Split deliberately, because the two halves need different things.

**Machines catch errors.** Anything that crashes, throws, fails to appear, or
computes the wrong answer is found in CI, and the person on the other end of
this project should not have to install an APK to discover it. That is what
the `UI` workflow is for: it boots an emulator, drives the demo client through
real screens, and fails when one of them does not arrive. A bug that reaches a
phone is a hole in that test, and the fix is to widen the test as well as the
code.

**A person judges how it looks and feels.** Spacing, colour, whether an
animation reads as a ripple or a stutter, whether a gesture lands where the
thumb expects — none of that is assertable and none of it should be faked with
a pixel comparison. The APK on the front page is for that, and the answer
comes back as an opinion, not a failure.

So: **do not ask for the app to be installed in order to find a crash.**
Reproduce it on the emulator, read the trace, fix it, and let the install be
about the design.

### Seeing the screens without a device

The `UI` workflow pushes what it saw to the **`ui-screenshots`** branch, which
is rewritten on every run and can simply be fetched:

```
git fetch origin ui-screenshots && git show FETCH_HEAD:evidence/screenshots/03-chats.png
```

It holds three things: `evidence/screenshots/` — one PNG per step of the smoke
test, `evidence/results/` — the instrumentation report, which names the test,
the assertion and the stack trace, and `evidence/crash.txt` — the app's own
fatal exceptions when there were any.

A branch rather than a workflow artifact, and that is not a preference: an
artifact needs a GitHub session to fetch and the environment this project is
written in cannot reach the storage it redirects to. A branch clones.

Two things learned the hard way while building it, both worth keeping:

- **Type into the field, not into its placeholder.** Compose publishes a
  `TextField` as an `EditText` and its placeholder as a separate `TextView`.
  Selecting by the placeholder's words finds the `TextView`, and setting text
  on that silently does nothing.
- **Screenshots must go through `TestStorage`.** Written to the app's own
  files they are deleted when Gradle uninstalls the APK at the end of the run,
  which is before anything can collect them.

## What CI proves, and what it does not

Every push builds the app and runs the unit tests. A green build means the
tests **executed** — the final step fails the build when none ran, because
Gradle reports success on an empty run and a passing step would otherwise mean
nothing. The count comes back as a check-run annotation, which is the only
route to it here: the log tail is buried by post-job steps and artifact
downloads redirect to storage some environments cannot reach.

What the Build workflow cannot say is whether a screen appears — it never
constructs one. The `UI` workflow does, on an emulator, and that is where a
crash or a screen that never arrives is caught.

What neither can say is how any of it **looks**. A green emulator run means
the chat list exists, not that it is the right shape. Do not describe a visual
change as working on the strength of a green build, or of a screenshot that
merely proves something was drawn.

## History worth knowing

The initial commit — the whole client including the TDLib integration — is
the user's own work from July 2026. Do not describe that integration as
something this project's sessions produced.
