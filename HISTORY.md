# How it got here

The log of TelegramYou's sessions and releases, moved out of `ROADMAP.md`
on 7 October 2026 so that the roadmap reads as a plan. Kept whole, for
the reasons: most entries say why something is the way it is, and some
say what it cost to find out. Newest first, as it was written.

## The log, 26 September to 2 October 2026

What was asked for and how it went, release by release, kept for the
reasons. The summary is above; the notes are in `CHANGELOG.md`.

**1.9 — 3 October: playing as it downloads.** From the owner's list
after a day with 1.8:
- **Streaming**, video and music: `TelegramFileDataSource` gives Media3
  a Telegram file as `tgfile://<id>`, asks TDLib to download from the
  offset being read (`downloadFile` with an offset, not synchronous) and
  reads what `getFileDownloadedPrefixSize` says is there straight off the
  phone. What arrived stays; closing a video stops the rest. The demo
  writes its files into the cache a slice at a time so the UI test plays
  through the same path.
- **The skips**, from the player log: an MP3 resumed at minute fifty was
  seeked past its end and failed twice. MP3s now seek by an index of
  their frames, and that error retries from the top.
- **Long messages** in parts (`splitLongText` in `:core`), formatting
  carried across each cut, instead of "Message is too long".
- **The viewer** edge to edge, under both bars, with a tonal Close and a
  count pill in the dark scheme of the wallpaper's colours.

**1.8 — 2 October: translation, the Nekogram pack, media out of its
bubble.**
- **Translate** in a message's menu: `translateMessageText` into the
  phone's language, shown in the stock dialog. Telegram may refuse it to
  an account without Premium; the refusal is said, not hidden.
