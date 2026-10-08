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

## Where it stands, 7 October 2026 — version 2.0

Read this part first, and then **The plan** under it. The feature map
further down is the inventory, box by box; how each release came to be,
with the reasons, is in [`HISTORY.md`](HISTORY.md); the release notes are
in `CHANGELOG.md`.

**What 1.9 already had.** Everything a person coming from the official app
looks for first: formatted text, forwards, albums, pins, drafts, replies,
edits, reactions with Premium emoji, polls and quizzes, scheduled messages,
location and contacts, bot buttons and keyboards; voice and round video
messages recorded, held and locked; an emoji, GIF and sticker panel in the
keyboard's place; translation of a message. Folders as pages, the archive,
forum topics, admins and permissions, invite links and join requests,
creating groups and channels, profiles with blocking, contacts. Photos and
albums that open out of their bubble, video that plays while it downloads,
with speed and picture-in-picture, shared media, a download manager. Music
with a player in the shade, a queue, offline downloads and a library under
For geeks. Stories watched and posted. App lock, sessions, privacy,
storage, proxies, QR and email sign-in. Appearance with accents, pure
black, wallpapers and Less motion — Material 3 Expressive throughout.

**What 2.0 added** (7 October; the notes are in `CHANGELOG.md`):

- **A new composer**, as Google Messages has it — the field and a round
  button on the chat's own background, the capsule kept under For geeks;
  **voice messages as the official client records them** — the button
  swells and breathes with the voice, slide up to lock, aside to cancel;
  **several photos at once**, large inside the field, each with its cross,
  dragged into order, the caption under them.
- **Comments under channel posts**, opened in the conversation screen.
- **Inline bots** — "@bot query" in the composer, answers over the field —
  and **Mini Apps** inside the client, in its own colours.
- **Quotes**: part of a message quoted in a reply, and replies whose
  original was outside the window fetched rather than drawn as "Reply /
  Message".
- **Go to a date**, from a day separator or the date that floats while
  scrolling; dates say the year when it is not this one.
- **When it was read** — the read time in a private chat, who has seen it
  in a small group.
- **Copy link** and **Report** in a message's menu.
- **Notifications by kind of chat**, and the fix for a channel opened
  flooding the shade with its old posts.
- **A round video watched before it is sent**; a tap on a video hides its
  controls; a long caption opens and closes.
- **Groups**: a new sender starts a new run, so names and avatars are
  where they belong.
- **Music**: Reply from the player; the library's albums and covers from
  the files' tags, and Most shared.
- **A chat translated as it comes**, under For geeks.
- Dialogs whose rows were another colour from the dialog, and a calmer
  bottom to the chat background.

## The plan

What is left after 2.0, in the order it seems worth doing. The owner's
word on 7 October: close the base first — what the official client has —
and leave the forks' extras for later.

### 1. What a person coming from the official app still misses

By how soon they would notice, not by how hard it is.

- [ ] **Photos edited before they go** — crop and rotate at least, and the
      options the official app has in the same place: send as a file, as a
      spoiler, without sound, when the person is online.
- [ ] **Voice to text** — TDLib's `recognizeSpeech`, Premium on Telegram's
      side; the button on a voice message, the text under it.
- [ ] **Stickers looked after** — a pack opened from a sticker, added and
      removed, the packs reordered, trending ones.
- [ ] **Emoji status, profile colour, birthday** on profiles.
- [ ] **Saved Messages by chat** and its tags; **shared folder links**.
- [ ] **More than one account** — the official app holds three. TDLib runs
      one instance per database directory, so it is a client per account
      and a switcher on the Profile tab; everything above the backend
      already takes "the account" as given. The largest item here, and the
      one people would miss most.
- [ ] **Secret chats** — end-to-end, one device; TDLib carries them, the
      screens are the work.
- [ ] **Channel statistics and boosts**, for admins.
- [ ] **Report a whole chat**, from its info — 2.0 reports a message.
- [ ] **"Typing…" sent** — the client shows others typing but has never
      told them it is; TDLib's `sendChatAction` as the field changes, and
      "recording a voice message" while one is held.
- [ ] **Russian** — with Android 13's per-app language, chosen in the
      system's settings like any other app's. Asked about for 2.0 and not
      yet answered.

Done in 2.0, from this list as it stood on 7 October: replies that lost
their quote and quoting part of a message, when it was read, a link to a
message and Report, jumping to a date.

### 2. Better than the official app — the platform's own

The official client draws Android by hand; this one is Android, and the
platform has things a hand-drawn client does not reach for. These are the
places to be ahead rather than level.

