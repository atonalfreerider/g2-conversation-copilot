# G2 Conversation Copilot prototype

This is a dependency-free interaction prototype for an Even G2 conversation copilot. It demonstrates the state machine, compact glasses rendering, three-axis R1 tone steering, rolling 20-word context, mode switching, and off-script English handling without sending audio or text to a real provider.

The ring changes future suggestions rather than selecting a scripted response:

- Press cycles the active axis: persona → stance → risk.
- Conversation style supports `P` playful, `S` strategic/trust-building, and `C` argumentative/contrarian. Contrarian branches challenge assumptions and surface contradictions while remaining evidence-seeking and non-hostile.
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

Latency-first transcription uses `gpt-live-transcribe` with its `minimal` delay setting in an ephemeral OpenAI Realtime transcription session. The EvenHub surface streams G2's raw mono 16-bit/16-kHz PCM after resampling to 24 kHz and updates the lens from transcript deltas while speech is still arriving. The long-lived OpenAI key never enters the glasses page; the phone bridge exchanges it for a short-lived client secret. If the key or network is unavailable, the same audio automatically falls back to Pixel's offline Android speech recognizer. For non-English sessions, recognized source text is translated to English by ML Kit's downloaded on-device Translation model before the top lens container is updated. The selected branch provider (OpenAI, xAI, Gemini, or Nano) still receives text only.

Build and deploy from a machine with Android SDK Platform 35, Build Tools 35, JDK 17+, and Gradle 8.11.1 available:

```bash
gradle :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.g2copilot.settings/.MainActivity
```

Or run `./deploy-android.sh` to build, verify that the Pixel authorized USB debugging, install, and open the app.

The APK intentionally does not contain provider keys. Enter them on the phone after installation.

The **Speaker biography** field stores the user's self-description locally on the phone. Every provider prompt includes it as point-of-view context so branches sound like something that user might plausibly say. It is never treated as permission to invent additional biographical facts.

### G2 + phone companion

The Android app is now only the phone companion/settings surface; the separate phone glasses simulator is no longer registered. Language and playful/strategic style are controlled exclusively on the phone and are never printed on the glasses. American English is the default and there is no Auto mode. R1 scroll moves through eight active responses; R1 press regenerates all eight using the complete current conversation context. The lens shows two queue entries at a time. During automatic contextual refreshes, the selected response and its immediate neighbors are locked while the five unprotected slots can be replaced. An explicit R1 refresh replaces all eight. English branches use at most two lines.

Foreign branches always carry three separate values: English meaning, native-language writing, and an American-English-readable pronunciation of that native phrase. The phone toggle **Show native characters instead of phonetic English** defaults off. Thus French displays `bohn-ZHOOR` by default and `bonjour` when enabled. A phone-selected foreign language is authoritative even if a provider incorrectly reports English; incomplete foreign output is rejected rather than displayed as English phonetics.

The biography supplies the primary speaker name when it contains a phrase such as “My name is John.” Provider prompts carry that saved point of view alongside the recent conversation.

Provider prompts carry the speaker biography, compressed recent named turns, phone-selected `language`, and `persona=P|S|C`. They require exactly eight structured branches in this order: inquisitive risk 1, declarative risk 1, inquisitive risk 2, declarative risk 2, inquisitive risk 3, declarative risk 3, inquisitive risk 4, declarative risk 4. Risk 4 is the deliberately extreme option. These classifications are not shown on the glasses.

The G2 branch region is packed dynamically. Two short branches share a row in independent left and right text containers; a longer branch receives a full-width row. This uses the full 576×288 display while keeping each response independently selectable. Because the G2 font is proportional and has no right-alignment control, the right-hand response is positioned in a separate half-width container instead of being aligned with fragile space padding. The SDK permits eight text containers, so one transcript container plus up to seven visible branch containers are used; scrolling brings the eighth queued branch into view.

```text
CONTROL VARIABLES: language=PL; persona=P.
SPEAKER BIOGRAPHY: My name is John. I am a photographer from Chicago.
Conversation transcript: Speakers: John · Katy. Recent turns: Katy: I love Warsaw...
```

The backend can be switched from either the main phone settings screen or the EvenHub surface. Selecting a backend makes it active immediately. The Gemini choices are deliberately separate: **Gemini Nano · on-device** is private, offline-capable, and keyless; **Google Gemini** is the cloud API and still requires a key.

The offline fallback uses Android's installed on-device `SpeechRecognizer` with the G2 PCM stream supplied through `EXTRA_AUDIO_SOURCE`. It continues inside the companion foreground service while the Even Realities app stays visible, avoiding background WebView throttling. Translation remains on-device through ML Kit. Only Gemini Nano/AICore branch generation requires the companion UI to be foreground; tap **Bring Gemini Nano to foreground** when that provider is selected.

### Prototype hot reload without repeated QR scans