- **Five For geeks switches** after Nekogram's settings of the same
  intent (its `NekoConfig` read as a list, no code taken): Repeat and Save
  to Saved Messages in the menu (`forwardMessages`, a copy into the same
  chat or a forward into one's own), Delete from this phone (`deleteFile`),
  pull down for the archive, blocked people's messages left out of groups,
  recordings that wait in the composer, and no sound for private messages
  from people not in contacts.
- **Photos and videos open out of their bubble** at the owner's asking:
  bubbles report where their media is on the screen (`MediaOrigins`), and
  the gallery scales its page until the fitted picture covers the bubble
  the way the bubble's crop does, clipped to it, then springs to full
  screen — and back into whichever page is in view. The geometry is in
  `:core` (`OpenFromBubble`). The dialog's own window fade is off, which
  also ends a UI flake on 1.7's run where an emulator stall caught it half
  gone.

### Where it stood, 26 September 2026

A review against what a complete client contains — the inventory above,
with Nekogram read as a list of behaviour. Checked against the code rather
than against this file: each "missing" below was grepped for in the TDLib
client and is not there.

**Still missing, and basic** — what someone coming from the official app
notices within a day. Roughly in the order to do them:

1. ~~**Formatted text.**~~ Done the same day — see below. Messages arrived as plain strings: no bold or italic,
   and links, @mentions and #hashtags are not tappable. TDLib sends
   `entities` with every text; none are read. Composing with formatting
   (Material's text-selection toolbar) comes with it.
2. ~~**Forwarded messages say where they came from.**~~ Done with the formatting pack. `forward_info` is not
   read, so a forward looks like the sender's own words.
3. ~~**Albums.**~~ Done with the formatting pack. Photos sent together (`media_album_id`) draw as separate
   bubbles instead of one grid. Sending albums already works.
4. ~~**Pin and unpin**~~ — done with the formatting pack.
5. ~~**Drafts**~~ — done 27 September: saved a second after typing stops
   and on leaving, through `setChatDraftMessage`, so they follow the account
   to other devices; "Draft:" in the chat list, back in the field on return.
6. ~~**Someone else's profile**~~ — done 28 September: a private chat's
   info shows the number, username and bio, photos and videos, mute and
   **block**; a group member tapped opens their profile by user id, with
   Send message; Settings → Privacy → Blocked users lists everyone blocked
   and unblocks them.
7. ~~**Delete a chat, clear its history**~~ — done 28 September, from the
   list's long-press menu: clear or delete a private chat (for both sides
   where Telegram allows it), leave a group or channel. `chatRemovalOf` in
   `:core` decides which, and the dialogs confirm.
8. ~~**Contacts**~~ — done 28 September: a screen in the chat list's ⋮ menu,
   tapping one opens the chat, and Add contact takes a number and a name
   (`importContacts`); a number not on Telegram is said so, not an error.
9. ~~Your own profile, properly~~ — done the same day; see section 3.
10. ~~**Editing folders**~~ — done 28 September (1.4): Settings → Chat
    folders lists them in tab order, moves them up and down from each
    row's menu and deletes them; the editor takes a name, chats by hand
    (a sheet of the chat list with ticks), chat types and what to leave
    out (`FolderRules` in `:core`, with Telegram's membership rule in
    `contains`). Colour tags and shared folders are not offered yet.
11. ~~**Posting a story**~~ — done 29 September (1.6): "My story" opens
    New story — a recent photo from the strip or anything from Android's
    photo picker, a caption, who sees it (everyone, contacts, close friends,
    as segmented buttons), then `postStory` from the account's own chat,
    after `canPostStory` has said yes. No editor on the picture yet.
12. ~~**App lock**~~ — done 29 September (1.5): a PIN (PBKDF2 hash only)
    and the platform's BiometricPrompt, auto-lock after a chosen time on
    the monotonic clock, the app silent to TalkBack behind it and blank
    in Recents. Settings → Privacy and security → App lock.

**Content in the conversation — the rule, 27 September 2026.** One rule
for every kind of message, so a new kind knows where it goes:

- **The picture is the message** when nothing is written about it: a photo,
  a video, a GIF or an album without a caption, a quote or "Forwarded
  from" has no bubble colour round it. It is rounded on its own (20dp) and
  the time, "edited" and the ticks sit on it in a scrim chip — black at 45%,
  white type — the one style for anything drawn over media.
- **Words bring the bubble.** A caption, a reply or a forward puts the
  bubble back; the picture fills its top and sides, and the words and the
  time sit under it in the bubble's colour.
- **Standing alone, always:** stickers (still, Lottie and video) and round
  video messages — their outline is the message.
- **In a bubble, always:** text, files, voice, music, polls, link previews.
- **Selected**, a picture without a bubble is ringed in the tertiary
  colour; there is no fill to change.
- **Video stickers play** — WebM, VP9, with the alpha that Android's own
  players drop: `parseWebm` in `:core` reads the file's two streams and
  `VideoSticker` decodes both with the platform's VP9 decoder into frames
  at the size drawn.

**Appearance — next, on the owner's list of 28 September 2026.** Today
Appearance is four rows in Settings: colour from the wallpaper, theme,
shaped avatars, text size. It becomes a screen of its own, measured
against the official client's Chat settings and the forks, and kept to
Material's own parts:

1. **Its own screen** — Settings → Appearance, one row where the group is
   now. At the top a live preview: a few bubbles on the chosen wallpaper,
   redrawn as anything below changes, as the official client shows it.
2. **Colour.** Material You from the wallpaper stays the default on
   Android 12+. Off — or below 12 — a row of accent swatches (teal, blue,
   violet, pink, red, orange, amber, green) instead of teal alone, the whole
   scheme built from the chosen seed the way the fallback already is
   (tonal spot, material-color-utilities), at run time rather than by hand.
3. **Pure black** for the dark theme: surfaces to black, for OLED.
4. **Chat:** a wallpaper — none, or one of a few patterns and gradients
   drawn in the theme's own colours (the wallpaper is on CLAUDE.md's short
   list of things worth drawing by hand); message text size, apart from the
   interface's; bubble corners as a slider (the official "Message
   corners"); which tone outgoing bubbles take.
5. **Chat list:** two lines of preview or one. Avatar shape stays the
   switch it already was — Material's shapes or circles; a third choice of
   rounded squares was written in here without being asked for, and the
   owner struck it.
6. **App icon** in the accent colours, chosen here, through
   `activity-alias`; Android 13's themed monochrome icon stays as it is.
7. **Less motion** — container transforms become fades, the typing morph
   holds still, springs settle faster; on top of Android's own "Remove
   animations", which is honoured already.

All of it in `AppearanceSettings`, with the rules and defaults in `:core`
and tested; each lands visible in the preview first.

