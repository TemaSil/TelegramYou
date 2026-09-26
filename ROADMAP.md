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
`1.5.0-alpha29` (from alpha28 on 25 September 2026 — the same Compose core, so
nothing else had to move). targetSdk stays 35; that governs runtime
behaviour, not what compiles.

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
- [x] A bottom navigation bar on Home — `ShortNavigationBar`, not
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
- [x] Copying goes through `LocalClipboard` — `rememberTextCopier` in
      `ui/common`; `LocalClipboardManager` is gone

## Colour and type, 25 September 2026

- **The fallback palette is generated, not picked.** Below Android 12, and
  wherever dynamic colour is off, the scheme is the one Android would build
  from a teal wallpaper — tonal spot, 2021 spec, from `TealSeed` with
  material-color-utilities. It was hand-picked before: coral secondary,
  periwinkle tertiary, so the navigation bar's pill came out brown and the
  story ring a rainbow.
- **Unread counts and story rings in primary.** The folder tabs' counts
  took Material's badge default, the error colour, which on dark schemes
  reads as brown, and the unseen-story ring swept through tertiary, which
  put a brown or orange arc on every avatar. Counts are primary on the
  folder in view and tonal on the others, as the chat rows' are; the ring
  is primary alone.
- **The app's name is set expressively** — Google Sans Flex rounded (`ROND`
  100), weight 650, width 115, where it was Medium, square and normal width.

## The conversation, 25 September 2026

- **It rises with the keyboard.** The list's box shrank as the keyboard
  came up, but a list holds on to its top, so the newest messages slid under
  the composer. It is now scrolled on by as much as the keyboard grows, frame
  by frame, and the bubbles move up with the keys.
- **New messages pop in** — grown out of their sender's corner on the
  spatial spring, bounce included, instead of only fading.
- **The composer's corners** are a fixed 28dp rather than half its height,
  which at five lines were half-discs cutting into the buttons.
- **Holding the microphone did nothing.** The press was read after the
  button's own clickable had taken it; it is read first now.
- **Photos fill their bubble** to its edges and corners, with the caption
  and the time beneath, instead of sitting framed inside it.
- **Group avatars** are 36dp at the foot of each run; at 28 a person's
  shape read as a stray mark.
- **A chat opens at its latest message.** The list is laid out from the
  bottom (`reverseLayout`), newest message first. It used to be laid out from
  the top and scrolled down on a spring once the first page arrived, and the
  older page loading meanwhile shifted the index it aimed at — so a chat
  opened with its history sliding past and stopping above the latest line.
  Laid out from the bottom it also rises with the keyboard by itself, which
  the hand-written keyboard follower it replaces did frame by frame.
- **The composer is round on one line, and keeps that curve as it grows.**
  Its corner is half its measured height at rest — the fixed 28dp before it
  was round on paper only, the capsule being nearer 76dp tall than 56 — and
  stays that as lines are added, the capsule growing upward out of its round
  ends. A version that switched to 28dp from the second line, on a spring,
  read as a change nobody needed. The field inside follows concentrically,
  8dp less.
- **Nothing shows beneath the composer.** The list stops at the capsule's
  bottom edge; a bubble scrolled into the margin under it read as a strip.

## Motion between screens, 25 September 2026

- **A chat opens out of its row, and a story out of its circle** — Material's
  container transform, on `SharedTransitionLayout` and `sharedBounds`
  (`ui/motion/ContainerTransform.kt`). Back — including the predictive back
  gesture — closes it into where it came from.
  The chat's went round the houses — the theme's bouncy spring, a calmer
  one, a plain slide — and settled on the first version's pace (stiffness
  380) with the bounce taken out (`ChatContainerSpring`), on the owner's
  word. What made the first version shake was not its motion but two
  things under it, both gone: the screen was laid out again at every frame's
  size, re-wrapping every line, and it arrived empty with its messages
  landing mid-flight. Now it is laid out once, at full size, with its
  messages fetched on the tap first (`TelegramRepository.warmChat`, capped
  at 200 ms — TDLib answers from its local database well inside that), and
  scaled into the growing container from its top, so the row shows the
  chat's own header at the start. A story keeps the standard scheme's slow
  spatial spring.