- [x] **Conversations as Android means them** (2.1) — each chat a
      long-lived sharing shortcut with its picture, so its notifications sit
      in the shade's Conversations section and it can be made a priority
      one; it **bubbles** over other apps (BubbleActivity, one chat, Back
      folds it); and it is a **Direct Share** target — the share sheet's
      text, pictures and files land in its composer, or in a picked chat's
      when the app itself is chosen.
- [x] **Notification channels per kind of chat** (2.1): private chats,
      groups, channels, each with "More in Android's settings" in
      Settings → Notifications.
- [x] **Widgets** (2.1), in Glance and Material You: recent chats, and the
      music now playing with its controls.
- [ ] **A widget for one person** — the chat picked when it is placed, its
      newest line, a tap into it. Needs Glance's configuration activity.
- [x] **Keyboard** (2.1): Enter sends from a hardware keyboard,
      Shift+Enter a new line, Ctrl+F searches the chat.
- [ ] **Two panes** on a tablet and an unfolded phone, the chat beside the
      list (the rail is done). Larger than it looks: the chat screen's
      wiring lives inside the navigation graph, with the container
      transform from its row, and has to come out into something a pane
      can hold first.
- [ ] **Stylus handwriting** into the composer — Compose's text fields take
      it on Android 14 by themselves; to be checked on a device with a pen.

Android Auto and a watch are out, on the owner's word of 8 October.

### 3. Later — from the forks

Left until the base is closed, on the owner's word. What each is and why
is in the AyuGram and Nekogram sections below.

- [ ] From AyuGram: **ghost mode** (no "typing", no read marks until one
      acts, no online, stories unseen) and **message filters**; edited
      marks as one likes, plain replies, a profile opened by id, streamer
      mode, ending a view-once photo at once; a Quick Settings tile for
      ghost mode once it exists.
- [ ] From Nekogram: markdown parser options.
- [ ] For the owner to decide first: AyuGram's **edit and deletion
      history** and **delayed sending** to stay offline; **the music
      library** out of For geeks (1 October: only if people ask for it).

### Not planned

