# Speech-to-text: options

Status: research only, not in v1. Goal: turn voice captures (German and English, mostly short) into text in the note, ideally next to the audio embed, e.g.

```
- [ ] Call the plumber about the kitchen tap ![[Audio/2026-10-09_143201.m4a]]
```

Constraints: Fairphone 4 (Snapdragon 750G, 6–8 GB RAM, stock FP OS with Google services), vault synced via Syncthing, a PC is available.

## Options

### 1. Android `SpeechRecognizer` (system recognizer)
Uses whatever recognition service the phone provides. On FP OS that's Google's. `createOnDeviceSpeechRecognizer()` (Android 12+) forces local processing.

- **Pro:** No model to ship, low effort (~1 day), good German quality with Google's on-device models, fast.
- **Con:** It is designed for **live mic input**. Transcribing an existing `.m4a` file isn't officially supported, so we'd have to recognize in parallel while recording (two mic consumers is awkward and not guaranteed). It depends on Google's service being installed and its language packs downloaded, and behaviour varies by device. It wouldn't work on /e/OS-style ROMs.
- **Privacy:** On-device mode keeps the audio local, but the engine is Google's closed code. The default (non-on-device) recognizer may send audio to Google.

### 2. Vosk (offline, Kaldi-based)
An open-source library with small per-language models (~50 MB for German "small"). It can transcribe files.

- **Pro:** Fully offline and open source, small, fast even on mid-range phones, simple Android API.
- **Con:** Noticeably worse accuracy than Whisper-class models, especially for casual speech, mixed German/English and names. One model per language means no automatic language detection.
- **Privacy:** Excellent. Nothing leaves the phone.

### 3. Whisper on device (whisper.cpp, or sherpa-onnx with Whisper/Parakeet models)
Runs an OpenAI Whisper model (open weights) locally through a native library (JNI).

- **Pro:** Much better accuracy than Vosk, multilingual with automatic language detection, transcribes files after recording, fully offline and open source.
- **Con:** Speed on the FP4 CPU: the `base` model (~150 MB) is roughly real-time or a bit slower, `small` (~500 MB) is several times slower than real time. That means running it as a background job (WorkManager) after Send, not inline. It adds NDK/native build complexity, the model is either downloaded on first use or the APK grows a lot, and it costs battery.
- **Privacy:** Excellent. Nothing leaves the phone.

### 4. Cloud transcription API
Examples: OpenAI (Whisper / gpt-4o-transcribe), Mistral (Voxtral, EU company), Deepgram, Google Cloud Speech, AssemblyAI. The app would upload the `.m4a` after Send and append the text when the result comes back.

- **Pro:** Best accuracy with the least effort (one HTTPS call), handles German/English mixing well, no load on the phone.
- **Con:** Needs a network connection (captures made offline would need a retry queue). Costs per minute (cheap for short notes, but non-zero), and needs an account plus an API key stored on the phone.
- **Privacy:** Weakest. Your voice notes go to a third party. Check each provider's retention and training terms (most API tiers don't train on your data by default, but retain it for up to ~30 days for abuse monitoring) and the jurisdiction (US vs EU/GDPR). Mistral is EU-based. Don't use it for anything sensitive unless you accept that.

### 5. Transcribe on the PC after sync
The phone stays dumb. Syncthing brings the `.m4a` to the PC. A script there (or an Obsidian desktop plugin) runs local Whisper (e.g. `faster-whisper` with `large-v3-turbo`, which is fast on a GPU and fine on a CPU) and rewrites `- [ ] ![[x.m4a]]` to `- [ ] <transcript> ![[x.m4a]]`.

- **Pro:** Best accuracy without the cloud, no work in the Android app, battery-neutral on the phone, and the model can be swapped freely.
- **Con:** Text only appears once the PC is on and synced, so it's delayed. There's a risk of edit conflicts if the script rewrites the note while the phone appends (mitigate by only touching lines that contain an untranscribed embed, and by keeping writes small). One more thing to keep running.
- **Privacy:** Excellent. Everything stays on your devices.

## Comparison

| | Accuracy (de/en) | Effort | Latency | Offline | Privacy |
|---|---|---|---|---|---|
| 1. Android recognizer | good | low, but live-only | instant | yes (on-device mode) | ok (Google engine) |
| 2. Vosk | fair | low–medium | seconds | yes | excellent |
| 3. Whisper on device | very good | high | ~1× real time or slower, background | yes | excellent |
| 4. Cloud API | excellent | low | seconds (online) | no | weak |
| 5. PC-side Whisper | excellent | medium (outside the app) | until next PC sync | yes | excellent |

## Recommendation

- **Privacy-first and simplest:** start with **option 5**. It doesn't touch the app, gives the best local accuracy, and the audio stays the source of truth.
- If text should appear **on the phone right away**, the next step is **option 3** (whisper.cpp `base`, or a sherpa-onnx model, running in the background after Send). Choose **option 4** only if you're fine with the provider seeing the audio. If so, make it opt-in per capture or global, with Mistral as the EU option.
- Skip option 1: the live-only limitation clashes with "record first, decide later", and it ties the app to Google services.

Note: all options keep the `.m4a` in the vault, so a transcript can always be redone later with a better model.
