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

The native app under `app/` configures OpenAI, xAI/Grok, and Gemini independently. API keys are encrypted using a non-exportable Android Keystore AES-GCM key. Each connection has an editable model and endpoint plus an on-device connection test. The build uses Android Gradle Plugin 8.9.1 so it can use the installed Build Tools 35.0.0 without requesting Build Tools 36.

Build and deploy from a machine with Android SDK Platform 35, Build Tools 35, JDK 17+, and Gradle 8.11.1 available:

```bash
gradle :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.g2copilot.settings/.MainActivity
```

Or run `./deploy-android.sh` to build, verify that the Pixel authorized USB debugging, install, and open the app.

The APK intentionally does not contain provider keys. Enter them on the phone after installation.

On Ubuntu, the complete host prerequisites can be installed with:

```bash
sudo apt install openjdk-21-jdk-headless google-android-platform-tools-installer google-android-platform-35-installer google-android-build-tools-35.0.0-installer
```

## What is real vs mocked

- Real: conversation state, rendering policy, word-windowing, ring actions, provider-neutral request/response contract, privacy state.
- Mocked: speech recognition, speaker identification, translation, phonetics, and LLM network calls.
- Hardware adapter: `src/even-adapter.ts` documents the narrow boundary to implement with `@evenrealities/even_hub_sdk` after testing on the shipped G2/R1.

See [TECHNICAL_PLAN.md](./TECHNICAL_PLAN.md) for researched capabilities, architecture, rollout, and acceptance tests.
