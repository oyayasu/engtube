# EngTube (Android)

A native Android app for collecting and studying English phrases encountered on YouTube, with **British English as the default reference accent**.

## Features implemented

- Android share target for YouTube links, plus manual URL input.
- In-app YouTube IFrame player with study-time A–B replay (when the video allows embedding); a link always opens the original YouTube app/page.
- Capture phrases manually or from user-provided **SRT/WebVTT/plain-text** transcripts. Timed subtitles preserve their real timestamps; plain-text phrases are explicitly unaligned.
- Editable quote, translation, note, collection, video timestamps, and independent imported-audio timestamps.
- Offline Room database, searchable phrase library, collection and learning-status filters, edit and delete.
- UK/US accent preference, optional IPA lookup by word from the public Free Dictionary API, with honest "unavailable" fallback and manual IPA editing. IPA is dictionary-based, **not** phonetic transcription of the speaker.
- Native Android TextToSpeech set to en-GB/en-US when a language voice is available.
- Clip-range playback with independent A–B timestamps for **user-imported local audio**; a YouTube source remains a link/player rather than an illegally downloaded audio file.
- Optional draggable floating quick-capture bubble with explicit overlay permission and foreground notification.
- CI builds a debug APK as a GitHub Actions artifact.

## What is not automatic

YouTube does not expose arbitrary videos' caption files and audio recordings to third-party Android apps as a guaranteed public feature. EngTube **does not** scrape captions, bypass content restrictions, rip third-party audio, or pretend to extract a clip when capture is blocked. Users can paste/import transcripts they have rights to use, manually define a phrase, import audio they own, or jump to the moment in the original video.

MediaProjection/AudioPlaybackCapture is not used here: whether another application permits its audio to be recorded is outside this app's control.

## Install / build

Open the repository in Android Studio, use JDK 17, Android SDK platform 36, and sync Gradle. Run the **app** configuration on Android 8.0+.

Alternatively, GitHub Actions > **Android APK** > Run workflow (or push to main), then download the \`engtube-debug-apk\` artifact. The APK is for development/testing and is not signed for Play Store distribution.

For a local Gradle installation:

\`\`\`sh
./gradlew :app:assembleDebug
\`\`\`

## How to use

1. Open a YouTube video and tap **Share > EngTube** (or paste its link under Capture).
2. Watch using the embedded player when supported or open in YouTube.
3. Tap **Use current time** in the embedded player, or enter a start/end time manually.
4. Type a sentence, or paste SRT/WebVTT/plain transcript lines and select one.
5. Choose UK or US IPA; **Look up IPA** requests dictionary phonetics; edit the text if needed.
6. Save. Search the library, assign a collection, revisit the original timestamp, practice with TTS, and review.
7. Optional: import an audio recording you own through the Android document picker for A–B playback.

## Permissions and privacy

- **INTERNET**: embedded YouTube video and opt-in dictionary lookups.
- **SYSTEM_ALERT_WINDOW**: only when the user turns on the bubble; the bubble does not read YouTube screens or audio.
- **POST_NOTIFICATIONS / FOREGROUND_SERVICE_SPECIAL_USE**: persistent notification for active optional overlay.
- **READ_MEDIA_AUDIO** is **not** requested: user audio is selected via Android Storage Access Framework.
- All phrases remain on device in a Room database. No account, analytics SDK, or background speech capture.
- The dictionary lookup sends the **individual words requested** to \`api.dictionaryapi.dev\` (public third-party service).

## Project structure

- \`app/src/main/java/com/oyayasu/engtube/MainActivity.kt\`: Compose app and UI workflow
- \`data/PhraseStore.kt\`: Room database, entity, DAO
- \`data/SourceTools.kt\`: URL validation and transcript parsing
- \`data/Pronunciation.kt\`: dictionary IPA, TTS, audio segment player
- \`OverlayService.kt\`: optional floating quick-capture button

## Technical caveats

- The embedded player must show original YouTube controls and branding, and embedding can be disallowed by a video.
- Source timestamps are not inferred from untimed plain-text subtitles.
- The audio imported through the document picker is a separately authorized user file, not a YouTube download.
- Transcription/automatic source audio extraction is a future opt-in integration needing a legal source and device testing, not a shipped guarantee.
