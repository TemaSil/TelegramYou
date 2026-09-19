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
- [ ] `ButtonGroup` somewhere it belongs. It was tried in the chat composer
      and taken out again: grouped beside the field it read as a split button
      next to a text box — three things in a row rather than one control, and
      the person who asked for it said so. The composer is a floating capsule
      now, with plain icon buttons inside it. A segmented choice is what this
      component is for; Settings is the obvious candidate
- [~] Settings still uses `SingleChoiceSegmentedButtonRow` for the theme
      choice; `ButtonGroup` is the Expressive alternative, and now the most
      likely home for it
- [ ] A bottom navigation bar on Home — `ShortNavigationBar`, not
      `ButtonGroup`: it is navigation, and Expressive has a component for
      exactly that

Careful with the ticks: CI proves these compile, not that they look right.
Nothing in the pipeline renders a screen.

### `ButtonGroup`, written down because it cost six red builds

Nothing documents an alpha, and guessing at this one burned an afternoon.
The signature below was read out of `material3-android:1.5.0-alpha28` with
`javap`, which is the only authority there is. Two things about it are not
what you would assume:

```kotlin
ButtonGroup(
    overflowIndicator: @Composable (ButtonGroupMenuState) -> Unit,  // no default
    modifier: Modifier = Modifier,
    expandedRatio: Float = ButtonGroupDefaults.expandedRatio,
    horizontalArrangement: Arrangement.Horizontal = ButtonGroupDefaults.horizontalArrangement,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: ButtonGroupScope.() -> Unit                            // NOT @Composable
)
```

**`content` is a builder, not a row.** Calling `IconButton` straight inside
it fails with *"@Composable invocations can only happen from the context of a
@Composable function"*, which reads like a mistake somewhere else entirely.
Items go in through the scope:

```kotlin
interface ButtonGroupScope {
    fun Modifier.weight(weight: Float): Modifier
    fun Modifier.animateWidth(interactionSource: InteractionSource): Modifier
    fun Modifier.align(alignment: Alignment.Vertical): Modifier
    fun clickableItem(onClick, label: String, icon: @Composable () -> Unit, weight, enabled)
    fun toggleableItem(checked, label, onCheckedChange, icon, weight, enabled)
    fun customItem(
        buttonGroupContent: @Composable () -> Unit,
        menuContent: @Composable (ButtonGroupMenuState) -> Unit
    )
}
```

**`clickableItem` draws a labelled Button.** An icon-only button, or one
driven by a gesture rather than a click, needs `customItem` — and
`customItem` demands a menu entry as well, for the overflow menu shown when
an item does not fit. `Modifier.animateWidth(interactionSource)` is what
makes a pressed button widen and its neighbour squeeze; without it the group
is just a row.

`ButtonGroupDefaults` supplies the connected shapes —
`connectedLeadingButtonShape`, `connectedTrailingButtonShape` and the
middle and pressed variants — plus `OverflowIndicator`, which is the sane
thing to pass as `overflowIndicator`.

If a later alpha changes any of this: the way to find out is a workflow step
that curls
`.../androidx/compose/material3/material3-android/<v>/material3-android-<v>.aar`
— **material3-android**, its own coordinate, not `material3/` — unzips
`classes.jar` and runs `javap -public` over the classes. This environment
cannot reach `dl.google.com`, so that has to happen in CI.

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

Note `SegmentedListItem` too, which the blog does not name but 1.5.0-alpha28
ships, probed out of the AAR:

```
SegmentedListItem(onClick, shapes, modifier, enabled, overlineContent,
                  supportingContent, leadingContent, trailingContent,
                  verticalAlignment, onLongClick, onLongClickLabel, colors,
                  elevation, contentPadding, interactionSource, headlineContent)

ListItemDefaults.segmentedShapes(index, count, shapes = shapes())
ListItemDefaults.segmentedColors(containerColor = …, …)
```

