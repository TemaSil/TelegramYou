#!/usr/bin/env python3
"""Regenerate app/src/main/java/com/telegramyou/app/ui/icons/Symbols.kt.

Downloads each icon below from google/material-design-icons — Material
Symbols, Rounded, 24px, unfilled and filled — and writes them as lazily
built ImageVectors. To add an icon: add its Kotlin name (and, if Symbols
calls it something else, the Symbols name) to ICONS, and run this.
"""
import os
import re
import sys
import urllib.request

# Kotlin name -> Symbols name, when they differ from the snake_case of it.
RENAMED = {
    "AddAPhoto": "add_a_photo",
    "EmojiEmotions": "mood",
    "ErrorOutline": "error",
    "Phone": "call",
    "Poll": "bar_chart",
}

ICONS = """
Add AddAPhoto AddReaction AlternateEmail Archive ArrowBack AttachFile Block Bookmark
Campaign Chat Check CheckCircle Close Computer Contacts ContentCopy ContentPaste DarkMode
Delete DeleteSweep Description DesktopWindows Devices Done DoneAll Download Edit EmojiEmotions Folder
ArrowUpward ArrowDownward
ErrorOutline Forward Group History Image Info InstallMobile Keyboard
KeyboardArrowDown KeyboardHide Language LaptopMac LightMode Link Lock Logout
MarkChatRead Mic MoreVert MusicNote Notifications NotificationsActive
NotificationsOff OpenInNew Pause Person Phone PhoneAndroid PhoneIphone PhotoCamera
Palette PersonAdd PhotoLibrary PlayArrow Poll Public PushPin QrCode2 Reply Schedule Science Search
Send Settings Share Snooze SportsEsports Storage SystemUpdate TabletMac Unarchive
Visibility VisibilityOff VolumeOff VolumeUp VpnKey WorkspacePremium
""".split()

BASE = "https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android"
OUT = "app/src/main/java/com/telegramyou/app/ui/icons/Symbols.kt"


def snake(name):
    return re.sub(r"(?<=[a-z])(?=[A-Z0-9])|(?<=[0-9])(?=[A-Z])", "_", name).lower()


def fetch(symbol, filled):
    url = f"{BASE}/{symbol}/materialsymbolsrounded/{symbol}{'_fill1' if filled else ''}_24px.xml"
    with urllib.request.urlopen(url, timeout=30) as response:
        xml = response.read().decode()
    paths = re.findall(r'android:pathData="([^"]+)"', xml)
    if len(paths) != 1:
        sys.exit(f"{symbol}: expected one path, found {len(paths)}")
    return paths[0], 'autoMirrored="true"' in xml


def main():
    lines = []
    for name in sorted(ICONS):
        symbol = RENAMED.get(name, snake(name))
        for suffix, filled in (("", False), ("Filled", True)):
            path, mirrored = fetch(symbol, filled)
            lines.append(
                f'    val {name}{suffix}: ImageVector by lazy {{ symbol("{name}{suffix}", '
                f'{"true" if mirrored else "false"}, "{path}") }}'
            )
    with open(OUT) as f:
        current = f.read()
    head = current[: current.index("object Symbols {") + len("object Symbols {")]
    tail = current[current.index("\n}\n\nprivate fun symbol"):]
    with open(OUT, "w") as f:
        f.write(head + "\n" + "\n".join(lines) + tail)
    print(f"{len(ICONS)} icons written to {OUT}")


if __name__ == "__main__":
    main()