Calls need `tgcalls`, a second native stack TDLib does not carry.
Payments, Premium and Business are out of scope. Of AyuGram's features,
those that break Telegram's terms are out for good — the reasons are in its
section.

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
- [x] Reactions — `FilterChip` row inside the bubble, in the accent (the
      account's own filled with it); picker in a `ModalBottomSheet`; the
      toggle arithmetic lives in `:core` with tests. Since 28 September a
      quick row over every message's menu, with an arrow to a grid of all
      the message may take (`getMessageAvailableReactions`), each drawn as
      Telegram's own animation of it (`getEmojiReaction`, centre animation,
      TGS through Lottie). Custom-emoji reactions (Premium's) are read,
      drawn as their stickers (`getCustomEmojiStickers`) and offered in the
      picker — dimmed and locked where the account has no Premium
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
      Percentages use the largest-remainder rounding, in `:core` with tests
- [x] Writing a poll — a Poll row in the attachment sheet of groups and
      channels opens Material's full-screen dialog: the question, answers
      that grow a new row as the last is typed into (up to ten), switches for
      anonymous voting, several answers and quiz mode, the right answer
      marked with a `RadioButton` and an optional explanation. Send stays
      disabled with the reason written under the fields; the rules are
      `PollDraft` in `:core` with tests
- [x] Scheduled messages — holding send offers "Schedule message", then
      Material's `DatePicker` and `TimePicker`, one after the other. The
      message goes to the chat's scheduled list (`messageSchedulingStateSendAtDate`)
      and never into the conversation until it is sent; a clock in the app
      bar opens that list while anything is waiting, each message with Send
      now and Delete. Text only: an attachment or an edit goes at once
- [x] Formatted text — TDLib's `entities` read into `TextEntity` in `:core`
      and drawn as span styles: bold, italic, underline, strikethrough,
      code, quotes. Links, e-mail and phone numbers are Compose's own
      `LinkAnnotation.Url`; an @mention opens that chat (`searchPublicChat`);
      a #hashtag searches the conversation for it; a spoiler is covered until
      tapped. Sending reads Telegram's markdown — **bold**, __italic__,
      ~~strikethrough~~, ||spoiler||, `code`, [text](url) — through TDLib's
      `parseMarkdown`. Captions keep their formatting in the data; photo and
      file captions still draw plain
- [x] "Forwarded from …" over a forward, from `forward_info`'s origin
- [x] Albums — photos sharing a `media_album_id` draw once, as one grid
      where the first of them is, with the album's caption under it
- [x] Pin and unpin from a message's menu; the pinned bar follows at once,
      and `updateMessageIsPinned` keeps it true when it changes elsewhere
- [x] Music files — `messageAudio` in its own bubble: play and pause,
      title, performer and length, and a bar while it plays, on the same
      player as voice messages. Picked files with a music extension are sent
      as audio rather than as documents
- [x] Location and contacts — since 1.5 a contact card draws with View
      (their profile, where they are on Telegram) and Add; a place or venue
      with its name, address or coordinates and Open in Maps, through a
      `geo:` link to whatever maps app the phone has — no map is drawn, so no
      provider is chosen. A contact is sent from the paperclip's sheet
      (`inputMessageContact`); one's own place is sent from the same sheet
      since 1.5.1 (`inputMessageLocation`)
- [x] Inline bots and Mini Apps (2.0) — "@bot query" at the start of the
      composer asks the bot as the typing pauses, its answers over the
      field as tiles or rows; a web-app button opens the page in a
      full-height sheet with the client's Material You colours as its
      theme and the bridge every Telegram client speaks
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

- [x] Editing folders — creating, renaming, reordering and choosing chats,
      since 1.4; see *Still missing* item 10 at the top

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
- [x] Settings laid out as Android's own Settings app: `SegmentedListItem`
      rows in rounded groups (`ListItemDefaults.segmentedShapes`), an icon
      in a tonal circle only on rows that open a screen, the account on top, then Appearance,
      Privacy and security, Data and network, For geeks, App update and Log
      out — the same building blocks on For geeks. Privacy, Devices and
      Storage still use plain rows
- [x] App update as its own screen, like Android's System update: the
      version and one button that does the next thing, then one short
      "What's new" card for the incoming update — `whats-new.md`, carried
      in the release description (see CLAUDE.md)
- [x] Updates without a store — Settings → App update asks the repository's
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
- [x] Profile, as the official app has it — a large photo with the name and
      status under it, Set photo / Edit / Settings as three tonal buttons,
      the phone, username and bio as a
      segmented list that copies on a tap, a QR code of the t.me link with
      Share, and Change username / Copy link in the overflow. A new photo
      goes through the system photo picker to `setProfilePhoto`. Editing is
      Material's full-screen dialog behind Edit. The account's own stories
      as a grid are still to come
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
- [x] Video playback — Media3's engine with Material's controls over a
      `TextureView`: a filled play and pause, a `Slider` for position and the
      time beside it. Media3's own player view is a View with its own look,
      so only the engine is taken. The arithmetic — progress, seek target and
      the label — is in `:core` with tests, because a duration the player has
      not worked out yet is -1 rather than 0 and dividing by it gives a bar
      that never reaches the end. Speed and picture-in-picture since 1.6;
      audio files play in the music player since 1.6.2
- [x] Stickers — shown in the conversation without a bubble, WEBP through
      Coil and animated TGS through Lottie; sent as TDLib's `inputSticker`.
      Since 28 September the smiley opens a panel in the keyboard's place
      with Emoji (Jetpack's `EmojiPickerView`), GIFs (saved, and searched
      through the @gif bot) and Stickers (recent and each set as a tab); a
      keyboard key takes the smiley's place to go back. Video (WEBM) stickers
      play with their transparency — see "Video stickers play" above; this
      line said they waited on a native decoder long after they stopped
      waiting. Custom emoji inside message text are drawn as their stickers, in the line, since 1.5 (`EntityType.CustomEmoji`, `InlineTextContent`)
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
      stretching it, and stops while the app is in the background.
      **Recorded since 1.7** — the camera button held, CameraX in a circle,
      locked by a slide up, the camera turned without stopping

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
- [x] Permissions and admins — since 1.6.1 the info screen lists members
      with their standing (owner, admin by title, "Can't write"), owner and
      admins first, and for an admin a menu on each: Make admin (the
      official client's default rights, not the right to make more admins),
      Dismiss as admin where Telegram says it is theirs to, Don't let them
      write (`chatMemberStatusRestricted`, supergroups only) and Remove from
      group, which asks first and bans for a minute so they can come back.
      Permissions is a screen of switches over `setChatPermissions`, media
      folded into one. What is offered comes from `memberActions` in `:core`.
      Making or editing an admin opens a bottom sheet with their title
      (`setChatMemberTag`, 16 characters, no emoji) and a switch per right
      (`AdminRight`; a basic group has only the title). A search over the
      members filters what is loaded and asks `searchChatMembers` for the
      rest
- [x] Invite links — since 1.6.1 a screen of every link this account made:
      the primary one and others with a name, a time limit (hour, day,
      week, never) and a number of people (1, 10, 100, any), each with who
      joined and what is left (`inviteLinkSummary`); copy, share, revoke,
      and revoked ones listed below. A link can ask first ("Admins approve
      new members", no head count then); the info screen shows how many are
      waiting, and Join requests adds or dismisses them
      (`getChatJoinRequests`, `processChatJoinRequest`)
- [x] Forum topics — since 1.6.1 a forum opens onto its topics (General,
      pinned, the rest, each in its colour with its newest line and unread
      count) rather than onto one conversation. A topic opens in the usual
      conversation screen: `setOpenTopic` points history, paging, every send
      and the draft at it (`getForumTopicHistory`, `topic_id`), and arrivals
      from other topics are left out. New topic starts one; an admin who may
      manage topics renames, closes, reopens or deletes one from its menu
      (General only renames). Each topic keeps its own draft
      (`saveTopicDraft`), shown in the list as the chat list shows one.
      Topic icons are not offered yet
- [x] Invite links — additional links made, edited and revoked since
      1.6.1, with join requests. The primary one: shown and copied where the server
      offers one. It is read from `basicGroupFullInfo`/`supergroupFullInfo`
      and never created: a screen that minted a link because it wanted
      something to show would be handing out an invitation nobody asked for.
      Since 1.5 it is shared through Android's share sheet and revoked for
      a new one (`replacePrimaryChatInviteLink`), or made where there was
      none — the server refuses anyone who may not
- [x] Create a group or channel — one screen, the name focused and the
      people under it with a checkbox each; the create button appears once
      there is something to create, and Done on the keyboard creates it too.
      A group may start with nobody else in it, which TDLib allows

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
- [x] Open search without the keyboard. By default search takes the caret
      and raises the keyboard the moment it opens
- [x] Prefer IPv6 — TDLib's `prefer_ipv6`. Showing RPC errors is not in:
      failures already reach a snackbar with the server's words

Since then: translation (1.8) and a chat translated as it comes (2.0)
are in. Not built yet: voice transcription and markdown parser options —
see The plan. (A tablet layout and QR login were on this list; both are
done — see Home and Login.)

## AyuGram, read as an inventory, 7 October 2026

The owner pointed at AyuGram (github.com/AyuGram) as a second fork worth
mining, after Nekogram. Read from its README and its documentation
(AyuGramDocs), not its code: the Android client is GPL-2.0 and the
desktop one GPL-3.0, so as with Nekogram nothing of it can come in here —
it is a reference for behaviour only.

It is not Nekogram's kind of fork. Its own README calls it "a client with
ToS-breaking features in mind", and most of what it is known for is that.
This client ships the owner's own api_id inside a public APK (see
CLAUDE.md); a feature that breaks Telegram's API terms puts that key at
risk for every user at once, and a key cannot be reissued. So the list
splits in three.

**Worth taking — client-side, the account's own behaviour, nobody else's:**

- [ ] **Ghost mode, the honest half** — each its own switch under For
      geeks: don't send "typing…" and the other chat actions (TDLib's
      `sendChatAction` simply not called); don't mark messages read
      (`viewMessages` not called) until something is done in the chat —
      AyuGram's "read on interact"; don't send online (TDLib's `online`
      option). All three are things the account chooses about itself, as
      Telegram's own last-seen setting is. "Don't read stories" belongs
      with them.
- [ ] **Message filters** — hide messages in a chat, or everywhere, that
      match a word or a pattern, chosen by selecting text in a message and
      "Hide messages like this". Kept on the phone, per chat or for all,
      each chat able to opt out of the global ones. Not "hide ads": see
      below.
- [ ] **Edited marks, shown how one likes** — the "edited" label as a word,
      an icon or nothing. Small, Appearance rather than For geeks.
- [ ] **Plain replies** — the quote in a reply and a link preview without
      their colour bar and emoji background ("Disable colorful replies").
      Appearance.
- [ ] **Open a profile by id** — `tg://user?id=…` opening the person, from
      what TDLib already knows. Without AyuGram's fallback to a third-party
      lookup bot.
- [ ] **Streamer mode** — the phone number, the account list and the
      notification previews hidden while it is on, for screen-sharing.
- [ ] **Expire a view-once photo or video at once** — a button that ends it
      rather than waiting out its timer.

**Decide first — legal, but against how Telegram means something to work:**

- [ ] **Edit and deletion history** — the earlier text of an edited
      message, and messages deleted by the other side, kept in a database
      on the phone and shown marked. Telegram's deletion is meant to reach
      every device; keeping what someone deleted is a choice about their
      messages, not the account's own. The owner's call, and if yes, only
      for edits first.
- [ ] **Delayed sending to stay offline** — messages held a few seconds so
      sending does not flash online. Harmless, but it only makes sense with
      the whole ghost mode, and it makes "sent" lie for those seconds.

**Not taken, and why:**

- Forwarding and saving from channels that forbid it ("AyuForward",
  `noforwards` bypassed) — circumvents the channel's own protection.
- "Peek online" — reads another person's last seen by adding them to and
  removing them from one's own exceptions, against the privacy setting
  they chose.
- Screenshots in secret chats, and secret-chat media kept past its timer.
- "Local Premium" — Premium's features unlocked on the client, which the
  server does not back and Telegram's terms forbid.
- Hiding sponsored messages ("hide ads"): third-party clients on their own
  keys are required to show them. If this client shows them at all, they
  stay.
- Emulator-detection removal, an FPS limiter, its own push workarounds —
  problems of a fork of the official Java client, which this is not.

## Infrastructure

- [x] TDLib wired through `JsonClient`, demo backend for offline work
- [x] Build TDLib workflow (`JSONJava`) publishing the native libraries
- [x] Unpack `tdlib-java-d1085f9` into `app/src/main/jniLibs/` — `fetchTdlib` in `app/build.gradle.kts` does it for every live build
- [x] CI that builds the APK on push
- [x] Tests over the pure logic (message grouping); backends still untested


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
nothing else had to move), and to `1.5.0-beta01` on 8 October 2026, for 2.1 —
the first beta of the line Expressive lives on. targetSdk stays 35; that governs runtime
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
- [x] `ButtonGroup` somewhere it belongs — the profile's Set photo / Edit /
      Settings, with every item at weight 1 (see "Second attempt" below for
      the crash that taught that). Before that: It was tried in the chat composer
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