- **Fade through between the bottom tabs**, which is what Material's motion
  guidance gives navigation-bar destinations: they are separate places, not
  neighbours, so nothing slides. Shared axis X stays where it belongs, on
  the folder tabs, whose pages already move sideways under the finger.

## The schema, 25 September 2026

Sending a photo failed with TDLib's "Input file is not specified", and the
folder tabs were all called "Folder". Both were the same mistake: the TDLib
this app is built with (`d1085f9`) moved fields the code still wrote and read
at their old places. Checked against that commit's `td_api.tl`, line by line:

- **Every sticker set said "This set is empty".** TDLib sends `int64`
  values as strings, and `optLong` reads a string through a Double, which
  keeps sixteen digits of an id's nineteen — so the set asked for was not
  the one tapped. `optInt64` reads them exactly and they go back as strings;
  the same fix reaches session ids (ending a device) and chat order.
- **Photos, files and voice notes could not be sent.** The file now sits in
  an `inputPhoto`, `inputDocument` or `inputVoiceNote` of its own.
- **Folder names** are `name.text.text`, a `chatFolderName` holding a
  `formattedText`.
- **Link previews never showed** (`link_preview`, formerly `web_page`), a
  **reply's quote** was read as a string where it is a `formattedText`, and
  **@usernames** were read from a list of objects where TDLib sends
  `usernames.active_usernames`, a list of strings.
- The proxy API is new in the same way — `addProxy` takes a `proxy` object
  and the list is `addedProxies` — and was written against the schema from
  the start.

When TDLib is next bumped, its `td_api.tl` is the thing to diff first.

Also on the chat list: the stories sit above the folder tabs now, next to
the name, and the tabs against the list they filter. The bar gained an
overflow menu — light or dark theme, proxy, Saved Messages.

## On a real account, 24 September 2026

The first time the live client was used on a real account, on
`fix/live-typing-stories-photos`:

- **Nobody was ever seen typing.** Telegram only tells a session who is
  typing while that session says it is online, and this client never did.
  TDLib's `online` option now follows the activity in and out of the
  foreground. The chat's header also took "typing" once from `openChat` and
  never again; it now follows the chat list.
- **No story could be opened.** The viewer's state began as "no story" and
  the route closed on it before anything had been looked up. A circle is now
  keyed by its chat rather than by its first unseen story, which changed
  under the viewer. It plays the real photos and videos from `getStory`, one
  after another with a segment each, and the smoke test opens one.
- **A photo that failed to send looked like one still sending, for ever.**
  `updateMessageSendFailed` was not handled. A refused message is now marked
  on its bubble, a pending one wears a clock, and the server's reason comes
  up in a snackbar. The Live workflow also builds for Telegram's test
  servers (`-PtelegramTestDc=true`) to sign in with a +99966 number and
  send a photo to Saved Messages. **Not yet working**: the servers take the
  number and send a code, then refuse the documented one (the data
  centre's digit five times), so that step reports as a warning until it
  signs in once. The cause of the reported photo failure is therefore still
  unknown; the next failure on a phone will at least say it.
- **A story lasted no time with animations switched off** — its timer was
  an animation, and the system's animation scale shortened it to nothing.
  It is a real-time clock now.
- **No avatar ever showed a picture.** Nothing asked TDLib to download chat
  and profile photos. They are now fetched at the lowest priority and drawn
  over the initials everywhere an avatar is.
- **Demo mode inside the live APK**: ten taps on the login screen's mark
  restart the app on the demo backend, signed in; signing out of it, or ten
  more taps, goes back. The real account is left as it was.

## The live backend, reviewed, 24 September 2026

Demo mode is all CI has ever run, so the TDLib backend had only ever been
compiled. Read against the API it talks to, it had drifted in ways no test
here could see. What was fixed, on `fix/live-backend`:

- **A refusal crashed the app.** Send, edit, delete, react, mute, pin and the
  rest let TDLib's exception escape `viewModelScope`. Each view model now
  has one `attempt()` and a snackbar; a refused send puts the draft back.
- **Every chat was drawn pinned** (order compared with 2^50; real orders are
  a date in the top bits), **every sent message drawn read** (`sending_state`,
  an object, read as a number), and **chats the account is not in** — search
  results, groups just left — listed at the bottom. Positions now live in
  `ChatPositions` in `:core`, with tests.
- **Stories never showed**: the array was read from a field that does not
  exist. They now come from `updateChatActiveStories`.
- **The pinned bar never showed** (`pinned_message_id` is gone from `chat`),
  and **muting reset a chat's sound and previews** (a partial settings
  object). Fixed with `getChatPinnedMessage` and the chat's own settings.
