# Architecture

Where the code is going, and why. The current shape is written down as
honestly as the target, because the gap between them is the work.

Kotlin, Jetpack Compose, Material 3 Expressive, TDLib through
`org.drinkless.tdlib.JsonClient`. None of that is in question here; this
document is about the layers between the TDLib socket and a screen.

Why those choices are not negotiable is in `CLAUDE.md`: this client exists to
be **bare Android** — stock Material, dynamic colour, the platform's own
motion — against an official client that imitates another platform's design
language. The architecture serves that. A structure that makes reaching for
the stock component harder than hand-rolling one is the wrong structure.

## Nekogram as the map, never as the source

[Nekogram](https://github.com/Nekogram/Nekogram) is read as an inventory of
what a complete client contains — which screens exist, what each one does,
how they connect. It is **GPL-2.0**, so not one line of it can come here.
Behaviour travels, code does not. It also draws everything by hand on
`Canvas` with 694 `View` subclasses and 22 XML layouts in the whole app, so
there is nothing in it to copy even if the licence allowed it.

Its inventory, counted from a clone of `master`: **129 screens** — 61
miscellaneous, 18 settings, 15 groups and channels, 10 profile and contacts,
7 media, 7 login and security, 6 payments and bots, 3 conversation, 2 calls.

That number is the reason this document exists. The current structure works
for four screens and will not survive forty.

## What is wrong today

| | Today | Consequence |
|---|---|---|
| State | Screens take `TelegramRepository` and keep state in `remember { mutableStateOf }` | Rotation loses it; `ChatScreen` is 759 lines of UI, state and I/O together; nothing testable but pure functions |
| Screen arguments | `var activeStory` lives in the `NavHost` | An argument smuggled through a composable variable, lost on recreation |
| Client | One `TelegramClient` interface: auth, chats, messages, stories | Grows a method per feature, and both backends must implement every one |
| Routes | `"chat/{chatId}"` plus `navArgument` | Typos the compiler cannot catch, at 129 screens |
| Message loading | `openChat` returns a fixed 50-message window | "Load older messages on scroll" has nowhere to go |

`lifecycle-viewmodel-compose` is already a dependency. It has never been used.

## Target

```
:core   (plain Kotlin/JVM — no Android, no Compose)
  telegram/model/                     data classes shared by both backends
  ui/chat/MessageGrouping.kt          date headers, run grouping
  ui/chat/SwipeToReply.kt             drag maths for swipe-to-reply

:app    (Android)
  MainActivity, TelegramYouApp        entry points, dependency wiring
  navigation/
    Route.kt                          every destination, one sealed hierarchy
    TelegramYouNavHost.kt             graph only: no state, no arguments held
  telegram/
    TelegramClient.kt                 one socket, split by domain below
    auth/ chats/ messages/ stories/   focused interfaces per domain
    tdlib/  demo/                     the two backends
  ui/
    <feature>/
      <Feature>Screen.kt              Composable: renders state, emits events
      <Feature>ViewModel.kt           state holder: owns UiState, does the work
    components/                       shared, stateless
    theme/
```

Package names do not change across the split: `MessageGrouping.kt` is still
`com.telegramyou.app.ui.chat`. The module boundary is about what a file
*needs*, not about where it belongs conceptually.

Three rules, and the whole restructure follows from them.

**A screen renders a state and emits events.** It receives one `UiState` and
a set of callbacks. It does not hold a repository, does not launch
coroutines, does not decide what the server is asked. That is what makes a
759-line screen possible today and what stops it happening again.

**A ViewModel owns the state.** One `UiState` data class per screen, exposed
as a `StateFlow`, survives rotation, and is the only place that talks to the
repository. Its inputs are plain method calls.

**Navigation carries arguments, not state.** Every destination is a type in
one sealed hierarchy, and everything a screen needs to be rebuilt from
scratch arrives as an argument.

## Why :core is a separate module

Not for reuse, and not for build times. For a **feedback loop**.

Nothing in this project could be compiled outside CI. The environment the
code is written in has no Android SDK and cannot reach `dl.google.com`, so
every mistake — a missing import, a renamed parameter, a broken assertion —
cost a push and a CI round. That is minutes per typo, and it is why several
commits in this repository exist only to fix the one before.

`:core` is the part that needs none of that: plain Kotlin, JUnit, Maven
Central. It compiles and its tests run in seconds, locally:

```
gradlew :core:check --configure-on-demand
```

`--configure-on-demand` is not optional there. Without it Gradle configures
every project in the build, `:app` included, and configuring `:app` means
resolving AGP from Google's Maven — which is exactly what is unreachable.
For the same reason the root `build.gradle.kts` declares no plugins at all,
not even `apply false`: naming a plugin resolves its marker.

`:core:check` also runs `checkNoInternalApi`, which fails on any `internal`
declaration in the module. `internal` means "this module", so an internal
function here compiles clean, passes every test in `:core`, and then breaks
`:app` in CI — which is precisely the round trip this module exists to
remove. It caught nothing on the way in only because CI found it first: the
four helpers in `MessageGrouping.kt` were `internal` and had to be made
public.

One thing the move costs, and it is worth knowing before writing code against
these types: **Kotlin does not smart-cast a public property of another
module.** `if (!message.senderName.isNullOrBlank()) Text(message.senderName)`
compiled when `ChatMessage` lived in `:app` and does not now, because the
compiler cannot assume a property it did not compile stays what it was. Bind
it to a local first — `val sender = message.senderName?.takeIf { ... }` — or
use `let`. The guard above cannot catch this one; only `:app` can.

So the rule for new code is: **if it does not need Android, put it in
`:core` and write a test.** Anything that touches Compose, `ViewModel` or
TDLib stays in `:app` and waits for CI, as before.

## Screen inventory

Derived from Nekogram's map, ordered by what a messenger is unusable
without. Ticked when the screen exists and works, not when a file exists.

### Core — the client is not a messenger without these
- [x] Login (phone, code, password)
- [x] Chat list
- [x] Conversation
- [ ] Chat search / global search
- [ ] Settings root
- [ ] Profile (own, and another user's)
- [ ] Contacts and contact picker
- [ ] New message → contact picker
- [ ] Media viewer (photo, video)
- [ ] Notifications settings

### Groups and channels
- [ ] Group / channel profile
- [ ] Member list, admins, permissions
- [ ] Invite links
- [ ] Create group, create channel

### Settings, 18 screens in Nekogram
- [ ] Appearance (theme, dynamic colour, text size)
- [ ] Privacy and security, active sessions
- [ ] Data and storage, cache
- [ ] Language
- [ ] Devices, folders, stickers

### Media and misc
- [x] Story viewer
- [ ] Shared media grid
- [ ] File and gallery pickers
- [ ] Wallpaper

Calls need `tgcalls`, a second native stack TDLib does not carry. Payments,
Premium and Business are out of scope. Both stay out of the inventory.

## Migration order

Each step builds green on its own; none of them is a rewrite.

1. ~~**ViewModel layer, screen by screen.**~~ Done: four screens, four state
   holders, none of them holding a repository.
2. ~~**Typed routes.**~~ Done: one sealed `Route`, and `activeStory` is no
   longer a variable in the graph.
3. ~~**Split `TelegramClient`**~~ Done: auth, chats, messages and stories,
   with `TelegramClient` inheriting all four so the backends were untouched.
4. ~~**Paging for messages.**~~ Done: `loadOlderMessages` on
   `TelegramMessages`, with the exhaustion signal being an empty page.
5. Then the inventory above, in order. The migration is finished.

Not done, and the gap that matters most: **nothing renders a screen.** CI
proves this compiles and that the message-grouping logic holds. How any of it
looks or moves is unverified until somebody installs the APK. Screenshot
tests would close it and are not set up.

What does not change: the demo backend stays a first-class citizen. Every
screen must work offline with `DemoTelegramClient`, because that is what
makes interface work possible without credentials — and it is the only
backend CI ever exercises.
