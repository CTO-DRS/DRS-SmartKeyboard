# User Guide — DRS Smart Keyboard

- **Version:** 2.0.0 (unified)
- **Languages:** this is the English guide — Arabic version: [USER_GUIDE_AR.md](USER_GUIDE_AR.md)
- **Support:** [SUPPORT.md](../SUPPORT.md) — official channels and crash reports.

## 1. What is DRS Smart Keyboard?

A smart Android keyboard built on one explicit principle: **everything runs on
your device, and nothing leaves it**. Typing, correction, suggestions,
shortcuts, statistics, the economy system and backups — all local. No account,
no sign-in, no ads, and no network unless *you* request an update or a new
package. It is a full system service (IME) that replaces any other keyboard
without you losing anything.

## 2. Install & activate (two minutes)

1. Install the APK (verify `SHA256SUMS.txt` when downloading from GitHub).
2. Open the app → tap **Enable keyboard** → toggle "DRS Smart Keyboard" in the
   system settings (Android asks for confirmation — that is Android's own
   security step, not ours).
3. Return to the app → **Switch keyboard** → pick DRS.
4. Start typing in any field — try gliding between letters for gesture typing.

Upgrading over an older install works in place (same signing certificate since
the first unified release) — you never need to uninstall first.

## 3. A tour of the screens

All settings live in one unified app with clear sections:

| Section | What you'll find |
|---|---|
| **Basics** | Languages & subtypes, typing mode (gesture/tap), height, haptics & sound, themes |
| **Typing** | Correction & suggestions, literal typing, numbers & punctuation, space/backspace behavior |
| **Systems** | DRS systems: economy, profiles, daily stats, backup |
| **Tools** | Text tools, smart shortcuts, tech toolbar, internal clipboard |
| **Statistics** | Real daily numbers: keystrokes, words, speed — all local |
| **Diagnostics** | System status, event log, performance metrics, opt-in crash-log export |
| **Themes** | Built-in theme library + importing `.flex` packages from the package library |

## 4. The features unique to DRS

### Daily economy
Every active writing day earns points computed from your real statistics — a
motivation system with no ads and no purchases, and every number stays on the
device.

### Smart shortcuts
Type an abbreviation and it expands into full text automatically (your email
address, your signature, a recurring sentence). Create shortcuts from the
Tools screen and manage them in the shortcuts panel.

### Profiles
Multiple writing profiles: "Work" with formal tone and tech keys, "Personal"
with emoji and lighter colors — switch from the toolbar profile panel.

### Text tools & smart panels
Fast selection, case conversion, smart paste from the internal clipboard, and
a context-aware tools panel (number, URL, email fields).

### The safe internal clipboard
Your last copied items with previews. Copied media is protected by gates: it
is never written to the system disk nor read before your approval, and its
orphaned files are cleaned automatically.

## 5. Privacy: what actually happens?

- **Network:** the app contacts no server by default. Update checks are manual
  (opt-in), and the package catalog uses HTTPS only when you request a package.
- **Password fields:** learning, voice and context features stop completely on
  password fields — enforced in code, not promised in prose.
- **Statistics & logs:** abstract numbers, never typed text. The crash log
  stores the exception class name and a stripped stack fingerprint — no
  message, no content, no field names.
- **Export:** nothing leaves your device unless you press the button (backup
  export, or a crash report you choose to share yourself).

## 6. Backup & restore

From **Systems → Backup**: create an archive with your preferences, adaptive
dictionary and shortcuts, protect it with a passphrase only you keep (it
cannot be recovered), and store the file wherever you like. Restore brings
everything back on a new device — and never touches the content of an active
field during the operation.

## 7. FAQ

**The keyboard doesn't appear after enabling?** Re-check step 2 (system
enable) then the switch. Try re-focusing the input field.

**No Arabic suggestions?** Download the language pack from the package
library, and make sure correction is on in the "Typing" section.

**Does it drain battery?** There is no background work at all — the app runs
only while the keyboard is open, and state is flushed once every 15 seconds.

**How do I move my setup to a new phone?** Backup on the old device → restore
on the new one. Or use the latest release and verify `SHA256SUMS`.

**Where do I report a problem?** [SUPPORT.md](../SUPPORT.md) — including a
crash report from the Diagnostics screen if available (you send it yourself).