- **The open chat never heard of edits, deletions, reactions, reads or a
  send's real id**, and was refetched after every send, losing the history
  scrolled back through. `MessageUpdate` events and a tested reducer in
  `:core` replace both.
- **A notification line could repeat** once per screen rotation. Fixed, and
  the smoke test now turns the screen and reads the notification back.

And the second pass, on `fix/live-backend-rest`, closing what the first
left open:

- **Opening a chat marked nothing read**, in either backend — the badge
  stayed until "Mark as read" was chosen from the list. The conversation now
  marks itself read while it is in front, and clears its notification.
- **Turning the phone on chat info, the media grid, the archive or a
  new-group form threw the person back to the chat list**: the redirect
  after sign-in checked a list of routes that had fallen behind.
- **A new message pulled someone reading back through the history down to
  the bottom.** It now only follows along when the list was at its end, or
  the message is our own.
- Presence, member counts and dates, all tested in `:core`: a private chat
  says "online" or "last seen …", a group "1,284 members", a channel
  subscribers, and the list says "Yesterday" or "Mon" rather than a bare
  time for a message from March.
- A conversation opens with a full page even when TDLib answers the first
  request with one message; the chat list asks for its next page as it
  nears its end.
- TDLib's `openChat`/`closeChat` are counted across the three screens that
  open a chat.
- Copying moved to `LocalClipboard`; the dead screenshot previews are gone
  (see below).

Still unverified: none of this has run against a real account. That needs
the TDLib libraries unpacked and an `api_id` on a developer's machine — see
README.

## Where this was left, 19 September 2026

A day of finishing sections rather than starting them. Everything here is on
`main`, built green, and all but the last item was watched arriving on the
emulator.

- **Media is no longer the emptiest section.** The shared-media grid, the
  full-screen viewer with pinch, pan, double-tap and drag-to-dismiss, and a
  carousel of recent photos at the top of the attachment sheet. The carousel
  is `HorizontalMultiBrowseCarousel` and it needs `READ_MEDIA_IMAGES`, asked
  for when the sheet opens and answerable with Android 14's "Select photos";
  refused, the sheet is exactly the three rows it was.
- **Folders, as tabs over the one chat list.** `PrimaryScrollableTabRow` with
  a `Badge` per tab. Which chat is in which folder, what the badge counts and
  what happens when a folder is deleted elsewhere all live in `:core`.
- **A chat info screen** behind the conversation's header: members, the
  invite link where the server offers one, and leaving the group.
- **A rail where the window is big enough**, through
  `NavigationSuiteScaffold`.
- **The blur came out of the reply.** It was allowed under the narrow
  exception CLAUDE.md grants, and the owner withdrew that after seeing it on
  a phone. A mockup is coming; nothing blurs in the meantime.

Three things the emulator caught that no unit test could, worth repeating
because they are the argument for that workflow existing:

