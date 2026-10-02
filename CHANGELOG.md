# Changelog

What each version of TelegramYou brought, newest first. The APK of every
one is on the [releases page](https://github.com/TemaSil/TelegramYou/releases);
the app shows the same notes itself when it updates (Settings → About).

The front page of the repository names only the latest version — this is
where the rest lives. When a version is released, its notes go at the top
here and its one-paragraph summary replaces the "Latest" section of
README.md.

## 1.8

- **Translate a message** — from its menu, into the phone's language, by Telegram's own translator: the one the official client uses, so the result is the same. The text can be selected or copied whole.
- **Photos and videos open out of their bubble** and close back into it — the picture grows from where it sits in the chat to full screen on the theme's spring, and shrinks back into whichever photo is in view when it closes, a drag down included. A video shows its poster while it starts, so what grows is the picture, not a black frame. With Less motion on, or with the bubble scrolled away, it fades instead. The viewer no longer leaves the system's own fade to the window either, which on a slow phone could be caught half gone over the chat.
- **More in a message's menu** (For geeks): **Repeat** sends it again into the same chat, **Save to Saved Messages** forwards it there in one step, and **Delete from this phone** takes a downloaded file off the phone while leaving it on Telegram, to be fetched again when next opened.
- **Pull down for the archive** (For geeks) — the chat list's pull opens the archive instead of refreshing, while there is an archive.
- **Hide blocked people in groups** (For geeks) — their messages left out of a group's history, as if they were not there.
- **Ask before sending a recording** (For geeks) — a voice or video message waits in the composer when the finger lifts, to be sent or taken back, instead of leaving at once.
- **Silence people not in contacts** (For geeks) — a private message from someone not saved still reaches the shade, without a sound. Bots are left as they are.

The For geeks additions follow Nekogram's settings of the same intent, read as a list of what people asked for; none of its code is in this app.

## 1.7

- **Round video messages, recorded.** Hold the camera button in the composer and the front camera comes up in a circle over the chat; lift to send, slide aside to throw it away. A ring fills towards Telegram's one-minute limit, where it sends by itself. A tap still takes a photo.
- **Locked recording.** Slide up while holding and the recording goes on with the finger off the screen, with Delete, Switch camera and Send under the circle. The camera turns round without ending the recording.
- **Forwarding opens the chat it went to**, as the official client does, so what was forwarded is seen arriving. And the forward sheet has a search, over the chats and over contacts there is no chat with yet.
- **The player closes smoothly** — a pull lets go into a slide that carries the finger's speed to the bottom, with the dimming fading alongside, instead of the sheet setting off again from a standstill. And its arrow down is gone: the handle, a pull and Back close it.
- **Share from the player** — the track's file through Android's share sheet when it is on the phone, otherwise its performer and title. Everyone's player has it, not only the library's.
- **Music says why it stopped.** A track that could not be fetched, or failed to play, used to stop the music with nothing said; it is now tried again, and named if it still fails. And For geeks → Diagnostics has a **Player log**: every play, pause and skip with the reason the system gave — a call, headphones out, a car's button — for music that seems to stop or skip by itself.
- **Fixed: the back gesture moved differently from the back arrow.** Since the navigation library's update in 1.6.10, a swipe back shrank the page instead of sliding it away. The gesture follows the same transitions again — and a chat or a story still shrinks back into its row.
- A photo from the composer now asks for the camera first: the app records video messages itself, and Android then wants the permission for the camera app as well.

## 1.6.10

- **Fixed: avatars stayed blank after clearing the cache** (Settings → Storage) until the app was restarted. The app kept calling the deleted pictures downloaded and never asked for them again; it now checks the file is still there, and asks again straight after a clear.
- **An avatar whose download was cut off is asked for again**, up to three times, rather than once for as long as the app runs — which, with the connection kept alive, could be days.
- **No online dot for Saved Messages or bots** — Saved Messages is the account itself, always online while it uses the app, and a bot is a program. A bot's chat says "bot" under its name, as the official client does.
- **The mini player glides back down** when a reply or an attachment is dismissed. The banner was emptied before it folded, so it had no height to fold from and the capsule above dropped in one jump.
- **For geeks, checked over** — with Hide the All tab on, the archive (whose row only All carries) is in the chat list's menu; a double tap set to copy does nothing on a message with no text, rather than emptying the clipboard; and a UI test now drives stories hidden, All hidden, search without the keyboard, double tap to reply and forwarding without quoting.
- **What's new says when** — the notes in Settings → About carry the version's release date: "Released today", "yesterday" or the date, from the release on GitHub.
- **Up-to-date libraries** — AndroidX activity, core, lifecycle, navigation and datastore, which had fallen a year behind, along with coroutines, Coil and the build tools.

## 1.6.9

- **The mini player floats** — the chats, Music, Settings and the rest run on under it, where a band of background used to sit round the capsule.
- **One head on the Music tab** — as the page scrolls under the bar, the tabs below it, and the mini player when it is at the top, take the bar's colour with it.
- **No swipe on the mini player** — it missed more often than it landed on a phone; the cross stops the music.

## 1.6.8

- **The player as a bottom sheet** — Material's modal sheet, opened straight to full height, closed by its handle, a pull down or Back.
- **Swipe the mini player away** to stop the music, as a notification is swiped away. Swiping it to change track, new in 1.6.7, read as dismissing it on a phone.
- **A smoother composer** — the reply banner and the attachment chip fold away rather than vanish, so the mini player and the capsule settle instead of jumping.
- **The player's buttons as Expressive button groups**, both rows — the one under the finger widens and the others give way.
- **Mini player at the top** (Appearance → Music), off by default — for whoever preferred it under the title, as until 1.6.7.
- **Ticks in the chat list** — when the last message is yours, one tick for sent and two for read before the time, as the official client has it; a clock while it sends. Asked for by a user.
- **The composer no longer jumps as the keyboard goes** — on gesture navigation the bar under the keyboard shrinks the moment it closes, and the composer used to fall that difference in one step. And **the keyboard comes back** when a drag that put it away is followed by a quick flick back down to the newest messages, as on iOS.
- **Fixed:** a track sent with a caption drew the caption over the track.

## 1.6.7

- **The mini player at the foot of the screen** — over the navigation bar, and over the composer in a chat, where the thumb is. Swipe it left for the next track and right for the one before. A voice message keeps its bar at the top, now with the same thin progress line and a speed button that sits level.
- **The player rises from below** and falls back when pulled down, in place of the container transform of 1.6.6.
- **Open chat goes to the track** — from the player or the library, the chat opens at the track's own message, lit for a moment.
- **The cover breathes** — on the beat it morphs a little into a scalloped square from Material's shape library and settles back, instead of the rippling edges that read as twitching.
- **Large titles for Music and Search**, folding as the page scrolls, as Settings has.

## 1.6.6

- **The player in the shade and on the lock screen** — it never was: the media session was not the playback service's own, and Android shows only the service's.
- **The player opens out of the mini player** as one container, the way a chat opens out of its row, and **closes by pulling it down** from anywhere on it.
- **A slimmer mini player** — its progress is a thin line along the bottom edge instead of a wave on a row of its own.
- **Titles on one line** — a long one runs across rather than wrapping, so the controls no longer move from track to track.
- **Music library** (For geeks → Experiments) — a Music tab on the bottom bar, "My music" opens it, and Saved Messages leads its front page.
- **A quieter connection notice** — the "connected" notification Android requires for the background connection is at minimum importance: no status-bar icon, folded at the foot of the shade, and it no longer comes back each time the app opens.

## 1.6.5

- **Fixed: the app closing when a track started or paused.** The player's cover springs slightly past its size as it plays; that spring was on its padding, which cannot go below zero. It is a scale now.

## 1.6.4

- **Up next** — Play next and Add to queue on any track, from a chat, My music or the library, before the queue goes on.
- **Voice messages, properly** — a run of them plays one after another, keeps going when you leave the chat with a bar to pause, change speed or stop, and 1×, 1.5× or 2× is remembered.
- **No gaps** — the next track is fetched while this one plays.
- **A player shaped like Material means it** — play is the wide one, previous and next squared-off beside it, repeat and speed as pills, the sleep timer and the queue as chips. The cover's corners are squarer, and its edges ripple a little on the beat (Appearance → Motion to turn it off).
- **Last crash** (For geeks → Diagnostics) — if the app closes on its own, what happened is kept on the phone to read and copy into a report. Never sent anywhere.
- **Music library** (For geeks → Experiments) — every chat's music as albums, artists and playlists, what people in your chats sent, and a reaction onto a track's message from the player.

## 1.6.3

- **Shared media in tabs** — media, files, music, voice, links and GIFs, back to the first thing sent; files open in the app that reads them.
- **Downloads** — everything you downloaded in one place, on the main menu: pause, resume or cancel, a notification while it runs, save a copy to the phone's Downloads, and files cleared from storage stay listed to fetch again.
- **A music player** in Material 3 Expressive: a mini player that follows you, a full player in each track's own colours, and the chat's whole music as a queue that never jumps.
- **My music** — every chat's tracks in one place, searchable, and a Music tab in search.
- **Play it your way** — in order, reversed or shuffled, repeat one or all, 0.5× to 2×; an album plays as it was posted, and shuffle and repeat are in the shade and on the lock screen too.
- **Music makes room** — it turns down under a voice message or a round video and comes back after, and pauses while you record or watch a video full screen, then plays on.
- **Sleep timer, equaliser, Saved Messages as a library, offline** — save a track and play on from your saved music; a whole chat's music onto the phone; long tracks carry on where they stopped.

## 1.6.2

- **Music files** play with a slider to drag anywhere in the track.
- **Your own story** in the stories row, opening like anyone's; a picked video previews before posting.

## 1.6.1

- **Admins and permissions** — admins with the rights and title you choose; stop someone writing or remove them; what members may do; a search over the members.
- **Invite links** with a name, a time limit, a number of people or admin approval — and join requests.
- **Forum topics** — a forum opens onto its topics; read, write, start, rename, close; a draft per topic.

## 1.6

- **Post your own story** from *My story* — a photo or a video, for everyone, contacts or close friends.
- **Video speed** — 0.5×, 1.5× or 2×, kept for the next video.
- **Picture-in-picture** — a video keeps playing in a small window when you leave.
- **Calmer chat backgrounds** — Dots, Sparkles or Grid, and a solid top bar.

## 1.5

- **App lock** — a PIN or your fingerprint in front of the app.
- **Contacts and places** as cards; send a contact from the paperclip.
- **Group invite links** — share, revoke, create.
- **Animated emoji**, Premium custom emoji in text, *Add to GIFs*.
- **Wallpapers** in a grid, with Zigzag, Crosses, Rings and Sparkles.
- **Selecting messages** the Material way — checkboxes and the whole row.
- **A taller emoji panel**, larger reactions, blurred previews while media loads.

Every release and its notes: [Releases](https://github.com/TemaSil/TelegramYou/releases).