**Second attempt, 26 September 2026: the profile, and a crash.** The
profile's Set photo / Edit / Settings went in as a `ButtonGroup` of three
`clickableItem`s, which compiled against the signature above, and the
Profile tab crashed the moment it opened:

```
IllegalArgumentException: maxWidth must be >= than minWidth,
    maxHeight must be >= than minHeight, minWidth and minHeight must be >= 0
  at androidx.compose.ui.unit.Constraints.copy(Constraints.kt:673)
  at androidx.compose.material3.ButtonGroupMeasurePolicy.measure(ButtonGroup.kt:712)
  ... PaddingNode ... FillNode ... ColumnMeasurePolicy ... ScrollNode
```

What it means: ButtonGroup's own measure policy built a `Constraints`
with a negative or inverted width while dividing the row among its items.
The group sat in `Modifier.fillMaxWidth().padding(horizontal = 16.dp)`
inside a `verticalScroll` Column, measured in the lookahead pass (the app
runs inside a `SharedTransitionLayout`), with three labelled items at
default weight on a 411dp phone. Likely suspects, in the order to try:
the overflow logic deciding items do not fit (the labelled buttons' minimum
width times three plus the gaps, against the padded width) and computing a
negative remainder; the lookahead pass handing it constraints the main pass
would not; `expandedRatio` pushing a pressed item past the row.