- Every folder tab showed the same badge, because the demo backend's folder
  ids sat below the properties that read them and Kotlin initialises in
  declaration order. All three folders were folder 0.
- "Leave group" was under forty members and three screens down.
- A heads-up notification lands over the app bar, and UiAutomator will
  happily tap a header underneath one — which opened the wrong chat and
  photographed it. Waiting the notification out at each call site fixed it
  three times and it came back three times; heads-up notifications are now
  off for the run, which is the fix. The reply test is unaffected: it opens
  the shade itself.

What is left, largest first: video in bubbles (a thumbnail and a player,
and the thumbnail is most of it), upload and download progress, permissions
and admins, creating a group, and per-chat notification settings.

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
2. ~~**Folders**~~ — done, 19 September: tabs over the same rows, with the
   selection and the filtering in the view model and `:core`.
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
2. ~~**Video in bubbles**~~ — done, 23 September: poster, duration and a
   play button in the bubble, Media3 behind a full-screen player with
   Material's own controls.

### Screenshot rendering: taken out, 24 September 2026

Compose Preview Screenshot Testing (`com.android.compose.screenshot`) was
applied for a week and never rendered anything: under AGP 9 the
`screenshotTest` source set was never even compiled, so its four previews
drifted out of step with the screens they called and nothing noticed. The
plugin, its two `enableScreenshotTest` switches and the previews are gone.

What it was for is done another way: the **UI** workflow draws the real app
on an emulator after every push to `main`, and the **`gallery`** branch
keeps those screens build by build. If a pixel-level check is wanted again,
Roborazzi is the candidate — it does not depend on AGP compiling a source
set of its own.

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
- [x] Photos in bubbles — `AsyncImage`, space reserved from the photo's own
      aspect before the bytes arrive; tapping one opens it full-screen as a
      `Dialog`, with pinch, pan, double-tap and drag-to-dismiss
- [x] Video in bubbles — the poster, the duration and a play button over
      both, built like the photo bubble so a video reads as a picture you
      can start. The poster is fetched on sight and the video itself only
      when somebody asks for it: scrolling past a chat should not pull down
      everything anyone ever sent
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
- [x] Jump to a search hit older than the loaded window, and to an old
      pinned message the same way — `getChatHistory` from that message with a
      negative offset puts it mid-page, and that page replaces the latest
      messages on screen. Older pages load above it as ever, newer ones below
      it until it meets the latest window and is folded back in; the jump
      button drops it and goes straight back. The hit is lit for a moment.
      Sending from there goes back to the latest first. The view model's
      side is unit-tested, and the UI test jumps to the demo group's oldest
      line, 120 messages before what opening it loads
- [x] Load older messages on scroll — `loadOlderMessages`, guarded against
      the request-per-frame a list sitting at the top would otherwise make
- [x] Polls and quizzes — `RadioButton` rows, one tap votes; `Checkbox` rows
      and a Vote `Button` where several answers are allowed; results as
      Material's `LinearProgressIndicator`, growing on the theme's spring.
      A quiz marks the right answer and a wrong one of ours, and shows its
      explanation. The vote is drawn at once and corrected by the server's
      counts (`updateMessageContent`); retracting where the poll allows it.
      Percentages use the largest-remainder rounding, in `:core` with tests.
      Creating a poll is not in yet
- [x] Bot inline buttons — `FilledTonalButton` rows under the bubble, as wide
      as the bubble or the buttons, whichever is more. Callback buttons ask
      the bot (`getCallbackQueryAnswer`) and its answer is a snackbar, or a
      dialog when the bot asks for one; links open in the browser, copy
      buttons copy. A bot rewriting its buttons in place
      (`updateMessageEdited`) is followed. Games, payments, inline queries
      and Mini Apps are drawn disabled until there is a platform for them
