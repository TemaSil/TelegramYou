# What to build, measured against Nekogram

[Nekogram](https://github.com/Nekogram/Nekogram) is the official Telegram for
Android plus roughly eighty patches. It was read here as a **map of what a
complete client contains** — not as something to borrow from. Measurements
below are from a clone of `master`, September 2026.

Tick a box only when the thing works in the app, not when the code exists.

## Why this exists

The point of the client is **bare Android in a Telegram**: stock Material 3
Expressive, dynamic colour, the platform's own motion — where the official
client brings its own conventions everywhere, including imitating Liquid
Glass on Android. `CLAUDE.md` states this and what follows from it; the short
version is that a stock component beats a hand-built imitation of one, and
another platform's materials are never the answer.

Everything below is measured against that.

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

- [x] **Material 3 Expressive, on the alpha.** `MaterialExpressiveTheme` with
      `MotionScheme.expressive()`, and Material's `LoadingIndicator`.

### What that cost, since none of it was optional

Expressive is not public in any stable `material3`. `MaterialExpressiveTheme`,
`MotionScheme`, `ExperimentalMaterial3ExpressiveApi` and `LoadingIndicator`
are all `internal` in 1.4.0, the newest stable:

```
e: Cannot access 'fun MaterialExpressiveTheme(...)': it is internal in file.
e: Unresolved reference 'LoadingIndicator'.
```

From there each step forced the next. `material3:1.5.0-alpha28` declares
Compose core 1.12.0 → 1.12 requires compileSdk 37 and AGP 9.1 → AGP 9
requires Gradle 9. So the stack is Gradle 9.7.1, AGP 9.4.0, Kotlin 2.4.20,
compose-bom 2026.09.00, compileSdk 37, and `material3` pinned past the BOM to
`1.5.0-alpha28`. targetSdk stays 35; that governs runtime behaviour, not what
compiles.

Three AGP 9 removals had to be worked around — the standalone Kotlin plugin,
`kotlinOptions`, and the variant API the APK-naming block used. CLAUDE.md
lists them, because each one is a red build for whoever meets it next.

**This is an alpha, under the theme every screen is built on.** That is the
trade. If it goes wrong the way back is `2025.09.01` and stable `material3`
1.4.0, which is where this sat one commit earlier and which built green.

Cleaned up on the way through, and worth keeping either way:

- [x] `ExpressiveMotion` / `LocalExpressiveMotion` deleted — hand-rolled
      spring tokens that no component ever read, so they styled nothing.
      `MotionScheme.expressive()` does what they pretended to.
- [x] The wavy ring `ExpressiveLoadingOverlay` drew on a Canvas is gone.
- [x] The demo chat's claim about the theme is true, and says it is on alpha.

Still to spend the move on:

- [x] `FloatingToolbar` for a message selection bar
- [~] Settings uses `SingleChoiceSegmentedButtonRow` for the theme choice;
      `ButtonGroup` is still unused and is the Expressive alternative

Careful with the ticks: CI proves these compile, not that they look right.
Nothing in the pipeline renders a screen.

## What Expressive contains, and what our alpha exposes

Two sources, and they agree. Material's own write-up (13 May 2025,
[Start building with Material 3 Expressive](https://m3.material.io/blog/building-with-m3-expressive)),
and the class list of `material3-android:1.5.0-alpha28`, read straight out of
the AAR because the blog cannot be reached from every environment this is
written in and describes a release rather than the artifact we resolve.

Expressive is an evolution of Material 3, not a Material 4. Its claim is that
expression is not decoration: Material reports 46 studies with 18 000+
participants, and that on expressive screens people found key interface
elements up to four times faster. That is the part worth taking seriously for
a messenger — **the argument is about attention, not about looking nice.**

### The four style changes

| Blog | What we can call |
|---|---|
| Motion physics — spatial springs for movement, effects springs for colour and alpha | `MotionScheme`, with `ExpressiveMotionSchemeImpl` / `StandardMotionSchemeImpl` |
| Emphasized typography | `Typography` |
| 35-shape library with shape-morph animation | `MaterialShapes`, plus per-component `ButtonShapes`, `IconButtonShapes`, `ChipShapes`, `ListItemShapes`, `MenuItemShapes`, `SplitButtonShapes`, `ToggleButtonShapes`, `DragHandleShapes` |
| More vivid colour schemes | `ColorScheme`, `DynamicTonalPalette` |

### The components

The blog names 14 new or updated. Present in the artifact and reachable:
`ButtonGroup`, `FloatingToolbar`, `FloatingActionButtonMenu`, `LoadingIndicator`,
`SplitButton`, `ToggleButton`, `ShortNavigationBar`, `WideNavigationRail`,
`AppBarRow` / `AppBarColumn`, `WavyProgressIndicator`, `Scrollbar`,
`DragHandle`, `MaterialShapes`, `MotionScheme`.

Everything Expressive sits behind `@ExperimentalMaterial3ExpressiveApi`.

Note `WavyProgressIndicator`: Material ships wavy progress as a real
component. The hand-drawn wavy ring deleted from `ExpressiveLoadingOverlay`
had a stock counterpart after all — two of them. `LoadingIndicator`, the
shape-morphing one, is in there now; if the overlay ever wants a wavy ring
specifically, use `WavyProgressIndicator` rather than a Canvas.

### The seven tactics, against this client

Material's guidance, and what it implies here. The last one is the one to be
careful with.

1. **Vary the shapes.** `MaterialShapes` for avatars and stories rings; a
   shape too small for an important action undersells it.
2. **Rich, nuanced colour.** Contrast between primary / secondary / tertiary
   and surfaces is how the eye finds the important thing. Own vs other
   bubbles is exactly this problem.
3. **Guide attention with type.** Emphasized styles for unread counts,
   pinned-message bars, section headers — not for body text.
4. **Group content in containers.** Message runs and day separators already
   do this; folders and the archive row are the same job.
5. **Natural motion.** Shape morph on press, and `MotionScheme` springs.
   Animation has to explain a change, not decorate one.
6. **Flexible components.** `ShortNavigationBar` and `WideNavigationRail`
   are the adaptive-navigation answer for tablets and foldables.
7. **Hero moments.** One or two per product. In a messenger the candidates
   are sending a message and opening a chat. Spend them there and nowhere
   else; seven tactics applied everywhere is noise, which is the failure
   mode this whole update invites.

### What this changes in the plan below

- Message selection bar → `FloatingToolbar`, not a custom bar
- Settings → `ButtonGroup` for segmented choices
- Attachment button → `FloatingActionButtonMenu` is the stock pattern
- Adaptive navigation → `ShortNavigationBar` / `WideNavigationRail`
- Avatars and story rings → `MaterialShapes`
- [ ] `LocalClipboardManager` is deprecated on this Compose — `ChatScreen`
      copy should move to `LocalClipboard`, which is suspend

## Where this was left, 16 September 2026

CI is green on the working branch, **70 unit tests** (48 of them in `:core`),
and the conversation
screen is close to complete: reactions, multi-select with copy, forward and
delete, in-chat search, an attachment sheet with camera, the unread divider,
jump-to-latest and the pinned message bar all landed today.

**The feedback loop changed more than any of the features.** There is now a
`:core` module — plain Kotlin, no Android — that compiles and tests in about
ten seconds in any environment, including ones with no SDK and no reach to
Google's Maven. Rules that can be wrong quietly live there: reaction
arithmetic, selection, search snippets, the unread divider, message grouping,
swipe thresholds. See ARCHITECTURE.md for what it costs (`internal` is
module-scoped, and Kotlin will not smart-cast another module's public
properties) and for the `checkNoInternalApi` guard that catches the first of
those locally.

CI reports differently too: one `Summary` step, last in the job, prints the
Kotlin errors and the test count together. Before that, a one-line compile
error arrived two hundred stack frames deep and cost a round trip to read.

**One real bug turned up on the way.** Attachments have never been sendable
against a live account: TDLib's `inputFileLocal` opens a filesystem path, and
a picker hands back a `content://` Uri. Demo mode ignores the value entirely,
which is exactly the kind of bug it is good at hiding. Pickers now copy into
the app's cache and send the copy's path.

**Still true: nothing in CI renders a screen.** Every visual claim above is
"it compiles and the logic is tested", not "it looks right". The app has not
been run since the reaction chips, the selection toolbar, the search field,
the attachment sheet, the unread line, the jump button and the pinned bar all
went in — and several of those share the same vertical space.

### The older history, still worth knowing

**The stack moved a long way on 15 September, and all of it was forced.** Material 3
Expressive is `internal` in every stable `material3` — 1.4.0 included — so
reaching it meant `material3:1.5.0-alpha28`, which declares Compose core
1.12.0, which requires compileSdk 37, which requires AGP 9, which requires
Gradle 9. The result: **Gradle 9.7.1, AGP 9.4.0, Kotlin 2.4.20, compose-bom
2026.09.00, compileSdk 37, material3 on an alpha**, pinned past the BOM.
The way back, if the alpha ever misbehaves, is compose-bom `2025.09.01` with
stable material3 1.4.0 — that combination built green.

Three AGP 9 removals cost a red build each and are written up in CLAUDE.md:
no standalone Kotlin plugin, no `kotlinOptions`, no old variant API. Plus one
that looks like a mistake and is not — `setup-android` must **not** name
`platforms;android-37`, because sdkmanager refuses it by name while listing
it as available. AGP installs it itself.

**The architecture was rebuilt** on the same day, per ARCHITECTURE.md: four
state holders, no
screen holding a repository, typed routes, and `TelegramClient` split into
four domain interfaces without touching either backend.

### What to do next

1. **Voice messages** — the composer's microphone is still `onClick = {}`, a
   control that pretends to be a feature. Recording, playback and the waveform
   (the one place custom drawing is justified) are all still missing.

### Screenshot rendering: groundwork laid, not working yet

Compose Preview Screenshot Testing is applied and configured, and the build
is green with it. What does not work is the rendering, and six runs narrowed
why to one fact:

```
--- compiled screenshotTest classes ---
                    (nothing)
```

**The previews are never compiled.** Not from `src/screenshotTest/java`, not
from `src/screenshotTest/kotlin` — both were tried. So this is not layoutlib
refusing a Material 3 Expressive alpha, and not a preview that cannot be
drawn; the source set simply is not being built. `updateDebugScreenshotTest`
then reports "test sources present ... did not discover any tests", which
sounds like a task misconfiguration and is really an empty classpath.

What is already in place and correct:

- Plugin `com.android.compose.screenshot:0.0.1-alpha16` — the newest; the
  Build workflow prints the list.
- `android.experimental.enableScreenshotTest` in **both** `gradle.properties`
  and the module's `experimentalProperties`. Both are required, and each
  failure names only the other one.
- Four previews in `app/src/screenshotTest/kotlin` — chat list in both
  themes, a conversation, the login screen — non-private, with dynamic colour
  off and fixed instants so they render identically on any machine.

Where to look next: whether AGP 9's built-in Kotlin compiles the
`screenshotTest` source set at all, and what `./gradlew :app:tasks` and
`:app:sourceSets` actually report for it. The plugin's alphas track AGP
closely and 9.4 is very new, so "not supported yet" is a live possibility —
in which case Roborazzi is the fallback.

The rendering steps have been taken back out of the workflow. A step that
always fails teaches nothing after the first time, and a red build on every
push costs more than the feature is currently worth.

### Known debts, none of them hidden

- **Swipe-to-reply is compiled and unproven.** Its arithmetic is tested — how
  far the bubble travels, where the threshold sits, that a leftward drag does
  nothing — but whether the gesture feels right under a thumb, and whether it
  fights the list's vertical scroll, can only be judged on a device. Nobody
  has held it.
- **Nothing renders a screen in CI.** The app was installed once today and
  the chat list, avatars and motion were confirmed by hand; everything since
  — the ViewModel rebuild, typed routes, the interface split — is unverified
  beyond compiling. Screenshot tests would close this.
- `MotionScheme` gives one motion scheme to the whole app, so animation
  currently feels uniform. Differentiating movement is the components' job,
  not the theme's.
- `onClick = {}` stubs remain on the search button and the composer's voice
  button. They look like features and are not.
- `LocalClipboardManager` is deprecated on this Compose; copying should move
  to `LocalClipboard`, which is suspend.
- **[`tdlib-java-d1085f9`](https://github.com/TemaSil/TelegramYou/releases/tag/tdlib-java-d1085f9)**
  — `tdlib-jnilibs-java.zip`, 34.6 MB, all four ABIs — has still not been
  unpacked into `app/src/main/jniLibs/`. Live mode needs it; demo mode does
  not, and demo mode is all CI ever exercises.

## Architecture

[`ARCHITECTURE.md`](ARCHITECTURE.md) holds the target: what each layer is
for, the screen inventory derived from Nekogram's 129, and the order the
restructure happens in. It exists because the current shape works for four
screens and will not survive forty.

- [x] ViewModel layer — every screen renders a state and emits events; no
      screen holds a repository or launches a coroutine
- [x] Typed routes, one sealed hierarchy instead of `"chat/{chatId}"`
- [x] Split `TelegramClient` into auth / chats / messages / stories
- [x] Paging for messages, replacing the fixed 50-message window

## The revision pass, 15 September 2026

Prompted by a fair question: with Expressive in hand, should the plan start
again? No — nine items are ticked out of a hundred-odd, and the new arsenal
mostly lands on work not yet done, where it costs nothing to pick the right
component up front. But auditing what exists against stock Material was
worth it, because **two ticks below were false**:

| Claimed | Actually was |
|---|---|
| `[x] Rows — ListItem` | a hand-built `Row` + `Column` with its own paddings, heights and text styles |
| `[ ] Unread badge — Badge` | already built, as a `Box` with a 50% corner radius |

Both are now what they said they were. Fixed with them:

- **`indication = null` in three places** — the avatar, the chat row and the
  story viewer had Material's press feedback switched off by hand, so a tap
  produced no state layer at all. The avatar keeps its scale animation; that
  is extra, not a replacement.
- **The attachment chip is an `InputChip`** — it was a Row painted to look
  like a chip, with a "Clear" text button where the dismiss icon goes.
- **`AnimatedVisibility(visible = true)`** around every chat row: a constant
  never transitions, so the enter animation could not run. Removed.
- **Our own components now read `MotionScheme`.** The theme publishes the
  expressive springs and stock components obey it, but `AvatarBubble` and
  `StoriesRail` still carried their own hardcoded numbers — the same
  `0.55f / StiffnessMediumLow` pair deleted from `ExpressiveMotion` as
  unread. Half the migration had gone unspent.

Still open, and honest about it: the composer's voice button is still an
`onClick = {}` stub. It looks like a feature and is not. The search button
was the other one, and it works now.

## 1. Conversation

The screen everything else depends on. 366 lines today: a `TopAppBar`, a
`LazyColumn` of bubbles, a `TextField` composer.

- [x] Message list, own vs other — `LazyColumn`, `Surface`
- [x] Composer with send — `TextField`, `IconButton`
- [x] Attachment draft chip — `InputChip`
- [x] Bubble shape: asymmetric `RoundedCornerShape`, tail on the last of a run
- [x] Date separators — `Surface` pill, `labelSmall`
- [x] Sender name and avatar in groups
- [x] Delivery state — `Icons.Rounded.Done` / `DoneAll`; Material ships both, so nothing is drawn by hand
- [x] Reply: swipe right on a bubble, banner over the composer, quoted block
      inside it
- [x] Edit and delete — long-press `DropdownMenu`, `AlertDialog` for the for-me / for-everyone choice
- [x] Reactions — `FilterChip` row inside the bubble, picker in a
      `ModalBottomSheet`; the toggle arithmetic lives in `:core` with tests
- [x] Select several messages — `HorizontalFloatingToolbar`, copy and delete
      in one go; what it offers is computed from what every message allows
- [x] Search inside a chat — the field takes the app bar's title, results as
      `ListItem` rows with the term in bold, tapping one scrolls to it
      (only when it is in the loaded window — a hit older than that is found
      and shown, but the list cannot jump to it yet)
- [x] Copy, forward and select — long-press to select, then the toolbar's own
      copy, forward and delete; forwarding picks a chat in a `ModalBottomSheet`
- [x] Attachment sheet — `ModalBottomSheet` with `ListItem` rows for gallery,
      camera and file. What a picker returns is copied into the app's cache
      first: TDLib opens a filesystem path, and a `content://` Uri is not one
- [ ] Photos and video in bubbles, full-screen viewer as a `Dialog`
- [ ] Voice messages: record on hold, play with a waveform (custom draw)
- [x] Unread divider and jump-to-latest `SmallFloatingActionButton`; where the
      divider goes is decided in `:core` with tests
- [x] Pinned message bar — `Surface` under the `TopAppBar`, one line, tapping
      it scrolls to the message when it is in the loaded window
- [ ] Typing indicator (custom draw)
- [ ] Link previews — `Card` under the text
- [ ] In-chat search with jump to the hit — global message search is in, this
      is the same query narrowed to one conversation
- [x] Load older messages on scroll — `loadOlderMessages`, guarded against
      the request-per-frame a list sitting at the top would otherwise make

## 2. Chat list

- [x] Rows — `ListItem`
- [x] Stories rail
- [x] Unread badge — `Badge`
- [ ] Swipe actions: mute, pin, archive, delete — `SwipeToDismissBox`
- [ ] Folders — `PrimaryScrollableTabRow`, from the account's own folders
- [ ] Archive: entry row and its own screen
- [x] Search — `SearchBar`, server-side across chats **and** message text,
      in two labelled sections
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
- [ ] Unpack `tdlib-java-d1085f9` into `app/src/main/jniLibs/` — the release exists, nobody has used it yet
- [x] CI that builds the APK on push
- [x] Tests over the pure logic (message grouping); backends still untested
