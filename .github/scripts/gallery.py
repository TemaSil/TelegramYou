"""Files one green run's screens into the gallery branch.

The `ui-screenshots` branch is rewritten on every run: it is what the app
looks like now, and nothing else. This keeps a chosen few from every green
build on main, so the way a screen changed from one release to the next can
be seen rather than remembered — and it keeps `latest/`, which is what the
front page shows.

Run by the UI workflow with the gallery branch checked out at GALLERY:

    python3 gallery.py SCREENSHOTS GALLERY VERSION SHA

Only the showcase below is kept, at half size and as JPEG. A run is about
thirty full-size PNGs and several megabytes; this is sixteen pictures
and well under a megabyte, which a branch that only ever grows
can afford. PNG at the same size was still more than twice as heavy, and at
half size the compression is not what anybody will be looking at.
"""

import datetime
import json
import os
import re
import shutil
import sys

from PIL import Image

# The smoke test's own names, in the order the front page shows them. A test
# renamed or removed just drops out of the showcase rather than failing it.
SHOWCASE = [
    ("03-chats", "Chat list"),
    ("04-chat", "Conversation"),
    ("06-group-header", "Group"),
    ("31-video-note", "Video messages"),
    ("34-poll", "Polls"),
    ("35-bot", "Bot buttons"),
    ("37-post-search", "Post search"),
    ("28-old-search-hit", "Search in chat"),
    ("36-search", "Search"),
    ("21-story", "Stories"),
    ("09-attachments", "Attachments"),
    ("12-chat-info", "Chat info"),
    ("32-privacy", "Privacy"),
    ("29-devices", "Devices"),
    ("30-storage", "Data and storage"),
    ("41-settings", "Settings"),
    ("25-updates", "App update"),
    ("33-for-geeks", "For geeks"),
    ("26-qr-login", "QR login"),
    ("27-email-code", "Email login"),
    ("23-proxy", "Proxy"),
    ("13-rail", "Tablet"),
]

WIDTH = 540
REPO = "TemaSil/TelegramYou"


def shrink(src, dst):
    with Image.open(src) as image:
        height = round(image.height * WIDTH / image.width)
        image.convert("RGB").resize((WIDTH, height), Image.LANCZOS).save(
            dst, quality=88, optimize=True, progressive=True
        )


def version_key(name):
    # 1.0.259 after 1.0.99, which a string sort gets backwards.
    return [int(part) if part.isdigit() else part for part in re.split(r"(\d+)", name)]


def main(screens, gallery, version, sha):
    build = os.path.join(gallery, "builds", version)
    latest = os.path.join(gallery, "latest")
    shutil.rmtree(build, ignore_errors=True)
    shutil.rmtree(latest, ignore_errors=True)
    os.makedirs(build)

    kept = []
    for name, caption in SHOWCASE:
        src = os.path.join(screens, name + ".png")
        if not os.path.exists(src):
            continue
        shrink(src, os.path.join(build, name + ".jpg"))
        kept.append({"name": name, "caption": caption})
    if not kept:
        sys.exit("none of the showcase screens was in " + screens)

    about = {
        "version": version,
        "sha": sha,
        "date": datetime.date.today().isoformat(),
        "screens": kept,
    }
    with open(os.path.join(build, "about.json"), "w") as f:
        json.dump(about, f, indent=2)
    shutil.copytree(build, latest)

    write_index(gallery)


def write_index(gallery):
    builds_dir = os.path.join(gallery, "builds")
    builds = []
    for name in sorted(os.listdir(builds_dir), key=version_key, reverse=True):
        path = os.path.join(builds_dir, name, "about.json")
        if os.path.exists(path):
            with open(path) as f:
                builds.append(json.load(f))

    lines = [
        "# TelegramYou, build by build",
        "",
        "The same screens from every green build of `main`, newest first,",
        "taken by the UI workflow on an emulator running the demo client.",
        "Written by `.github/scripts/gallery.py`; do not edit by hand.",
        "",
    ]
    for build in builds:
        sha = build["sha"]
        lines += [
            f"## {build['version']}",
            "",
            f"{build['date']} · [`{sha[:7]}`](https://github.com/{REPO}/commit/{sha})",
            "",
            "<p>",
        ]
        for screen in build["screens"]:
            src = f"builds/{build['version']}/{screen['name']}.jpg"
            lines.append(
                f'  <img src="{src}" width="160" alt="{screen["caption"]}" '
                f'title="{screen["caption"]}">'
            )
        lines += ["</p>", ""]
    with open(os.path.join(gallery, "README.md"), "w") as f:
        f.write("\n".join(lines))


if __name__ == "__main__":
    main(*sys.argv[1:5])