- [x] Bot keyboard under the composer — tonal keys above the field, a
      button in the field to raise and lower it, the bot's placeholder, gone
      after one press when the bot asks. Keys that share a phone number or
      a location are shown disabled. Kept per chat from
      `updateChatReplyMarkup`

## 2. Chat list

- [x] Rows — `ListItem`
- [x] Stories rail
- [x] Unread badge — `Badge`
- [x] Swipe actions — `SwipeToDismissBox`, right to pin and left to mute,
      with the row's own shape behind it. `confirmValueChange` does the
      work and then refuses the change, which is the pattern for a swipe
      that is an action rather than a deletion. Archive and mark-as-read
      are in the long-press menu — two directions, both spoken for.
      Delete is not offered yet. **Off where the account has folders**:
      there the sideways drag changes folder, and pin joins mute, archive
      and mark-as-read in the long-press menu, so nothing is lost but the
      shortcut. Telegram's own answer to the same collision is a setting
      that picks one; ours could grow the same if both are wanted
- [x] Folders — `PrimaryScrollableTabRow` over the one chat list, from the
      account's own folders, with Material's `Badge` carrying each tab's
      unread count. The strip is absent entirely for an account with no
      folders. The folders are also pages of a `HorizontalPager`, one list
      per folder, so a sideways swipe on the chats moves between them Membership is
      a set on the chat, because a chat can be in several at once — TDLib
      reports it as a position in `chatListFolder`, the same way the archive
      works, so each folder has to be loaded for its chats to arrive
- [x] Archive: an entry row above the chats, absent entirely when nothing
      is in there, and its own screen behind it — the same rows on the
      same panel. On TDLib the archive is a chat list rather than a flag,
      so this is `addChatToList`, with membership read from positions
- [x] Search — `SearchBar`, server-side across chats **and** message text,
      in two labelled sections
- [x] Search as a section of its own. Empty, it is a front page: the people
      written to most (`getTopChats`) as a row of faces, recent searches as
      `SuggestionChip`s (kept on the device, the prefixes typed on the way
      folded into the finished word), the chats opened from search before
      (Telegram's own `searchRecentlyFoundChats`, each removable, all
      clearable), and channels Telegram suggests (`getRecommendedChats`).
      With a query, `SecondaryScrollableTabRow` tabs — All, Chats, Messages,
      Posts, Channels, Groups, Bots. Chats are the account's own first, then
      **Global search** (`searchPublicChats`) for public ones it is not in,
      each once. **Posts** searches public channels anywhere on Telegram
      (`searchPublicPosts`) on a button or the keyboard's search key, never
      while typing: Telegram gives a few free post searches a day and asks
      Stars for more, and this client never pays — it says how many are left
      and, once they are gone, when the next one comes. The tab and merge
      rules are in `:core` with tests
- [x] Channels read as channels. A channel is a supergroup with `is_channel`
      inside its type; the flag was read off the chat, where it never is, so
      every channel was drawn and filtered as a group
- [x] Grouped into containers — `SegmentedListItem` with
      `ListItemDefaults.segmentedShapes(index, count)`; pinned chats are one
      run, everything else another
- [x] Header that scrolls away — the name, the folder tabs and the stories
      leave as the chats scroll down and come back as soon as they scroll
      up. The state is `TopAppBarState` under
      `TopAppBarDefaults.enterAlwaysScrollBehavior`, settled on the motion
      scheme's spatial spring, with one `SegmentTick` as it finishes going.
      The header measures itself to tell that state how far it can go,
      because its height is whatever the folders and the stories add up to
- [x] Bottom navigation — `ShortNavigationBar` with Chats, Search, Profile
      and Settings
- [x] Shaped avatars — each person gets one of Material's shapes from the
      same seed as their colour, so they are the same clover in the list, in
      a group header and on their own row. Switchable in Appearance, because
      a list of circles is what every other messenger looks like. Since 24
      September the same shape follows them into the conversation — header,
      message avatars, member list, pickers — through `personShape`
