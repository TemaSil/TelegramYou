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

  **Blur has one narrow exception, and it is granted per place, by the
  owner.** The rule above is about blur used *as a material* — a translucent
  surface standing in for depth, everywhere, the way another design language
  does it. Blur used once, on purpose, to take the conversation out of focus
  while a reply is being composed is a different thing: it is not pretending
  to be a surface, and Android itself blurs the same way behind its shade and
  its recents. It stays a decision the owner makes case by case, never a
  default and never a habit. Granted once, for the reply banner, on
  18 September 2026 — and **withdrawn on 19 September**, the day after, when
  the owner saw it on a phone. That code is out.

  **Granted on 29 September 2026, for one place:** a photo, video or GIF that
  has not downloaded yet shows Telegram's minithumbnail — the forty-pixel
  JPEG that comes inside the message — softened, and sharpens into the real
  picture, as the official client does; the owner asked for it by name. It is
  a picture that has not arrived, not a surface pretending to be glass, and
  it is blurred on the pixels (`MiniThumbnail` in `:core`, drawn by
  `BlurredMini`), not with `Modifier.blur`. That is the whole list; anywhere
  else still needs asking, with a mockup in hand.
  Material 3 Expressive ships no blurred material of its own — the effect is
  the platform's, through `Modifier.blur` and `RenderEffect`, and it does
  nothing below Android 12.

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
Central offer on every run — compose-bom, material3, AGP, Kotlin, Media3,
the navigation suite, and since October 2026 every other androidx library the
app pins by version (activity, core, lifecycle, navigation, datastore,
graphics-shapes, the test libraries) — as check-run annotations, one per
library. The full line, with the newest pre-release too, is in the step's log.
Libraries from Maven Central (coroutines, Coil, Lottie and the rest) can be
read from here directly: `repo1.maven.org` is reachable, `dl.google.com` is
not.
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

APK lands at `app\build\outputs\apk\debug\TelegramYou-<version>-debug.apk`.
CI publishes it under the fixed name `TelegramYou-debug.apk`, so the link on
the front page does not break every push.

### Versions

**The version is the owner's to set, and it is short:** `1.1`, `1.1.5`,
`1.2` — no build number in it. It is `appVersionName` in
`app/build.gradle.kts`, changed by hand when the owner says so and never
bumped on anyone's initiative. 1.1 is the first, from 27 September 2026,
with the first release build; before it the name was `1.0.<build>`.

The build number still exists and still moves with every build: it is
`$GITHUB_RUN_NUMBER` on CI and the commit count off it, and it is the
`versionCode`, so a phone takes each newer build as an upgrade. The release
is titled `TelegramYou 1.1 · build 430`, and the in-app update check reads
the build out of that title (`isUpdate` in `:core`), so a push to main is
still offered as an update while the version stays put. The gallery files
screens under `1.1-430`. Keep `appVersionName` a plain one-line string: both
workflows read it out of the file with `sed`.

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

## Credentials — shipped in the APK, never in git

**The published APK is the live client** — the owner's decision of 24
September 2026. Nobody should have to build a Telegram client with their own
keys to use it, and no Telegram client asks that: the official one and every
fork carry their `api_id` and `api_hash` inside the app.

Where the keys live, and the one place they must not:

- **CI:** the repository secrets `TELEGRAM_API_ID` and `TELEGRAM_API_HASH`
  (Settings → Secrets and variables → Actions). The Build workflow passes
  them to Gradle as environment variables, and the APK it publishes is live.
  Without them — a fork, a pull request from one — it builds the demo.
- **A developer's machine:** `local.properties`, git-ignored, which wins over
  the environment. Copy `local.properties.example` to set one up.
- **Never in a tracked file.** The repository is public: a hash committed to
  it is found by anyone searching GitHub, where one inside the APK takes a
  decompiler. It cannot be reissued at my.telegram.org, so a leak is for
  good. Do not put it in code, in a workflow file, in a commit message or in
  the README, and do not ask anyone to paste it into a file that git tracks.

In the APK the hash is masked, not stored as text: the build XORs it with a
random mask (`maskedApiHash` in `app/build.gradle.kts`) and the app reverses
it at start-up (`unmaskApiHash` in `:core`). That stops `strings` or a dex
search finding it; it is not encryption — the app must be able to read it,
so anything that runs the app can too.

`-PdemoClient=true` builds the demo whatever is configured; the UI workflow
uses it, because its smoke test drives the demo's seeded chats and the code
`12345`. The **Live** workflow (run by hand) builds with the secrets and
checks that TDLib starts and asks for a phone number — it stops there, since
the next step texts a real phone.