**Done 28 September (1.3):** 1–4 — the screen and its preview, the eight
accents (`schemeFromSeed`, with the hand-written teal replaced and held to
its old values by `PaletteTest`), pure black, and the chat's wallpaper,
message tone, corners and message text size (`LocalChatStyle`,
`StyledMessage`). Added on the owner's word the same day: **colours from
the avatar** — each conversation in a scheme seeded by the other side's
photo (`seedFromPixels`) or placeholder colour.

**Done 28 September (1.4):** 5–7 — two-line previews
(`LocalTwoLinePreviews`); the launcher icon in the eight accent colours,
one `activity-alias` each, switched when the app leaves the screen
(`AppearanceStore.applyAppIcon`, since switching the alias a task was
started through closes it on many launchers); and Less motion
(`LocalReduceMotion`): no container transforms, screens fade rather than
slide, the standard motion scheme, a typing avatar holding still. On the
owner's word the same day the wallpaper became a row of picture cards
instead of a segmented row, Gradient and Waves were made bolder — Gradient
and Plain could hardly be told apart — and two were added: Aurora and
Shapes (Material's shapes, scattered). The same release fixes forward,
delete and edit on live accounts (TDLib moved the permission flags to
`getMessageProperties`) and reports voice and video notes as listened
(`openMessageContent`).

**Changed 29 September (1.5), on the owner's word:** the icon colour is
out of Appearance — the coloured icons did not look good enough yet. The
aliases stay one more release so that an install that picked another
colour is put back on Teal as it leaves the screen
(`AppearanceStore.restoreLauncherIcon`); after that they and their
mipmaps can go. The wallpaper cards are a grid of three a row, because in
the sideways row Dots and Waves sat past the edge and read as lost.
Gradient is withdrawn — on a grey palette it muddied the chat — and a
stored "Gradient" reads as Plain, now the default. Four patterns were
added, each one mark in the same ink so the mark is the difference:
Zigzag, Crosses, Rings and Sparkles. `AppearanceTest` holds every
wallpaper that shipped, so none goes missing unasked.

**Also 29 September (1.5), from the owner's use of 1.4.1:**
- Selecting messages is Material's list selection: a leading `Checkbox`,
  the whole row a target, a chosen row washed in the primary colour; the
  bubble keeps its own colour. The count in the toolbar is centred and
  rolls rather than shifting the buttons.
- The expression panel pulls up to three quarters of the screen by
  Material's drag handle, or a tap on it.
- A message that is one emoji plays Telegram's animation of it
  (`messageAnimatedEmoji`, drawn as a sticker); one to three emoji without
  an animation are drawn large (`jumboEmojiCount`).
- Photos, videos and GIFs show their minithumbnail, blurred, until the file
  is here (`MiniThumbnail`, `BlurredMini`) — the one place blur is granted.
- A sticker still loading is a breathing skeleton, not its emoji.
- Reaction chips are narrower, with a larger emoji.
- ~~Still missing: Premium custom-emoji packs in the emoji tab~~ — in
  1.5.1, see below.

**1.6 — 29 September, stories and video (the owner chose the theme):**
posting a story (item 11 above); videos at 0.5×, 1×, 1.5× and 2× from a
text button in the player, the speed kept for the next video
(`nextPlaybackSpeed` in `:core`); and picture-in-picture — a video left
playing goes into the system's small window on the way out (auto-enter
from Android 12, onUserLeaveHint before), or from the player's own
button, with the controls left out while it is small (`PictureInPicture`).

**Also in 1.6, on the owner's word the same day:** the chat backgrounds are
cut to four — Plain, Dots, Sparkles and a new Grid — on the base and ink
Dots had before 1.5, in one row of cards, with none of the motion the
choice had picked up; the item is "Chat background", so it is not taken
for the phone's wallpaper. A stored name that is gone reads as Plain. The
chat's top bar has a solid fill (`surfaceContainer`) so a pattern no longer
shows through it, and the conversation runs under the navigation bar to the
bottom of the screen instead of stopping on a strip of bare background.