- [x] Typing, shown by shape — while someone types, their avatar morphs
      through Material's shapes and turns, the loading indicator's language,
      and settles back on their own shape (`typingShape`, from TDLib's
      `updateChatAction`). The login screen's mark uses the same motion
- [x] Compose — the pencil is a `ToggleFloatingActionButton` opening a
      `FloatingActionButtonMenu`: new message, new group, new channel, join
      with a link. New message opens a contact picker in a
      `ModalBottomSheet`. It used to open `chats.firstOrNull()`, which
      looked like composing and was not
- [x] Long-press `DropdownMenu` on a row — pin, mute, archive and mark as
      read, on both backends (`toggleChatIsPinned`, `viewMessages`)
- [x] Adaptive navigation — `NavigationSuiteScaffold`, which picks its shape
      from the window: the same short navigation bar in compact, a wide rail
      where the window is big enough for one. Note that a phone in landscape
      is *not* one of those — Material keeps the bar whenever the window is
      short, which is why the emulator test resizes the window to a tablet's
      rather than turning the phone

## Sign-in

- [x] Phone entry — one field with the SIM's calling code already in it,
      formatted as typed and flagged by country, with a searchable country
      sheet behind the flag; libphonenumber, in `PhoneEntry` in `:core`
- [x] The code sent on its last digit, SMS autofill, resend after the
      server's timer with a countdown, correcting the number
- [x] Two-step password with a show/hide toggle
- [x] Expressive medium button at the bottom, loading inside it
- [x] Log in with a QR code from another device — TDLib's
      `requestQrCodeAuthentication`, from a button under the phone field.
      Material has no QR component and Android no generator: ZXing decides
      which modules are dark, and they are drawn in Compose as rounded
      squares in the theme's colours — always dark on light, which is what
      scanners read
- [x] Login email — both of TDLib's steps. Where Telegram asks for an
      address before any code (`authorizationStateWaitEmailAddress`) there
      is a field for it; where the code went to the account's email
      (`…WaitEmailCode`) it is the code step under its own title, showing
      the masked address, resending, and "Reset email" for a mailbox that is
      gone — the label says the server's wait, a week without Premium. The
      wait and the address check are in `:core` with tests. The demo takes
      this road for a number ending in 99999, which is how the UI test walks
      it. Apple and Google sign-in, which the same steps offer, are not in:
      both need the vendor's SDK, and the emailed code reaches the same place

## 3. Settings and profile

Material 3 covers this area completely; nothing custom is warranted.

These were all marked undone until 17 September 2026, when counting them
against the code showed `SettingsScreen.kt` had already been carrying half of
them. Ticks are only worth something if somebody moves them.

- [x] Settings list — `Scaffold`, `ListItem`, `Switch`
- [x] Appearance: theme, dynamic colour, shaped avatars and text size —
      the first through `SingleChoiceSegmentedButtonRow`, the switches as
      `Switch` rows, and text size as a `Slider` with four named stops that
      multiplies the system's font scale rather than replacing it, so a
      phone already set larger stays larger
- [x] Devices — Settings → Privacy and data: every session this account
      has, this phone first and the rest by last use, each with its device's
      icon, its app and where it is. One ends with a tap and a confirmation,
      all the others from the row between the two groups. A client that is
      not Telegram's own says "(unofficial)", since a stranger's client is
      what this list is looked at for. `getActiveSessions`, `terminateSession`,
      `terminateAllOtherSessions`; naming and ordering in `:core` with tests
- [x] Privacy rules — phone number, finding by number, last seen, profile
      photo, bio, forwarded messages, calls, and adding to groups: each a
      list item saying who it is set to, changed in Material's radio-button
      dialog. Telegram's rules are an ordered list; the audience is read
      from its whole-audience rules and everything naming particular people
      is kept exactly as read and written back first, so exceptions made in
      another app survive a change made here — shown as "Everybody (−2)",
      not edited. `getUserPrivacySettingRules` / `setUserPrivacySettingRules`;
      the reading and writing in `:core` with tests