**Found, from the library's own source** (a probe branch printed
`ButtonGroup.kt` out of `material3-android-1.5.0-alpha29-sources.jar`):
an item without weight is given its `maxIntrinsicWidth`. Three labelled
buttons add up to more than a phone's row, so the group takes its overflow
path, and line 712 measures the overflow indicator with
`constraints.copy(maxWidth = remainingSpace + overflowWidth)` — keeping the
incoming `minWidth`, which `fillMaxWidth()` had set to the whole row. The
new maximum is below that minimum, and `Constraints` throws. A library bug
(the copy should clear `minWidth`), and it only bites a group that both
overflows and is forced wide. **The fix used: `Modifier.weight(1f)` on every
item** (through `customItem`, with `animateWidth` for the press motion) —
weighted items divide the row exactly, nothing overflows, and line 712 is
never reached. Keep the weights, or drop `fillMaxWidth`, wherever a
ButtonGroup goes next.

The notes below were written before the source was read:

To bring it back: try a newer `material3` alpha first — this is the
library's arithmetic, not ours — with the three items in the profile and a
UI test that opens the Profile tab (`theProfileEditsAndSharesItself`
already does, and is what caught it). If it still throws, give each item an
explicit `Modifier.weight(1f)` through the scope, drop the labels to icons
with `customItem`, or measure the group outside the lookahead with a fixed
height. Until then the profile uses three `FilledTonalButton`s, icon over
label, which is what the owner wants to replace.

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