Run `npm run dev:glasses`, scan the prototype URL once in EvenHub, and leave that development session open. Vite and EvenHub hot reload subsequent code edits automatically; a new QR scan is not required for each edit. The public/private `.ehpk` release flow remains the route for a version that starts without the Ubuntu development server.

The default xAI model is `grok-4.20-0309-non-reasoning`, selected for conversation-copilot latency. Older saved defaults equal to `grok-4.6` are migrated automatically; that reasoning model can spend hundreds of hidden reasoning tokens before emitting any branch text. A manually configured alternate xAI model is preserved, and `grok-4.6` requests are forced to low reasoning effort.

Choose the language on the phone before speaking. This passes its locale to Android speech recognition and prevents Google Speech from forcing foreign speech into English homonyms. There is deliberately no automatic language mode.

The language menu offers only American English (`en-US`) for English, plus Spanish, French, German, Greek, Polish, Russian, Ukrainian, and Chinese. Greek (`el-GR`) and Ukrainian (`uk-UA`) use the same live English-translation pipeline and are also supported by the Pixel's ML Kit offline translation fallback. Other English regional variants are deliberately not offered.

After every completed utterance—and earlier when a stable partial reaches sentence punctuation—the app requests four anticipatory candidates. Each candidate carries a 0–100 provider relevance vote based on immediate fit and usefulness across the next one or two predictable turns. Existing scores decay as context advances; duplicates are merged; the lowest-scoring unpinned entries are replaced in place. R1 focus adds a small user-interest vote. If speech approximately matches any branch's English, native, or phonetic line, that exact branch is pinned through the current and next ranking cycle so it cannot disappear while being spoken. R1 press remains the explicit full eight-branch regeneration.

The lens reserves three fixed-height regions: English context at the top, then branch one and branch two. Translation mode shows only the English translation in the top region—there is no “they said” label or placeholder. Each foreign-language branch always shows its English meaning on one line and stupidly simple phonetic pronunciation directly beneath it. Stressed syllables are ALL CAPS (`TEEN DOE Bray`, `BWAY-nohs DEE-ahs`, `bohn-ZHOOR`, `GOO-ten tahk`). The contract rejects missing fields and embedded newlines. The G2 SDK exposes one fixed firmware font and no font-size control, so prompts target 36 characters per field; complete over-target responses are retained and flagged rather than disappearing. Partial microphone results continue accumulating off-screen and trigger provider snapshots, but the lens commits only complete response objects, so suggestions change as stable chunks rather than token-by-token.

A live provider log appears on the companion surface. It shows outbound transcript snapshots and control settings, incoming xAI SSE chunks, completed parsed responses, and network or parsing errors. API keys and authorization headers are never written to the log.

The provider-status row distinguishes connection setup, request upload, waiting for HTTP, active SSE streaming, completion, supersession, errors, and timeout. It also shows elapsed seconds while work is active. Every provider operation has a 30-second whole-request deadline, including xAI streams that might otherwise keep a socket alive indefinitely.

Each outbound snapshot has a generation number. When newer microphone text arrives, the previous HTTP connection is actively disconnected and its result discarded, its branches are removed, and only the newest generation may update the lens. xAI requests have finite connection and read deadlines, so a silent stream cannot wait forever. For English speech, the top region is the exact rolling microphone word window; the provider cannot replace it with a paraphrase. For foreign speech, the newest successful provider generation supplies the English translation.

## Tests

`npm test` runs deterministic state-machine, provider-contract, risk, biography, clean-translation, and phonetic-policy tests. Android JVM tests under `app/src/test` verify the native prompt and output sanitizer, including `Dzień dobry` → `Teen Doe Bray`.

Live smoke tests read keys directly from the supplied files and never copy them into the repository or print them:

```bash
npm run test:live:openai
npm run test:live:xai
npm run test:soak:openai
npm run test:soak:xai
gradle :app:testDebugUnitTest
```

The soak commands grow a conversation over ten consecutive provider calls and fail if any turn stops returning branches. Production keeps the previous valid branches visible while the next generation runs. A provider missing either bilingual field is rejected; a complete sentence that exceeds the conservative 36-character G2 target is preserved and logged as a warning instead of blanking the entire card.

On Ubuntu, the complete host prerequisites can be installed with:

```bash
sudo apt install openjdk-21-jdk-headless google-android-platform-tools-installer google-android-platform-35-installer google-android-build-tools-35.0.0-installer
```

## What is real vs mocked

- Real: conversation state, rendering policy, word-windowing, ring actions, provider-neutral request/response contract, privacy state.
- Browser-only demo code remains useful for deterministic tests; the shipped workflow is the EvenHub G2 surface plus the Android phone companion.
- Hardware adapter: `src/even-adapter.ts` documents the narrow boundary to implement with `@evenrealities/even_hub_sdk` after testing on the shipped G2/R1.

See [TECHNICAL_PLAN.md](./TECHNICAL_PLAN.md) for researched capabilities, architecture, rollout, and acceptance tests.
