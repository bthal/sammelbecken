# Sammelbecken

A tiny Android quick-capture app for Obsidian. Trigger it with the assistant gesture (instead of Google Assistant). It opens with the keyboard up **and** the microphone recording:

- **Speak, then Send.** The recording is saved as `.m4a` in your vault's audio folder, and `- [ ] ![[Audio/2026-10-09_143201.m4a]]` is appended to your note. Obsidian renders that embed as an inline audio player, no plugin needed.
- **Type, then Send.** As soon as you type, the recording is discarded, and `- [ ] your text` is appended instead.
- **Leave without Send** (back, home, screen off): nothing is saved.

It works over the lock screen too. Built for a Fairphone 4 with the vault synced as a plain folder (e.g. via Syncthing).

## Build & install (USB)

Requirements: Android Studio (for its bundled JDK and the Android SDK) and USB debugging enabled on the phone.

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew test assembleDebug
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb" install -r app/build/outputs/apk/debug/app-debug.apk
```

Create `local.properties` with `sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk` if Gradle can't find the SDK.

## Setup on the phone

1. Open **Sammelbecken** from the launcher.
2. **Choose vault folder** and pick your vault's root folder.
3. **Note file:** browse to the markdown file captures should be appended to, or type a vault-relative path. The file is created if it doesn't exist.
4. **Audio folder:** browse or type a vault-relative path. Leave it empty for the vault root.
5. **Grant microphone.**
6. **Open default apps → Digital assistant app → Sammelbecken.** Your assistant gesture (long-press power, or corner swipe) now opens the capture screen.

## Project layout

```
app/src/main/java/io/github/bthal/sammelbecken/
  capture/   CaptureActivity (UI), CaptureViewModel, AudioRecorder
  assist/    VoiceInteractionService plumbing for the assistant role
  vault/     Entries (pure formatting/path helpers), VaultWriter (SAF writes)
  settings/  SettingsActivity, SettingsStore
docs/speech-to-text.md   options for transcribing recordings later
```