- [x] Updates without a store — Settings → About asks the repository's
      `latest` release for its version, and a newer one downloads with
      Expressive's wavy progress bar and opens Android's installer. The same
      tracked debug key signs every build, which is what lets it install
      over the one running. A quiet check at launch puts a badge on the
      Settings tab when there is something to get
- [x] Proxy — SOCKS5, HTTP and MTProto through TDLib's own list
      (`addProxy`, `enableProxy`, `pingProxy`), from the chat list's overflow
      menu and from the login screen's top corner — before sign-in is when
      a blocked network needs it. One is used at a time, so the list is a radio choice, and each
      row says how its proxy answered a ping. A pasted `tg://proxy` or
      `t.me/socks` link fills the form; the parsing and the form's rules are
      in `:core` with tests
- [x] Profile: name, bio and username, edited in place — the fields are the
      profile, with no pencil and no second screen behind one. What is valid
      is `:core`'s `ProfileEditing` with 22 tests, because a username Telegram
      refuses comes back as a generic error with no field attached and the
      screen would have nothing to point at. Only the changed fields are sent,
      the username last because it is the one that gets refused, and the
      account is re-read afterwards so what is shown is what the server took
- [x] Notifications per chat — on chat info: on or off, "Mute for…" (an
      hour, eight, two days, until turned back on), message preview and
      sound, as list items with switches. Read the way TDLib keeps them: a
      chat's own value where it has one, its scope's (private, group,
      channel) where it says "default" — which is most chats. The shade
      honours them: no preview says "New message", no sound posts silently.
      The rules and the "Off until 18:40" line are in `:core` with tests
- [ ] Language — Russian and English. Left for last, on purpose: the
      strings are only worth extracting once the screens have stopped moving
- [x] Data and storage — what TDLib keeps on the phone, folded from its
      per-chat, per-type statistics into kinds (photos, videos, voice, files,
      stickers…), largest first, with the database named but not offered.
      Checkbox rows choose what to clear — all but profile photos and
      stickers to start with, since those come straight back — and
      `optimizeStorage` pointed at those file types clears them. The folding
      and the sizes are in `:core` with tests. Not in: keeping media for a
      set time, which TDLib has no setting for and would need a scheduled
      clean

## 4. Media

- [x] Image loading — Coil `AsyncImage`, used by the chat's photo messages
- [x] Shared media grid — `LazyVerticalGrid` with `GridCells.Adaptive`, from
      `searchChatMessages` filtered to photos and video; reached from the
      chat's overflow menu, and tapping a tile opens the viewer — or the
      player, for a video, whose tile carries a play badge so the grid does
      not claim a still and then start moving
- [x] Full-screen viewer with zoom and drag-to-dismiss — pinch, pan,
      double-tap and a drag that fades the backdrop as it goes; the maths is
      in `:core` as `ZoomPan` with tests
- [x] Download and upload progress — `LinearProgressIndicator`, determinate
      once the size is known and indeterminate before that, with a line
      saying which direction the bytes are going. It reads `updateFile`,
      which the client had been ignoring: TDLib announces a file repeatedly
      as it moves rather than sending percentages. The bar is drawn over the
      poster in a bubble, in the player while a video is being fetched, and
      in the grid tile in the play button's place
- [~] Video playback — Media3's engine with Material's controls over a
      `TextureView`: a filled play and pause, a `Slider` for position and the
      time beside it. Media3's own player view is a View with its own look,
      so only the engine is taken. The arithmetic — progress, seek target and
      the label — is in `:core` with tests, because a duration the player has
      not worked out yet is -1 rather than 0 and dividing by it gives a bar
      that never reaches the end. Audio files are still not playable; voice
      notes have their own player
