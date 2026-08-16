# Nutka — Android app

Native Kotlin + Jetpack Compose implementation of the **Nutka** design
(`Nutka.dc.html`, imported from your claude.ai/design project "Dyktafon z
transkrypcją do Notion"), built for Pixel 8 Pro (Android 14/15, edge-to-edge,
Material 3). The Settings screen also carries the options from your
"Transcribe files" screenshot: primary language, tag audio events, include
subtitles, no verbatim, assign speakers from library, and keyterms.

## Opening the project

This environment has no Android SDK / JDK, so nothing here has been
compiled or run. To build it:

1. Install **Android Studio** (Ladybug or newer).
2. Open the `NutkaApp` folder as a project. Android Studio will detect the
   missing Gradle wrapper jar and offer to regenerate it — accept that (or
   run `gradle wrapper` once if you have a system Gradle installed).
3. Let Gradle sync, then **Run** on a Pixel 8 Pro emulator or device.

Package: `com.nutka.app` · minSdk 26 · targetSdk 35 · Kotlin 2.0.21 · Compose
BOM 2024.12.01.

## What's real vs. what needs your input

**Works out of the box, no configuration:**
- Recording (MediaRecorder → AAC/M4A), pause/resume, bookmarks, elapsed timer
- Background recording via a proper foreground service + notification
  ("Nagrywanie w tle" in Settings)
- Live, on-device transcription while recording, via Android's built-in
  `SpeechRecognizer` (free, no account, no network) — single-speaker text,
  since stock Android has no diarization API. The app then splits it into
  the two-speaker dialogue view using a pause-based heuristic (a >2s silence
  flips the speaker), purely cosmetic — rename either speaker by tapping
  their name on the transcript screen.
- Local recordings library persisted to a JSON file in app storage
- Copy / share transcript as plain text

**Needs your credentials to activate, from Settings:**
- **Notion export** — real `api.notion.com` integration (create an
  integration at notion.so/my-integrations, share your target database
  with it, paste the token + database ID into Settings → Notion). Auto- or
  manual-send both work; the title property name is looked up automatically
  so it doesn't have to be called "Name".
- **ElevenLabs transcription** — paste your ElevenLabs API key (elevenlabs.io)
  into Settings and that's it: the audio is sent to `api.elevenlabs.io/v1/speech-to-text`
  (`scribe_v1`) and ElevenLabs does the actual transcription/diarization —
  nothing else about the app talks to any server. Leave the key blank and
  everything stays on-device only. This is also the only way **imported**
  audio files get transcribed, since Android's on-device recognizer only
  works on a live mic, not a finished file. The Settings toggles map to
  real Scribe parameters (`tag_audio_events`, `diarize`, `num_speakers`,
  `language_code`) plus a client-side filler-word strip for "no verbatim"
  and a best-effort SRT save next to the audio for "include subtitles".
  See [`TranscriptionService.kt`](app/src/main/java/com/nutka/app/data/TranscriptionService.kt).

**Fallback guarantee:** a recording/import is written to local storage and
shown in the app (status "Przetwarzanie") *before* any network call is even
attempted. If ElevenLabs is unset, fails, or returns nothing, live
recordings fall back to the free on-device transcript automatically; either
way the audio file and the list entry are never deleted — only their
status/text updates. A failed Notion send just reverts the recording to
"Do wysłania" so the transcript stays visible and retryable, it never
disappears.

## Structure

```
app/src/main/java/com/nutka/app/
  MainActivity.kt              entry point, splash + edge-to-edge
  NutkaViewModel.kt             all app state + logic (mirrors the mockup's Component)
  data/                          models, persistence, recording, transcription, Notion
  service/RecordingService.kt   foreground service for background recording
  ui/theme/                     colors/type/shapes lifted from the design system tokens
  ui/screens/                   Record, Recordings list, Transcript, Import, Settings
  ui/components/                waveform/pulse animations, tags, toggle rows, keyterms chip input
```

## Honesty notes

- I could not compile or run this here (no Android SDK/JDK in this
  environment) — I checked it by hand (balanced braces, resource
  cross-references, API signatures I'm confident about) but a first build
  in Android Studio may still surface something to fix.
- Simultaneous MediaRecorder + SpeechRecognizer mic access is best-effort;
  some OEM ROMs restrict it. If live captions don't appear on your device,
  the audio file still records fine — only the live-caption convenience is
  affected, and it doesn't block you from wiring up cloud transcription
  instead.