The same goes for signing keys: `*.jks` and `*.keystore` are git-ignored and
should stay so — **with one deliberate exception**, `app/debug.keystore`, which
is tracked.

That exception has a reason and a boundary. AGP creates a debug keystore on
whatever machine is building when there is none, and a GitHub runner is a fresh
machine every run, so every APK this repository published was signed by a
different key. Android will not install one over another: it reports a package
conflict, and the only way through was to uninstall the app and lose everything
in it. A single tracked debug key fixes that for CI and for both developers at
once. Its password is `android` and its alias `androiddebugkey`, the values the
Android SDK has used for its own debug keystore forever; it signs debug
builds and nothing else. **A release key is not covered by this** and must
never be committed.

### Two builds

- **Debug** (`com.telegramyou.app.debug`, `TelegramYou-debug.apk`) — for
  testing: the demo inside it, the tracked debug key, no R8. Slower than the
  app will be; judge motion on the release.
- **Release** (`com.telegramyou.app`, `TelegramYou.apk`) — for people: live
  only, R8 and resource shrinking on, signed with the owner's key. **The demo
  is not in it**: every way in is behind `BuildConfig.DEMO_ALLOWED`, false for
  release, so R8 drops the backend, its chats and its media, and the Release
  workflow fails if a seeded demo line or the demo's media are still in the
  APK. Each build's update check looks in its own place for its own file
  (`BuildConfig.UPDATE_API`, `BuildConfig.UPDATE_ASSET`).

The release key lives in four repository secrets — `RELEASE_KEYSTORE_BASE64`
(the keystore file, base64), `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`,
`RELEASE_KEY_PASSWORD` — or on a machine in `local.properties` as
`release.storeFile` and friends. It is made once, by the owner, on the owner's
machine, and never passes through a session:

```
keytool -genkeypair -v -keystore telegramyou-release.jks -alias telegramyou \
  -keyalg RSA -keysize 4096 -validity 36500
base64 -w0 telegramyou-release.jks    # macOS: base64 -i telegramyou-release.jks
```

**Losing it means no installed release can ever be updated again** — keep
the file and its passwords somewhere safe, apart from this repository.

### When each is built

**Debug on every push, release only when the owner says so** — the owner's
rule of 27 September 2026, so a push builds one APK rather than two.

- The **Build** workflow builds the debug APK on every push and, from main,
  publishes it to the rolling `latest` prerelease. The debug build's update
  check follows that tag.
- The **Release** workflow (`release.yml`, `workflow_dispatch` only) builds
  the release and publishes it as a release of its own, `v<version>`, which
  becomes GitHub's latest release — what the README's download link and the
  release build's update check follow. **When the owner asks for a release,
  run it** (the GitHub tools' `actions_run_trigger`, workflow `release.yml`,
  ref `main`); nobody has to press anything. It refuses a commit without a
  green Build and UI run, a version already released, and missing secrets.
- A new release needs a new version: move `appVersionName` first, on the
  owner's word — see *Versions*.
- The price: R8 only runs at release time, so a shrinker rule that breaks
  the release shows up then. The workflow fails before publishing anything,
  and the fix is usually a keep rule in `app/proguard-rules.pro`.
- **A phone refusing a good release.** v1.1 was refused on the owner's
  Nothing phone (Android 16) — "package appears to be invalid" from Chrome,
  "You can't install the app on your device" from Files — while the same
  file installed on Android 15 and 16 emulators and checked out with
  `apksigner`. Builds one step away from it (another id with the release
  key; the real id with the debug key) installed at once, and a little
  later the release itself did. Nothing was wrong with the file: the phone
  had held on to a refusal of that id with that key. Should it happen
  again, check the file first, don't re-key — and never tell the owner the
  APK is broken on the strength of the phone's message alone.

## Working here

- **Говорить в чате по-русски.** Владелец проекта пишет по-русски и ждёт
  ответов по-русски — в каждом сообщении сессии, а не только в первом.
  Записано здесь, потому что после сжатия контекста язык переписки теряется
  первым, и ассистент молча переходит на английский. Код, комментарии,
  сообщения коммитов, README и ROADMAP остаются английскими: их читает и
  второй разработчик.
- **Name the branch after the work**, not after whatever the session was
  handed: `feat/transfer-progress`, `fix/avatar-online-dot`. A session often
  starts on a generated name like `claude/awesome-davinci-s5ha6b`; rename it
  before the first push. The owner reads the branch list to see what is in
  flight, and a name that says nothing makes that list useless — this has
  been asked for twice.
- Do not push to `main`, and do not open a pull request unless asked.
- **Release notes live in `CHANGELOG.md`, not the README** — the owner's
  call of 30 September 2026, once people started arriving and starring. The
  front page names only the latest version, in its "Latest" section, so it
  shows the project is alive without a wall of history. On a release, put
  the version's notes at the top of CHANGELOG.md and replace README's
  "Latest" paragraph.
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

