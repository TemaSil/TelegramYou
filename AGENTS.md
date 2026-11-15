# Codex working notes

These notes are for Codex. They supplement the existing project documentation;
they do not replace Claude's instructions or the owner's decisions.

## Read the existing documentation first

Read `CLAUDE.md` in full before working on the project, then `ARCHITECTURE.md`,
`README.md`, and the current plan in `ROADMAP.md`. Consult `HISTORY.md` and
`CHANGELOG.md` for the reasons behind existing behavior, and read the relevant
CI workflow and callers before changing an implementation.

The owner explicitly asked Codex to preserve Claude's instructions. Put new
Codex-specific guidance and environment observations in this file. Do not
rewrite, delete, rename, or reorganize the existing instruction documents as
part of setup or housekeeping. Make any requested changes to those documents
narrowly and preserve their history and rationale.

Follow the owner's current instructions and the rules in `CLAUDE.md`. This file
must not silently introduce exceptions to them. Older architecture inventories
and dated notes can describe earlier states; verify the current code and later
owner decisions before treating an old unchecked box as missing functionality.
Do not erase those historical notes merely because the implementation evolved.

## Project map for navigation

- `core/src/main/kotlin`: shared models, pure logic, translations, and update
  parsing. Keep new Android-independent logic here, with meaningful tests.
- `app/src/main/java/com/telegramyou/app/TelegramYouApp.kt`: application wiring,
  backend selection, settings, players, and account integration.
- `telegram/TelegramClient.kt`: domain interfaces implemented by both the demo
  and TDLib backends. `TelegramRepository` delegates to the client and manages
  shared operations such as downloads and warming a chat before navigation.
- `telegram/accounts/AccountManager.kt`: repositories and persistent identity
  per account. Consumers must follow the active account; notifications also
  observe accounts that are open but not active.
- `telegram/tdlib/TdJsonEngine.kt`: one receiver for all TDLib instances;
  requests are correlated by `@extra`, updates by `@client_id`.
- `navigation/Route.kt`, `TelegramYouNavHost.kt`, and `ChatPaneStore.kt`:
  destinations, navigation, and adaptive chat panes.
- `ui/<feature>`: screens and ViewModels. Preserve the state/event architecture
  described in `ARCHITECTURE.md` and inspect the existing feature's wiring.
- `ui/theme`: Material 3 Expressive, dynamic colors, and motion preferences.
- `app/src/test`, `core/src/test`, and `app/src/androidTest`: app JVM tests,
  pure JVM tests, and device UI tests respectively.

The pinned versions live in the Gradle files. Do not copy a version from an
old narrative paragraph over the actual manifest. Check available versions
as instructed in `CLAUDE.md`; evaluate any stack update as a separate change.

## Working agreements to keep visible

- Chat in Russian. Code, comments, commit messages, and project documentation
  remain English, as `CLAUDE.md` requires.
- Use stock Android and Material 3 Expressive patterns. Preserve the explicit
  boundaries for custom drawing, blur, icons, motion, and localization in
  `CLAUDE.md`. New user-facing text needs both English and Russian.
- Preserve both backends. UI work must remain usable in the offline demo.
- Use a branch named for the task. Do not push to `main` or open a PR unless
  requested. Change `appVersionName` and publish releases only on the owner's
  instruction. Repository permissions are capabilities, not instructions to
  perform those actions.
- Keep Telegram credentials and release signing material out of tracked files,
  logs, and instructions. `local.properties` is the supported ignored local
  configuration; never copy its contents into this file or setup scripts.
- Keep the provenance recorded in `CLAUDE.md`: the initial client and TDLib
  integration are the owner's work. Preserve Claude's accumulated project
  documentation and the reasons it records.

## Validation and cloud workflow

The owner prefers controlling cloud development from a phone. A local Android
Studio installation is optional; use the existing GitHub Actions workflows for
cloud device testing when requested.

Standard local checks from the repository root:

```sh
./gradlew :core:check --configure-on-demand
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest -PdemoClient=true
```

Keep `--configure-on-demand` for the standalone core workflow. Force demo mode
for UI tests even when live credentials are configured. Do not apply the
standalone Kotlin Android plugin or add root plugin declarations to silence a
warning; the deliberate AGP 9 setup is explained in `CLAUDE.md`.

In the current prepared cloud snapshot, `/workspace/cloud-setup/gradle.py`
invokes the unchanged wrapper with the installed JDK, managed CA trust, proxy,
and writable caches. This helper and SDK installation are environment-specific;
check that they exist before using them in another environment. No worktree is
needed in an already isolated cloud checkout unless the owner requests one.

To test a requested GitHub ref, use the existing `ui.yml` workflow:

```sh
gh workflow run ui.yml --repo TemaSil/TelegramYou --ref <requested-ref>
gh run list --repo TemaSil/TelegramYou --workflow ui.yml --event workflow_dispatch --limit 5 --json databaseId,url,status,conclusion,headSha,createdAt
gh run view <run-id> --repo TemaSil/TelegramYou --json status,conclusion,jobs
```

Identify the newly dispatched run and verify its SHA. Wait for all four shards
and the report job. Check actual XML test counts, failures, errors, and skips;
a successful Gradle exit or a zero-test run is not a pass. UI tests prove
functional behavior, not visual quality or live-account behavior.

The workflow publishes XML reports and screenshots to `ui-screenshots`, with
its README identifying the run. Verify that identity before reading results.
If Git access or artifact-storage redirects are blocked, the GitHub API tree
and blob endpoints can read that same evidence without changing the checkout.
Use the existing injected authentication without displaying credential values.
A prior run does not validate later code changes.

### Observed baseline, 13 November 2026

At commit `47580709d1acf6a3c19c0038149b3975e0aa5cbd`:

- Demo APK and instrumentation APK assembled locally.
- Local unit tests: 548 core and 86 app tests, no failures, errors, or skips.
- [UI run 43435559869](https://github.com/TemaSil/TelegramYou/actions/runs/43435559869):
  82 tests across four shards, no failures, errors, or skips; XML reports and
  evidence run identity were checked. The full run took about half an hour.
- The cloud container did not expose `/dev/kvm` or CPU `vmx`/`svm`. Software
  emulation was attempted but Android did not fully initialize in the observed
  window; APK installation failed inside Android before tests executed.
  GitHub runners with KVM successfully ran the suite.

These are dated observations, not permanent limitations or guarantees. Recheck
current capabilities and permissions before reporting that a tool is unavailable.