- [~] Stickers — shown in the conversation without a bubble, WEBP through
      Coil and animated TGS through Lottie; a sheet behind the smiley in the
      field with the recent ones and each installed set as a tab; sent as
      TDLib's `inputSticker`. Video (WEBM) stickers show their still
      thumbnail, and custom emoji are not in yet. A video sticker with its
      transparency needs a VP9 decoder that keeps the alpha channel, which
      Android's own do not — played through them it would be a sticker in a
      black square — so it waits for a native decoder
- [x] Videos out to the bubble's edges, as photos are, with the caption
      and time beneath — they were a smaller rounded frame inside the bubble
- [x] GIFs — TDLib's `messageAnimation`, which used to show as a word. Out
      to the bubble's edges, fetched on sight, and playing by themselves,
      looping and silent, on Media3 over a TextureView; a tap opens the
      player. A "GIF" label says it is a loop and not a video already
      running
- [x] Round video messages — `messageVideoNote`, also a word before. A
      220dp circle with no bubble, fetched on sight, played in place with
      sound on a tap and paused on another, back to the start when it ends.
      The shared inline player crops the frame to its shape rather than
      stretching it, and stops while the app is in the background

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
- [x] Join and leave — leaving from the chat info screen, with a
      confirmation and a pop back past the conversation; joining through an
      invite link from the pencil's sheet. The link is checked before
      anything is joined and its destination shown — name, member count, and
      Open instead of Join when this account is already in. Every spelling
      of a link (t.me/+, t.me/joinchat/, telegram.me, tg://join) is
      canonicalised in `:core` first
- [ ] Permissions and admins
- [~] Invite links — the primary link is shown and copied where the server
      offers one. It is read from `basicGroupFullInfo`/`supergroupFullInfo`
      and never created: a screen that minted a link because it wanted
      something to show would be handing out an invitation nobody asked for.
      Revoking and making new ones is not in
- [x] Create a group or channel — one screen, the name focused and the
      people under it with a checkbox each; the create button appears once
      there is something to create, and Done on the keyboard creates it too.
      A group may start with nobody else in it, which TDLib allows

## Not planned

Calls need `tgcalls`, a second native stack TDLib does not carry. Payments,
Premium and Telegram Business are out of scope.

## Nekogram's own additions

Cheap once the base holds; its `NekoConfig` carries about sixty switches.

All of these live behind one row, Settings → For geeks, and every one is
off until turned on — the client behaves as it always did for anyone who
never opens that screen. The settings are in `:core` with tests
(`GeekSettings`), kept by `GeekStore` and read by the screens through
`LocalGeekSettings`.

- [x] Configurable double-tap action — nothing, ❤️, reply or copy; the
      handler is only attached when one is chosen, since a double-tap
      handler makes every single tap wait
- [x] Message details — exact time to the second, message, chat and sender
      ids, in a dialog from the message's menu, copyable at once
- [x] Forward without quoting — TDLib's `send_copy`
- [x] Save to Downloads (MediaStore, Android 10 and later, no permission)
      and copy a photo (a copy in the cache, shared by FileProvider, so the
      clipboard never sees TDLib's own directory). Open in browser is not in
- [x] Time with seconds. Number rounding is not in
- [x] Hide stories, hide the All tab when there are folders
- [x] Prefer IPv6 — TDLib's `prefer_ipv6`. Showing RPC errors is not in:
      failures already reach a snackbar with the server's words

Blocked on a base we do not have: translation and auto-translate, voice
transcription, markdown parser options. (A tablet layout and QR login were on
this list; both are done — see Home and Login.)

## Infrastructure

- [x] TDLib wired through `JsonClient`, demo backend for offline work
- [x] Build TDLib workflow (`JSONJava`) publishing the native libraries
- [x] Unpack `tdlib-java-d1085f9` into `app/src/main/jniLibs/` — `fetchTdlib` in `app/build.gradle.kts` does it for every live build
- [x] CI that builds the APK on push
- [x] Tests over the pure logic (message grouping); backends still untested