That is the grouped list — tactic 4 below — as a component rather than as
something to build. `segmentedShapes` rounds the ends of a run and squares
its middle from nothing but an index and a count, and `SegmentedListItem`
takes `onLongClick` directly, so a row needing a context menu does not have
to be wrapped in `combinedClickable`. The chat list went the long way round
first, with an enum and four hand-cut corner radii, before this was probed;
`ListItemShapes` in the shape table above was the clue that was missed.
`ListItem` itself has the same new shape/elevation/contentPadding parameters
in this alpha, so anywhere still passing `tonalElevation` is on the old
overload.

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
4. **Group content in containers.** `SegmentedListItem` with
   `ListItemDefaults.segmentedShapes(index, count)` — the chat list uses it,
   pinned chats in one run and the rest in another. Message runs and day
   separators already do this; folders and the archive row are the same job.
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
- Grouped lists → `SegmentedListItem`, never hand-cut corner radii
- [ ] `LocalClipboardManager` is deprecated on this Compose — `ChatScreen`
      copy should move to `LocalClipboard`, which is suspend

## Where this was left, 18 September 2026

Second pass of the day, after the APK was looked at on a phone. Everything
below the first list landed after that, and most of it was reported rather
than found here — which is the split working as intended: the emulator says
whether a screen arrives, a person says whether it is right.

- **The profile edits.** Name, bio and username. `TelegramProfile` is a fifth
  interface on `TelegramClient`, with `setName`, `setBio`, `setUsername` and
  `refreshMe`, because TDLib has them separately and they fail separately.
- **The composer floats.** It was a row of a Column, so the strip under the
  messages was bare chat background rather than something hovering. The list
  and the composer share a Box now.
- **Three tone bugs, all the same shape.** The capsule was five units from the
  background it sat on; the chats had no panel distinct from the stories rail;
  the reply banner was a filled strip welded to the composer. Measured off
  screenshots rather than argued about.
- **Two keyboards that had to be asked for twice**, on the sign-in steps and
  on reply.
- **Messages animate in**, through `Modifier.animateItem` with the theme's own
  springs.
- **Send appears when something is attached** — it was chosen from the text
  alone, so a photo with no caption had a microphone where send belonged.
- **Versions and one signing key.** Every published APK was 0.1.0 with
  versionCode 1, signed by a key the runner generated fresh each time, so it
  could not be installed over the last one. The version is the run number now
  and the debug key is tracked; CLAUDE.md records why that exception exists.



Everything the 17 September list asked for is done except one, and that one
is a debt rather than a feature — see below. `main` carries the floating
composer, the avatar cluster, the grouped chat list, the bottom navigation
bar and the rewritten sign-in screen.

Landed today:

- **The chat list is grouped**, pinned in one run and the rest in another,
  on a light container with the bar and the stories rail on a darker tone
  behind it. Through `SegmentedListItem` and
  `ListItemDefaults.segmentedShapes(index, count)`, which material3 ships —
  the first attempt computed the corner radii by hand from an enum, and that
  enum is deleted. See the note under "The components" above.
- **Bottom navigation** — `ShortNavigationBar` with Chats, Search, Profile
  and Settings. Profile is a read-only stub; filling it is the next job.
- **The composer is visible.** It had a 28.dp corner and a 12.dp inset that
  nobody could see, because `surfaceContainer` landed five units from the
  conversation's own gradient. Capsule and field now sit at opposite ends of
  the container ladder, and the buttons lift 4dp so their centres meet the
  field's — the difference between a 56dp text field and a 48dp icon button.
- **The sign-in screen is rewritten.** It painted its mark with the
  pre-Android-12 fallback constants, so the first screen of a client named
  after Material You ignored the wallpaper. That, an emoji standing in for a
  Material icon, a button nested inside a button, and guessed window insets
  are all gone.

### What the next session should pick up

Everything the 18 September list asked for is done. What is left, largest
first:

