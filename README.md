# G2 Conversation Copilot prototype

This is a dependency-free interaction prototype for an Even G2 conversation copilot. It demonstrates the state machine, compact glasses rendering, three-axis R1 tone steering, rolling 20-word context, mode switching, and off-script English handling without sending audio or text to a real provider.

The ring changes future suggestions rather than selecting a scripted response:

- Press cycles the active axis: persona → stance → risk.
- On persona, either scroll direction toggles `P` playful / `S` strategic.
- On stance, either scroll direction toggles `I` inquisitive / `D` declarative.
- On risk, scroll adjusts `1` safest through `7` riskiest.
- Double press stops the session.

The first conversation branch is prefixed with detected language and compact settings, such as `PL P I 2 • ...`. Brackets show the currently active ring axis: `PL [P] I 2`, `PL P [I] 2`, or `PL P I [2]`.

Language is detected by the backend. The top display area is always the English rendering of the last N microphone words (20 by default). The lower area shows English branches for English speech, or only English-readable phonetic branches for foreign speech.

## API instruction examples

Yes—the current settings are placed at the very beginning of every provider request, before conversation context. The normalized instruction header looks like:

```text
CONTROL VARIABLES: language=AUTO; persona=P; stance=I; risk=2/7.
```

Examples:

```text
CONTROL VARIABLES: language=AUTO; persona=P; stance=I; risk=1/7.
```

Generate playful, question-led, socially safe branches—for example, light curiosity that makes it easy for the other person to continue.

```text
CONTROL VARIABLES: language=AUTO; persona=S; stance=I; risk=4/7.
```

Generate strategic, trust-building questions with moderate directness, without manipulation or pressure.

```text
CONTROL VARIABLES: language=AUTO; persona=P; stance=D; risk=7/7.
```

Generate bold, playful statements rather than questions. “7” permits social daring and vulnerability, but never deception, coercion, illegality, or boundary violations.

## Run

```bash
npm test
npm run demo
```

Then open `http://localhost:4173`.

## Android settings app

The native app under `app/` configures OpenAI, xAI/Grok, cloud Gemini, and Gemini Nano independently. API keys are encrypted using a non-exportable Android Keystore AES-GCM key. Cloud connections have an editable model and endpoint plus a connection test. **Gemini Nano · on-device** uses the Pixel's shared AICore model through ML Kit Prompt API and requires no developer API key. The build uses Android Gradle Plugin 8.9.1 so it can use the installed Build Tools 35.0.0 without requesting Build Tools 36.

Build and deploy from a machine with Android SDK Platform 35, Build Tools 35, JDK 17+, and Gradle 8.11.1 available:

```bash
gradle :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.g2copilot.settings/.MainActivity
```

Or run `./deploy-android.sh` to build, verify that the Pixel authorized USB debugging, install, and open the app.

The APK intentionally does not contain provider keys. Enter them on the phone after installation.

### Pixel glasses simulator

Tap **Open glasses simulator** in the Android app to test without G2 hardware. It includes a lens-sized preview, live Pixel microphone transcription with partial updates, typed input fallback, a speech-language selector, and on-screen R1 scroll/press controls. Provider updates are event-driven: a snapshot is sent after speech briefly settles or Android emits a final recognition result. Requests include the complete rolling thread and current P/S, I/D, and risk controls.

The backend can be switched from either the main settings screen or the selector inside the simulator. Selecting a backend makes it active immediately. The Gemini choices are deliberately separate: **Gemini Nano · on-device** is private, offline-capable, and keyless; **Google Gemini** is the cloud API and still requires a key.

Choose **Polish**, **Russian**, or **Chinese** before speaking that language. This passes the corresponding locale to Android speech recognition (`pl-PL`, `ru-RU`, or `zh-CN`) and prevents Google Speech from forcing foreign speech into English homonyms. **Auto** uses the phone's default recognition locale and asks the LLM to detect language after transcription, so it cannot recover foreign words that the speech recognizer has already mistranscribed.

The lens reserves three fixed-height regions: English context at the top, then branch one and branch two. Translation mode shows only the English translation in the top region—there is no “they said” label or placeholder. Text wraps within its region and automatically scales down instead of being truncated, while fixed region heights prevent other content from jumping. Partial microphone results continue accumulating off-screen and trigger provider snapshots, but the lens commits only complete response objects, so suggestions change as stable chunks rather than token-by-token. xAI requests use SSE streaming with a longer read timeout; streamed deltas are assembled off-screen and atomically committed only after valid JSON is complete.

A live provider log appears below the simulator. It shows outbound transcript snapshots and control settings, incoming xAI SSE chunks, completed parsed responses, and network or parsing errors. API keys and authorization headers are never written to the log.

Each outbound snapshot has a generation number. When newer microphone text arrives, the previous HTTP connection is actively disconnected and its result discarded, its branches are removed, and only the newest generation may update the lens. xAI requests have finite connection and read deadlines, so a silent stream cannot wait forever. For English speech, the top region is the exact rolling microphone word window; the provider cannot replace it with a paraphrase. For foreign speech, the newest successful provider generation supplies the English translation.

On Ubuntu, the complete host prerequisites can be installed with:

```bash
sudo apt install openjdk-21-jdk-headless google-android-platform-tools-installer google-android-platform-35-installer google-android-build-tools-35.0.0-installer
```

## What is real vs mocked

- Real: conversation state, rendering policy, word-windowing, ring actions, provider-neutral request/response contract, privacy state.
- Mocked in the browser: speech recognition, speaker identification, translation, phonetics, and LLM network calls. The Android simulator uses the Pixel's real speech-recognition service and the configured provider for translation, phonetics, and branches.
- Hardware adapter: `src/even-adapter.ts` documents the narrow boundary to implement with `@evenrealities/even_hub_sdk` after testing on the shipped G2/R1.

See [TECHNICAL_PLAN.md](./TECHNICAL_PLAN.md) for researched capabilities, architecture, rollout, and acceptance tests.