**1.6.1 — 29 September, groups (the owner's word: "1.7 is far too early"):**
admins and permissions, invite links and forum topics, below in section 6.
`GroupManagement` and friends in `:core` (`Groups.kt`, with tests) decide
what the screens offer; `TelegramGroups` is the twelfth domain interface.

**1.6.3 — done 30 September.** Everything below is in, as planned, with
these notes on how:
- **Shared media** is `SharedMediaKind` in `:core`, a TDLib search filter
  per tab, paged by `SharedMediaViewModel`. Files open through FileProvider
  from a copy in `opened/`, from the tab and from a bubble alike.
- **The player** is `MusicPlayer`, one for the app: ExoPlayer, one track
  at a time and fetched as it comes up. `PlaybackService` gives it a Media3
  session whose Next and Previous the queue answers. The queue is
  `MusicQueue` in `:core`, only ever appended to.
- **The seven:**
  - My music is TDLib's global `searchMessages` with the music filter;
  - Saved Messages takes a forward;
  - offline pages the chat to its first track, then fetches each;
  - resume applies from ten minutes of track (`resumeFrom`);
  - the colour is `seedFromPixels` over Telegram's cover thumbnail, or a
    hue from the name without one;
  - the sleep timer is `SleepTimer`;
  - the equaliser is the platform's panel, offered only where a phone has
    one.
- **The base, finished before release:** the order is one button with a
  menu — in order, reversed, shuffle — kept, with repeat, when another
  chat's music becomes the queue; speed runs 0.5× to 2×; Save offers
  "Play saved", which makes Saved Messages' music the queue and carries on
  with the same track where it was; and search has a Music tab, whose
  queue pages on with the same query (My music's too — it used to page on
  with everything).
- **Downloads**, asked for on the day, and built against what people
  complain about in Telegram's own download manager — read before a line
  of it was written:
  - *nobody finds it* (it lives in a tab of search, behind an icon that
    shows only mid-download) → it is on the chat list's menu and in Data
    and storage;
  - *"downloaded" files are nowhere a file manager looks* (they sit in the
    app's own storage since Android 11) → the screen says so, and every
    finished file has Save to Downloads, which puts a copy in
    Downloads/TelegramYou and marks the row "in Downloads";
  - *clearing the cache silently empties the list* → a finished file whose
    bytes are gone stays listed as "Removed from the phone — tap to
    download again", and the clear-cache dialog says downloads go too;
  - *"clear" does not say whether it deletes* → Remove from list and Delete
    from phone are two actions, the second confirmed with the size it
    frees, and Clear finished asks separately about deleting;
  - *downloads stop with the screen off, unseen* → the sync service already
    keeps the process up; a progress notification with Pause all says it
    is happening, and tapping it opens Downloads.
  It is TDLib's own list (`addFileToDownloads`, `searchFileDownloads`,
  `toggleDownloadIsPaused`, `removeFileFromDownloads`), so it survives
  restarts and updates. Only what a person asked for goes in — a file
  opened, music kept for offline — never thumbnails or stickers. A pause or
  cancel is not reported as a failure in the chat that started it.
- **The music pains found in Telegram's tracker, fixed before release:**
  - a voice message stopped the music for good there, and here played
    over it; now `AudioFocus` asks the system to *duck* other audio under
    a voice message or a round video with sound (the owner's call: "like
    KION's music going quiet under a voice message"), and to *pause* it for
    recording and for a full-screen video, which ExoPlayer and every other
    player then resume;
  - tracks posted as an album played backwards, or out of it — the list is
    newest first; `MusicQueue` now plays an album first to last in either
    direction (tested);
  - shuffle and repeat are media buttons in the shade and on the lock
    screen, and the session answers a watch's or a car's shuffle and
    repeat, because they are the queue's own.
- The tabs of shared media and search are as wide as their names, as the
  folders are (`minTabWidth = 0.dp`); Material's 90dp minimum made "Files"
  and "Voice" take a third of the phone.
- **Still open:** the playlists of 1.6.4, and album covers larger than
  Telegram's thumbnail.

**1.6.3 — next, on the owner's word of 29 September: files, properly.**
A chat's shared media in tabs as the official client has them — photos and
videos, files, music, voice, links, GIFs — where today there is only the
photo and video grid. And a player to go with it, made to feel like
Material 3 Expressive rather than a row of buttons: a mini player that stays
over the screens while something plays, a full player with the cover, a
wavy progress and Expressive's shapes, the chat's music as its queue, and
the controls in the notification shade (Media3's session). The official
client is the reference for what is there and how it behaves.

Listening to music, asked for by the owner's brother and agreed on the same
day — the official client's base first:
- a mini player over the chat list and inside chats, playing on across
  screens; the chat's music as the queue, in order, reversed or shuffled,
  repeating one track or all;
- the lock screen, the shade, headset buttons and Bluetooth, through a Media3
  session; playing on with the app in the background;
- music in global search; speed from 0.5× to 2× for long audio.

And seven things the official client does not do, or does awkwardly, all
seven wanted:
1. **My music** — everything anybody sent or saved, from every chat, in one
   place, searchable by title and performer.
2. **Saved Messages as a library** — Save sends a track there and queues it.
3. **Offline** — one button on a music channel downloads all of it ahead.
4. **Carry on where it stopped** for long tracks and podcasts.
5. **The cover's colour** — the player takes its scheme from the cover
   (Material You from the picture, a dynamic scheme on that seed).
6. **Sleep timer** — stop after 15, 30 or 60 minutes, or at the end of a track.
7. **Equaliser** — the platform's `AudioEffect`, nothing hand-rolled.

What the brother finds maddening in the official player, and so the queue's
first requirement: its track list shows a window of the chat, not the whole
history, and it jumps or vanishes when a track is started. Here the queue is
all of the chat's music — `searchChatMessages` with
`searchMessagesFilterAudio`, paged in as it scrolls, back to the first track
ever posted, with the count shown — and starting a track never rebuilds it:
the track lights up where it is and the list stays where the thumb left it.
Only opening another chat's music replaces the queue. A search over the queue
and a jump back to what is playing come with it, and the list lives on its
own screen or the player's sheet, not in a popup a stray tap closes.

**1.6.2 — 29 September, a small media step.** Music files already played —
the ROADMAP said otherwise and was stale — and now seek on Material's slider;
the demo's song is a chime it writes itself, so it plays offline. A story
this account posted is in the rail as "My story" and opens in the viewer
(the add entry becomes "Add story"). New story previews a chosen video.
Recording round video messages moved out of it: it needs CameraX, whose
versions cannot be read from where this is written — the Build workflow
prints them now — and a camera screen is not a thing to add blind.

**1.6.4 — done 30 September: the library, and four things the pains of
1.6.3 turned up** (chosen by the owner the same day; Android Auto and a
watch's browsing were left for later, since nothing here can test them).
- **Up next** — `MusicQueue.upNext` and `interlude`: tracks lined up from
  anywhere play after the current one and before the queue goes on from
  where it was; kept when another chat's music replaces the queue. Play
  next and Add to queue on a track's message menu, in My music and in the
  library; Up next heads the queue sheet. Tested in :core.
- **No gaps** — the next track's file is fetched while this one plays.
- **Voice messages in a row** — `VoicePlayback`, one for the app: the
  chat's voice messages below the one tapped follow it, each reported
  listened to; it goes on when the chat is closed, under a bar in the
  mini player's shape (tertiary, so it is not taken for a song) with the
  speed — 1×, 1.5×, 2×, remembered — pause and stop. It still ducks the
  music, as 1.6.3 made it.
- **The library**, below, as planned — built by `buildLibrary` in :core
  from the same global search as My music: an album is tracks posted
  together as one post (Telegram has no albums), an artist a performer, a
  playlist a chat with music, Saved Messages first. The front page is
  Material's carousel of what just arrived and "From your chats", what
  people (not channels, not you) sent; each track says who sent it where.
  The player's menu has a row of reactions onto the track's own message
  and Open chat. Switched on in For geeks → Experiments, reached from My
  music.

**1.6.5 — done 30 September: a crash fix.** The player's cover grew and
shrank on playback through its padding on an expressive spring, which
overshoots: padding sprang below zero and Compose threw, closing the app
when a track started or paused. CI never saw it — the emulator runs with
animations off — and the owner's copy of For geeks → Diagnostics → Last
crash named it. The cover scales instead. Also: a guarded start for the
playback service from the background, and the connection service as
remote messaging from Android 14 (data sync is capped at six hours a day
from Android 15).

**1.6.6 — done 30 September: the player within reach.**
- **The player in the shade and on the lock screen**, which it never was:
  the media session was not added to the playback service, and Media3
  shows only the service's sessions. The smoke test now opens the shade and
  looks for it.
- **Pull the player down to close it**, from anywhere on it.
- **Titles on one line**, running across with `basicMarquee` when too long.
- **The library as a Music tab** on the bottom bar while it is on; "My
  music" opens it; **Saved Messages leads it**, even with one track.
- **The connection notice at minimum importance** and posted once per
  service. It cannot go: there is no Google push here.

**1.6.7 — done 30 September: music at hand.**
- **The mini player at the foot** — over the navigation bar, over the
  composer in a chat — and **swiped sideways** to change track. The voice
  bar stays at the top, with the same thin progress line.
- **The player rises from below** instead of 1.6.6's container transform,
  which the owner found heavy to follow.
- **Open chat lands on the track's message**, from the player and the
  library.
- **The cover breathes into a scalloped square** on the beat — a shallow
  `Morph` from graphics-shapes — instead of the rippling edges, which read
  as twitching.
- **Large titles for Music and Search**, as Settings has.

**1.6.8 — done 30 September: the player as a sheet.** From the owner's test
of 1.6.7 on a phone: the player opens as Material's modal bottom sheet,
straight to full height (`skipPartiallyExpanded`), so its opening and
closing are the sheet's own; the mini player is swiped away to stop the
music rather than to change track, which read as dismissing it; the reply
banner and attachment chip fold as they go; and an audio message's
caption, laid in the bubble's Box, is under the track instead of over it.
The player's previous, play and next became a `ButtonGroup` (weighted, as
the profile's must be), and Appearance → Music can put the mini player back
at the top, off by default.
From a user: the chat list shows the ticks of the account's own last message
(`ChatPreview.lastMessageStatus`, from the message's sending state and the
chat's `last_read_outbox_message_id`).
The composer's jump as the keyboard closed, filmed by the owner, was the
gesture bar shrinking back at the end of the keyboard's animation; the lift
is measured from the resting bar now. A flick back towards the newest
message within 2.5 s of a drag that hid the keyboard brings it back — the
simple half of iOS's interactive dismissal. The other half, the keyboard
following the finger, is not wanted — the owner's word.

**1.6.9 — done 1 October: the mini player floats.** From the owner's test
of 1.6.8: the capsule sat in a band of the page's background, because every
page took the whole of its Scaffold's padding and stopped above the
bottomBar. Pages take the top and sides now (`withoutBottom`) and add the
foot to their list's contentPadding, so they run on under it. On the Music
tab the tabs, and the mini player when it is at the top, are painted in the
large bar's own colour as it folds — its `containerColor` to
`scrolledContainerColor`, eased by `collapsedFraction`, as the bar does
itself — so bar and tabs read as one head. The swipe that stopped the music
is gone: it missed more than it landed, and the cross is enough.

**1.6.10 — done 1 October: avatars after a cache clear.** From the owner's
phone: every avatar blank after Settings → Storage had cleared profile
photos, back after a restart. Chats and users are cached as TDLib last
described them, and deleting the files does not redescribe them, so the
cached object went on calling a deleted file downloaded
(`localPathIfDownloaded`), and each picture was asked for once per process.
The path is now checked on disk, a clear that includes profile photos asks
for them all again, and a download that stops short is retried up to three
times. Saved Messages and bots no longer show an online dot — the first
is the account itself, the second a program — and a bot's header says
"bot". The reply banner and the attachment chip keep what they showed while
they fold, so the mini player above glides down instead of dropping. And
For geeks, gone over switch by switch at the owner's ask: all eleven reach
what they say, but Hide the All tab took the archive with it (its row is
the All page's) — it is in the menu then — and the demo ignored Forward
without quoting, so nothing could test it; a new UI test drives five of
the switches that had none. What's new in App update now says when the version came out
(`releasedLabel`, from the release's `published_at`). Also the dependency bump of the same day: androidx activity 1.13,
core 1.19, lifecycle 2.11, navigation 2.10, datastore 1.2, coroutines 1.11,
AGP 9.4.1, Gradle 9.8 and the CI actions on Node 24.

**1.7 — done 2 October: round video messages.** The camera button,
held past a long press, records one: CameraX's Preview drawn by
`CameraXViewfinder` clipped to a circle, and a `VideoCapture` recording
SD under a square `ViewPort`, so the file is the square Telegram wants —
`inputMessageVideoNote` with the side read back from it. Lift sends,
aside throws away, up locks; locked, the circle carries Delete, Switch
camera and Send, and the recording is persistent so the camera turns
without ending it. The app now declares CAMERA, which makes Android refuse
`ACTION_IMAGE_CAPTURE` without it, so the photo button asks too. The UI
test's emulator has an emulated front camera. The player's menu gained
Share and lost its arrow down, and closes by carrying the pull's own
speed to the edge instead of handing over to the sheet's `hide()`, with a
scrim of its own that fades with the pull. Forwarding opens the chat it
went to and searches contacts. The owner's tracks sometimes skipping or stopping
by themselves, not yet reproduced: the player logs every play, pause,
skip and error with Media3's reason (`PlayerLog`, For geeks →
Diagnostics), retries a failed fetch and a failed playback once, and says
so when it gives up — it had stopped in silence. Also in it, the back gesture. The dependency bump took
navigation-compose from 2.8 to 2.10, and from 2.9 the predictive back
gesture no longer runs a NavHost's pop transitions: it has its own
(`predictivePopEnterTransition`/`predictivePopExitTransition`, defaulting
to a scale to 70%). The owner saw a swipe back from Proxy move unlike the
arrow. The NavHost now passes its pop transitions for the gesture too, and
repeats the per-destination ones (Home under a chat, the container routes)
since a string-route `composable` cannot set them itself.

**1.6.4 — the owner's idea: a music library, as an experiment.** Off by
default, under For geeks. Every track in every chat
(`searchMessages` with `searchMessagesFilterAudio`) as a music app would
lay it out:
- **Albums, artists, tracks and playlists.** Telegram's audio carries a title
  and a performer but no album, so tracks group by performer and cover at
  once and by their own tags (`MediaMetadataRetriever`) once downloaded.
  Playlists are Saved Messages and the music channels.
- **Album and artist pages** with the cover, its colour on the player, Play
  all and Shuffle.
- **A home** with Material's carousel of what arrived lately and what to
  carry on with.
- **Social**, which no music app has:
  - who sent each track, where and when, a tap away from the message;
  - "What your chats are playing", the newest music from friends and
    channels, which is honestly "what they send": Telegram does not say who
    listened;
  - what was forwarded and reacted to most;
  - a reaction from the player onto the message itself;
  - Share, and Reply to whoever sent it.

Built on 1.6.3's player and queue. Indexed and kept on the phone only.

**Later, polish rather than basics:** recording round video messages; a photo
editor; chat wallpapers and themes; global notification settings (sounds,
per type); location and contacts in messages; inline bots and Mini Apps;
translation; languages (last, on purpose).

From Nekogram's list, the ones worth taking next once the basics hold:
the user's id on their profile, "delete all my messages" in a group, hide
the keyboard while scrolling, and sending a sticker as an image — all four
in 1.5.1.

**1.5.1 — 29 September, a smaller step on purpose (the owner's word: "we
are flying too fast").**
- **Custom-emoji sets in the emoji tab** — the account's Premium sets
  (`getInstalledStickerSets` with `stickerTypeCustomEmoji`) as tabs over
  Android's picker, each a grid of its emoji playing. One tapped goes into
  the draft as its plain emoji and is remembered beside it
  (`PickedEmoji`); as the message is sent `placePickedEmoji` in `:core`
  finds each in the text TDLib's markdown gives back and adds its
  `textEntityTypeCustomEmoji`. Without Premium the tab says so.
- **Sending where you are** — the paperclip's Location asks for the
  permission at that moment, finds the phone through the platform's
  `LocationManager` (fused, then GPS, then network; the last known fix
  after fifteen seconds) and sends `inputMessageLocation` once confirmed.
  No map is drawn, as with the cards.
- **The user's id** as the last row of a profile, copied on a tap.
- **The keyboard goes away** when the conversation is dragged by hand.
- **Delete all my messages** in a group's info, confirmed: found with a
  sender filter and deleted a page at a time, since TDLib's own call for it
  needs admin rights.
- **Send as image** on a still sticker held down in the panel: flattened on
  white into a JPEG and sent as a photo.


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