The composer carries three buttons on its left — plus, camera, and the
microphone opposite them — which reverses an earlier rule here that it should
carry one. That rule's argument was that a composer growing an icon per
attachment type runs out of room before it runs out of types, and it is still
right about attachment types. These three are not types: they are the three
things people reach for, and the attachment sheet is still behind the plus
for everything else.

## Icons

**Material Symbols, Rounded** — Google's current icon set — generated into
`app/src/main/java/com/telegramyou/app/ui/icons/Symbols.kt` by
`.github/scripts/symbols.py`, which downloads each from
google/material-design-icons. There is no icon library dependency: the
frozen `material-icons-extended` these replaced had no unfilled rounded
glyphs at all, which is what Material 3's rules for icons need.

- **Unfilled by default** (`Symbols.X`), as Material 3 applies them.
- **Filled for state** (`Symbols.XFilled`): the selected tab in the bar, a
  pin marking a pinned chat — and for the few that read better solid, small
  or on a coloured ground: play, pause, send, the Saved Messages bookmark,
  a selection tick, an error mark.
- **One style throughout.** Rounded only — no Outlined or Sharp mixed in.
- **A new icon:** add its name to `ICONS` in the script (and to `RENAMED`
  when Symbols calls it something else — `Phone` is `call` there) and run
  it from the repository root; do not edit `Symbols.kt` by hand.

## What's new

Settings → App update shows **one** card: what the incoming update brings, or
— when none is waiting — what the installed one brought. The text lives in
`app/src/main/assets/whats-new.md` (`# Title`, then `- line`s); the app ships
it, and the Build and Release workflows copy it into their releases' descriptions
between `<!-- whats-new -->` markers, which is how an older app reads the
notes of the update it is about to download (`WhatsNew` in `:core`).

The owner's rules, both given after a first version got them wrong:

- **Only the update itself.** Rewrite the file with each merge to main a
  person would notice; do not append. What was done before is history for
  the people building this, and it lives in git — nobody installing an update
  reads it.
- **Short.** A title and at most four lines, one short sentence each —
  `WhatsNewTest` fails the build on more. Walls of text go unread. The voice
  stays upbeat and casual, English until the languages arrive.

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
test, `evidence/results/shard-N/` — the instrumentation report of each quarter,
which names the test, the assertion and the stack trace, and
`evidence/crash-shard-N.txt` — the app's own fatal exceptions when there were
any.

The smoke test runs as **four shards on four emulators at once**
(AndroidJUnitRunner's `numShards`/`shardIndex`), and a `report` job puts the
quarters back together before publishing and judging them. A test must
therefore not rely on another having run before it on the same device:
which quarter a test lands in is decided by the runner, not by the order in
the file. The demo
chat that speaks on a timer can be asked to speak at once from a test
(`TelegramYouApp.demoClient?.speakNow()`), which is what the notification
tests do instead of waiting for it.

Pushes that change only Markdown (`**.md`), `LICENSE` or `.gitignore` do not
run the UI workflow at all (`paths-ignore`): words cannot break a screen,
and each such push used to cost a full run.

A second branch, **`gallery`**, is the one that is never rewritten. After a
green run on `main` the same workflow files the screens `gallery.py` lists
into it under `builds/<version>/`, the version being the one the Build
workflow gave the same commit, and refreshes `latest/` — which is what the
Screenshots section of `README.md` shows. That section shows six at most —
chats, a conversation, a group, search, profile, settings — the owner's
call: the front page is a glance, and the gallery is where every screen
lives. A screen shown there has to be in `gallery.py`'s list as well. It is the record of how the app looked release
by release; `.github/scripts/gallery.py` chooses the screens, shrinks them and
writes its index. Renaming a screenshot in `SmokeTest` drops it from there
until the list in that script is updated to match.

A branch rather than a workflow artifact, and that is not a preference: an
artifact needs a GitHub session to fetch and the environment this project is
written in cannot reach the storage it redirects to. A branch clones.

Three things learned the hard way while building it, all worth keeping:

- **Gradle's exit code does not tell you whether the test passed.** AGP 9
  writes `failures="1"` into the instrumentation report, writes `1` into
  `test-result-exit-code.txt`, and then exits **0**. A workflow that trusts
  `|| echo failed > marker` therefore reports green on a run where the app
  never reached the chat list — which happened twice, the second time after
  the first was supposedly fixed. The report is the authority, the same way
  it already is for unit tests in the Build workflow, and a run with zero
  tests in it fails too.
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