1. **Media.** The grid, the viewer and the attachment carousel are in as of
   19 September; what is left is download and upload progress, audio and
   video playback, and stickers. Video is the largest of those, and the
   thumbnail is most of video.
2. **Folders** — `PrimaryScrollableTabRow`, from the account's own folders.
   The chat list is grouped and filtered already; folders are another
   filter over the same rows.
3. **Groups and channels** — join and leave, permissions, invite links,
   creating one. The member list is in, which was the piece the others
   depend on.
4. **Adaptive navigation** — `NavigationSuiteScaffold`, so a tablet gets a
   rail rather than a bar.
5. **The link preview's image**, deliberately left out for now: TDLib sends
   a photo as sizes whose files are not downloaded, and the card shows its
   words at once rather than waiting for bytes.

### One thing learned today that cost two rounds

**`javap` gives an alpha's parameter types and their order, never their
names.** The chat list was written against `SegmentedListItem` from a probe
of the AAR, and every guessed name was right except the last: the trailing
slot is `content`, not `headlineContent`. Seven compile errors came out of
that one word. When writing against a probed signature, expect the compiler
to be the thing that names the parameters — it does, precisely, in the
error.

## Where this was left, 17 September 2026

**Two branches are green and unmerged.** Both build, both pass, neither has
been looked at on a phone. Merging them is the first thing tomorrow, after
somebody has judged how they look — which is the half of this CI cannot do.

- `feat/floating-composer` — the composer is one floating capsule held clear
  of the edges, with plus, camera, the field and the microphone inside it.
  Seen on the emulator in its earlier form; the three-button version has not
  been.
- `feat/avatar-cluster` — a group's header shows its members overlapping,
  each in a different shape. Compiles; never rendered.

Landed on `main` today: the notification stack (a foreground service that is
actually started, two channels, `MessagingStyle`, tap-opens-the-chat, mute
and the open chat respected), `incomingMessages` in both backends — which
also gives a conversation that updates live rather than only on reopen — and
the emulator test now watches a notification arrive in the shade.

### What the next session should pick up

1. **Look at the two branches and merge them.** Both are waiting on an
   opinion, not on work.
2. **Reply from the notification** — `RemoteInput`. The one unticked line in
   the notifications section.
3. **A real member list.** The avatar cluster is built from whoever has
   written in the loaded window, because `TelegramClient` cannot ask who is
   in a group. It shows who is talking rather than who is present. The TDLib
   call belongs under groups and channels below.
4. **The bottom navigation bar on Home** — `ShortNavigationBar`, deferred
   twice now.

### Two things learned today that cost rounds

**`MaterialShapes` is unusable.** All thirty-five shapes are `internal` in
material3 1.5.0-alpha28, and alpha28 is the newest material3 that exists —
checked against Google's Maven, not assumed. `javap` shows their lazy
accessors (`access$get_flower$cp`), which reads exactly like a public API and
is not; the compiler refused twelve of them at once. The shapes are built
from `androidx.graphics:graphics-shapes` instead, which is the library that
catalogue is itself made of, at a stable 1.0.1. When androidx opens the
catalogue, swapping to it is mechanical.

**AGP 9 exits 0 when an instrumentation test fails.** It writes
`failures="1"` into the report and `1` into `test-result-exit-code.txt`, and
returns success. The UI workflow trusted that exit code and reported green on
a red test twice. It reads the report now. See CLAUDE.md.

## Where this was left, 16 September 2026

CI is green on the working branch, **79 unit tests** (57 of them in `:core`),
and the conversation screen is close to complete. Landed today: reactions,
multi-select with copy, forward and delete, in-chat search, an attachment
sheet with camera, the unread divider, jump-to-latest, the pinned message bar,
a drawn typing indicator, hold-to-record voice messages, and a settings screen
with the dynamic-colour switch this client is named after.

Two controls that pretended to be features are gone: the microphone records
now, and the avatar opens settings.

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

