# G2 Conversation Copilot prototype

This is a dependency-free interaction prototype for an Even G2 conversation copilot. It demonstrates the state machine, compact glasses rendering, two-axis R1 tone steering, rolling 20-word context, mode switching, and off-script English handling without sending audio or text to a real provider.

The ring changes future suggestions rather than selecting a scripted response:

- Press switches the active axis: stance or risk.
- Swipe up moves toward inquisitive or safe.
- Swipe down moves toward declarative or risky.
- Double press stops the session.

Each axis ranges from 0–7: stance is `0 = inquisitive`, `7 = declarative`; risk is `0 = safe`, `7 = risky`. The first conversation branch is prefixed with detected-language and tone status, such as `PL 2/5 • ...`.

Language is detected by the backend. The top display area is always the English rendering of the last N microphone words (20 by default). The lower area shows English branches for English speech, or only English-readable phonetic branches for foreign speech.

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