**One thing worth recording as a mistake rather than a fix.** Reading
`sendLocalFile` on its own, it looked as though attachments could never be
sent live — `inputFileLocal` takes a filesystem path and a picker returns a
`content://` Uri. `sendAttachment`, one screen up, already resolved the Uri
through `copyUriToCache`. Copying again in `ChatScreen` was redundant and
then actively broke it, because the second copy handed the backend a path it
tried to parse as a Uri. Reverted. The lesson is cheap and worth keeping:
read the caller before concluding the callee is broken.

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

1. ~~**Pinch to zoom in the photo viewer**~~ — done, 19 September, along
   with pan, double-tap and drag-to-dismiss.
2. **Video in bubbles** — still a caption and an emoji. Needs a thumbnail and
   a player, and the thumbnail is most of it.

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
      camera and file, above them a `HorizontalMultiBrowseCarousel` of the
      most recent pictures. Everything travels as a `content://` Uri; the
      TDLib backend resolves it into the upload cache, which is where that
      belongs. The carousel needs `READ_MEDIA_IMAGES`, asked for when the
      sheet opens and answerable with Android 14's "Select photos"; refused,
      the sheet is exactly the three rows it was
- [~] Photos in bubbles — `AsyncImage`, space reserved from the photo's own
      aspect before the bytes arrive; tapping one opens it full-screen as a
      `Dialog`, with pinch, pan, double-tap and drag-to-dismiss. Video is
      still missing
- [x] Voice messages: hold to record, release to send, tap to play, with the
      waveform drawn behind it — amplitudes measured while recording, and
      Telegram's own packed 5-bit waveform decoded for everyone else's. The
      played part is solid, the rest faded, and tapping a bar seeks there
- [x] Unread divider and jump-to-latest `SmallFloatingActionButton`; where the
      divider goes is decided in `:core` with tests
- [x] Pinned message bar — `Surface` under the `TopAppBar`, one line, tapping
      it scrolls to the message when it is in the loaded window
- [x] Typing indicator (custom draw)
- [x] Link previews — Telegram's own card under the text, a `Surface` with
      an accent bar rather than a `Card` inside a bubble. Nothing is
      fetched here: a client that read the page itself would tell every
      linked site who is looking. The image is still to come
- [ ] In-chat search with jump to the hit — global message search is in, this
      is the same query narrowed to one conversation
- [x] Load older messages on scroll — `loadOlderMessages`, guarded against
      the request-per-frame a list sitting at the top would otherwise make

## 2. Chat list

- [x] Rows — `ListItem`
- [x] Stories rail
- [x] Unread badge — `Badge`
- [x] Swipe actions — `SwipeToDismissBox`, right to pin and left to mute,
      with the row's own shape behind it. `confirmValueChange` does the
      work and then refuses the change, which is the pattern for a swipe
      that is an action rather than a deletion. Archive and mark-as-read
      are in the long-press menu — two directions, both spoken for.
      Delete is not offered yet
- [ ] Folders — `PrimaryScrollableTabRow`, from the account's own folders
- [x] Archive: an entry row above the chats, absent entirely when nothing
      is in there, and its own screen behind it — the same rows on the
      same panel. On TDLib the archive is a chat list rather than a flag,
      so this is `addChatToList`, with membership read from positions
- [x] Search — `SearchBar`, server-side across chats **and** message text,
      in two labelled sections
- [x] Grouped into containers — `SegmentedListItem` with
      `ListItemDefaults.segmentedShapes(index, count)`; pinned chats are one
      run, everything else another
- [x] Bottom navigation — `ShortNavigationBar` with Chats, Search, Profile
      and Settings
- [x] Compose — the pencil opens a contact picker in a `ModalBottomSheet`.
      It used to open `chats.firstOrNull()`, which looked like composing
      and was not
- [~] Mute and unmute from a long-press `DropdownMenu`; pin and mark-read
      still need client methods
- [ ] Adaptive navigation — `NavigationSuiteScaffold` for tablets

## 3. Settings and profile

Material 3 covers this area completely; nothing custom is warranted.

These were all marked undone until 17 September 2026, when counting them
against the code showed `SettingsScreen.kt` had already been carrying half of
them. Ticks are only worth something if somebody moves them.

- [x] Settings list — `Scaffold`, `ListItem`, `Switch`
- [~] Appearance: theme and dynamic colour are done, through
      `SingleChoiceSegmentedButtonRow`; text size is not
- [~] Sign out is there; the rest of privacy and active sessions is not
- [x] Profile: name, bio and username, edited in place — the fields are the
      profile, with no pencil and no second screen behind one. What is valid
      is `:core`'s `ProfileEditing` with 22 tests, because a username Telegram
      refuses comes back as a generic error with no field attached and the
      screen would have nothing to point at. Only the changed fields are sent,
      the username last because it is the one that gets refused, and the
      account is re-read afterwards so what is shown is what the server took
- [ ] Notifications settings — the two channels now exist, so this is
      per-chat overrides rather than a global switch
- [ ] Language — Russian and English
- [ ] Data and storage, cache size

## 4. Media

- [x] Image loading — Coil `AsyncImage`, used by the chat's photo messages
- [x] Shared media grid — `LazyVerticalGrid` with `GridCells.Adaptive`, from
      `searchChatMessages` filtered to photos and video; reached from the
      chat's overflow menu, and tapping a tile opens the viewer
- [x] Full-screen viewer with zoom and drag-to-dismiss — pinch, pan,
      double-tap and a drag that fades the backdrop as it goes; the maths is
      in `:core` as `ZoomPan` with tests
- [ ] Download and upload progress — `LinearProgressIndicator`
- [ ] Audio and video playback — `Slider` for position
- [ ] Stickers, animated stickers, custom emoji

## 5. Notifications

Without these it is not a messenger you can leave closed.

- [x] Foreground service holding the TDLib connection — it was declared in
      the manifest and written, but nothing started it and it listened to
      nothing
- [x] A notification per chat, tap opens the conversation — `MessagingStyle`,
      so a chat reads as a conversation rather than one interruption per line
- [x] Mute respected, open chat stays silent — decided by
      `decideNotification` in :core, with tests, because these rules fail
      quietly and no screenshot shows it
- [x] POST_NOTIFICATIONS asked for, from the chat list
- [x] Reply from the notification — `RemoteInput` into a receiver that sends
      with `goAsync`, and takes the notification down only once the message
      has gone

The emulator watches a notification arrive: the demo backend has a chat that
speaks every twenty-five seconds, and the smoke test leaves the app, opens
the shade and finds it there — in the loud channel, with the connection
notice under Silent, and without the message that was sent while the
conversation was open.

**The reply is watched working**, which closes the oldest debt in this
file. The test opens the shade, expands the notification, clicks Reply,
types into systemui's own `remote_input_text`, presses the send arrow — then
comes back into the app and finds the text in the conversation. That last
step is the only one that proves anything, and the first version of the test
did not have it: it asserted the notification went away, which a working
reply path cannot satisfy here, because the demo chat speaks again
twenty-five seconds later.

It caught a real mistake on its first run, too. The helper that types takes
the first `EditText` on screen and the shade has several, so the text went
elsewhere and the reply field kept its placeholder — plain in the
screenshot, caught by no assertion until two steps later. The field is read
back now, so it fails where it breaks.

The arrivals themselves were missing before this. `TelegramClient` had no way
to say a message had come in — a conversation was loaded once by `openChat`
and never heard from again — so `incomingMessages` was added to both backends
and the conversation screen now appends to the window it is showing. That was
a live-updating chat as much as it was groundwork for notifications.

## 6. Groups and channels

- [x] Member list — `basicGroupFullInfo` or `getSupergroupMembers`,
      whichever the chat type has; a channel has subscribers rather than
      members and is left alone. The header's cluster uses it instead of
      guessing from who has spoken
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
