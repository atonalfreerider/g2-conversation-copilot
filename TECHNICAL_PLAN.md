# Even G2 Conversation Copilot — feasibility and technical plan

Research checked 13 September 2026. Product behavior and SDKs can change; pin versions and repeat the hardware spike when the device arrives.

## Executive decision

Build the glasses experience as an **Even Hub plugin**, not as a replacement Bluetooth application. The official architecture runs a TypeScript/web plugin inside the Even Realities phone app; that app relays display, microphone, and R1 events over BLE. Add an optional native Android companion only for account/provider setup, encrypted credentials, diagnostics, and a persistent phone-microphone mode. This preserves the supported G2/R1 path and avoids reverse-engineered BLE.

The first releasable version should use the G2 microphone through the plugin and a small application backend. A native Pixel service is phase 2, only if testing proves that the official plugin cannot reliably survive the desired session length or needs phone-side audio preprocessing.

## Verified capability matrix

| Requirement | Stock G2 / Conversate | Official custom path | Verdict |
|---|---|---|---|
| Continuous conversation transcription | Yes, while the user explicitly starts Conversate | G2 four-mic, single 16 kHz PCM stream is exposed to plugins | Supported |
| Live translation | Yes; source can auto-detect and target is configured | Stream PCM to ASR/translation service and render text | Supported |
| On-glass AI cues / suggested answers | Conversate provides contextual cues and suggested answers | Provider-neutral suggestion engine | Stock is close; custom needed for exact behavior |
| Playful/flirty and strategic/trust modes | Not documented as configurable modes | Prompt policy + mode state | Custom |
| Exactly 1–3 replies in English | Not documented as controllable | Enforced JSON schema and renderer | Custom |
| Last 20 spoken words on top | Stock offers live transcript/translation, not this exact rolling layout | Local word-window state | Custom |
| English reply + target-language phonetics | Not documented | Translation + native script + pronunciation rendering | Custom |
| Detect the wearer going off-script | Stock transcribes the room but does not document this behavior | Speaker assignment + language ID + user correction control | Custom and probabilistic |
| Translate the wearer’s last English statement | Stock Conversate displays only the configured translation direction | Detect user English turn, translate it, replace reply card | Custom |
| R1 navigation | Conversate can be controlled with R1; double tap ends | SDK exposes the same press/double-press/swipe gestures as temples | Supported |
| OpenAI, Grok, Gemini selection | No documented provider selection | Backend adapters | Custom |

Evidence: the official [Even Hub overview](https://hub.evenrealities.com/docs/getting-started/first-app) says plugins are standard web apps using `@evenrealities/even_hub_sdk`, with QR/private testing and packaging. Official [Even device documentation](https://hub.evenrealities.com/docs/guides/display) lists the 576×288 monochrome display, one 16 kHz PCM microphone stream, no speaker/camera, and press/double-press/swipe input on G2 and R1. The official [Conversate support page](https://support.evenrealities.com/hc/en-us/articles/14273795154319-Conversate) documents AI cues, live transcription, summaries, input selection, real-time translation, and explicitly says on-glass translation is one direction only. The current [language support page](https://support.evenrealities.com/hc/en-us/articles/13755354799247-Language-Support) is the source of truth for language availability.

## Product interaction

### Glasses card: English

```text
EN · PLAY
THEY: I finally quit that job…
> What pushed you to do it?
  Bold move. How does it feel?
  What happens next?
```

No reply is selected or confirmed. The wearer simply speaks naturally; ASR threads what they actually said into the conversation. The ring controls the tone of suggestions generated from the next stable transcript. A contextual menu contains `Language`, `Playful`, `Trust`, `Reset tone`, `Pause`, and `Privacy` actions. The official contextual-menu API supports up to ten plugin actions and requires SDK 0.0.14+ / Even App 2.2.9+; see [Even contextual menus](https://hub.evenrealities.com/docs/build/contextual-menu).

### Glasses card: foreign language

```text
PL · PLAY
THEY: I moved here two years ago…
> Tell me more about that.
  POW-YETSH mee OH tym vee-EN-tsey
  Do you miss home?
  CHY ten-SKNEESH zah DOH-mem
```

The requested top line is the English translation of the rolling last 20 recognized words. Each response has three stored forms—English meaning, native-script translation, and English-friendly pronunciation—but the lens defaults to English + pronunciation to reduce clutter. A press temporarily reveals native script if its glyphs are supported.

Do not market phonetics as authoritative pronunciation. Generate transliteration deterministically when a language library exists, then ask the language model only to make it readable for an English speaker. Keep native script available for verification. The example “kak dela” means “how are things?”, not “hello”; prompts and tests must not learn mislabeled seed examples.

### Tone axes and controls

- **Stance:** `0` inquisitive ↔ `7` declarative.
- **Risk:** `0` safe ↔ `7` risky, with integer intervals between. “Risky” means socially bold or vulnerable, never unsafe, deceptive, coercive, illegal, or boundary-violating.
- Press: switch the active axis (`[S]` stance or `[R]` risk in the header).
- Swipe up: move stance toward inquisitive, or risk toward safe.
- Swipe down: move stance toward declarative, or risk toward risky.
- Double press: end session (matches the platform’s exit convention).
- Context menu: language, persona, reset tone, pause/resume, mic choice.
- Phone: full settings, provider choice, vocabulary corrections, transcript deletion.

Tone is session state, not a response selector. Every committed user utterance—including wording unrelated to any suggestion—is appended to the neutral conversation context. The current two-axis values are included in the next provider request; changing tone can also immediately regenerate the current card after a short debounce.

Language is backend-detected and session-smoothed; there is no manual language setting in the primary flow. The top lens zone always contains the English translation/transcription of the latest configurable N microphone words. The bottom zone follows immediately without a divider and contains English branches when English is detected, or pronunciation-only English-phonetic branches when another language is detected. The first branch is prefixed with compact status, such as `PL 2/5 • ...` (language, stance, risk).

## Runtime architecture

```text
G2 mic + R1
    ⇅ BLE (owned by Even app)
Even Hub plugin in Android WebView
    ├─ local VAD / 20-word buffer / card renderer
    └─ secure WebSocket → application backend
          ├─ streaming ASR + language ID
          ├─ turn segmentation + speaker assignment
          ├─ translation + phonetic pipeline
          ├─ policy/context orchestrator
          └─ LLM adapter: OpenAI | xAI | Gemini
```

The plugin should never contain durable provider keys. It authenticates to the application backend with a short-lived session token. The backend holds provider secrets in a secret manager, validates all model output, rate-limits sessions, and emits a single normalized `SuggestionCard`.

### Canonical event and result contracts

```json
{
  "type": "turn.final",
  "turnId": "t_104",
  "speaker": "user|other|unknown",
  "language": "en",
  "text": "...",
  "confidence": 0.91,
  "startedAtMs": 1234,
  "endedAtMs": 2780
}
```

```json
{
  "translation": "English rolling translation",
  "replies": [
    {"english":"Tell me more.","native":"Powiedz mi więcej.","phonetic":"POW-yetsh mee VYEN-tsey"}
  ]
}
```

The provider-neutral JSON schema is implemented in `src/provider-contract.js`.

## Audio, speakers, and latency

1. Plugin receives 16 kHz PCM and immediately runs energy/WebRTC VAD.
2. Send 20–40 ms frames over a session WebSocket; retain a two-second local reconnect buffer only in memory.
3. Streaming ASR emits partial text for the translation line. Commit a turn after roughly 450–700 ms of silence, tuned through field tests.
4. Assign `user` vs `other` using an enrolled wearer voice embedding plus microphone spatial features if the raw stream preserves them. The documented SDK exposes a **single** stream, so spatial separation must not be assumed.
5. If confidence is low, label `unknown`; do not trigger off-script translation. A press can mark the last turn “mine,” providing both correction and future adaptation.
6. Start suggestion generation on a stable partial transcript, cancel it if the speaker continues, and replace the card only on a validated final object.

Target perceived latency: partial translation under 500 ms, final translation under 900 ms, first useful suggestion under 1.5 s on good mobile data. Treat these as product targets, not guarantees.

Speaker attribution is the hardest technical risk. A four-mic array does not mean four independently accessible channels. Validate with wearer/non-wearer pairs in quiet rooms, cars, cafés, and overlapping speech before promising automatic off-script behavior.

## Backend adapters

All adapters implement:

```text
suggest(context, JSON schema, cancellation token) -> SuggestionCard
```

- **OpenAI:** use the Responses API with Structured Outputs, `store: false`, a short output budget, and explicit cancellation. Official [OpenAI Responses documentation](https://developers.openai.com/api/reference/cli/resources/responses/methods/create) documents text/JSON output, instructions, streaming, and multi-turn state. Keep the model ID configurable rather than baking in “ChatGPT,” which is a product name rather than an API model.
- **xAI/Grok:** use `/v1/responses` behind the same internal adapter. Current official [xAI Responses reference](https://docs.x.ai/developers/rest-api-reference/inference/responses) documents the endpoint and OpenAI-client-compatible base URL. Do not assume every OpenAI parameter behaves identically; adapter tests own those differences.
- **Gemini:** use the current Interactions or streaming content API, with structured output. Official [Gemini API reference](https://ai.google.dev/api) describes unary, streaming, and bidirectional APIs, while [structured-output documentation](https://ai.google.dev/gemini-api/docs/structured-output) documents its JSON Schema subset and streaming partial JSON.

Provider switching starts a fresh provider-side conversation. The app owns a compact neutral context (last turns + rolling summary), so no provider-specific response ID leaks into the domain layer. Use a circuit breaker and optionally fail over only after the user opts in, because failover sends conversation data to another company.

ASR should also be an adapter rather than coupled to the reply model. Benchmark a low-latency cloud recognizer against on-device transcription for supported languages. Translation can use a dedicated translation service for literal fidelity; the LLM’s job is reply selection and readable coaching.

## Android / Pixel 10 companion

The official plugin already executes on the Pixel inside the Even app. A separate native Android application is justified only for capabilities outside that sandbox:

- OAuth/provider setup and Android Keystore-backed session credentials.
- Foreground notification, explicit start/stop, privacy indicator, usage/cost view.
- Optional phone-microphone capture and on-device VAD/ASR.
- Diagnostics and safe transcript export/delete.

For native continuous microphone capture, start a user-initiated `microphone` foreground service while the activity is visible, request `RECORD_AUDIO`, declare `FOREGROUND_SERVICE_MICROPHONE`, and display an ongoing notification. Android’s current [foreground-service type guidance](https://developer.android.com/develop/background-work/services/fgs/service-types) documents these requirements and background-start restrictions. Do not implement an always-on boot listener.

Communication between the plugin and optional companion should use the application backend, not an undocumented local WebView bridge. If testing later finds a supported deep-link or loopback mechanism, add it behind an interface.

## Safety, consent, and privacy requirements

- Session capture is never ambient by default: require an explicit start and show `LISTENING` plus Android’s ongoing notification where applicable.
- Provide a visible/verbal consent workflow appropriate to local law; recording/transcription consent rules differ by jurisdiction.
- Default to ephemeral processing: audio buffers in memory only; no raw audio storage; `store: false` where the provider supports it.
- Transcript history is opt-in, encrypted at rest, and deletable by session and all-at-once.
- The “strategic” mode is framed as empathetic curiosity. It must not coach deception, coercion, pressure, exploiting vulnerabilities, or covert extraction of sensitive facts.
- “Flirty” stays respectful and non-explicit, backs off on disinterest, and is disabled for ambiguous age contexts.
- Show `OFFLINE`, `UNSURE`, or `LISTENING` rather than fabricating a translation when confidence is low.

## Delivery plan

### Phase 0 — hardware spike (2–4 days)

- Pair current G2/R1, enable developer mode, pin the current SDK/tooling.
- Confirm actual PCM buffer shape, microphone permission prompts, max uninterrupted duration, phone-lock behavior, R1 event mapping, and text update flicker.
- Establish whether R1 and temple events are distinguishable; the product does not require them to be.
- Measure glyph coverage for every target language and effective characters per line on the device.

Exit: a 30-minute PCM loop with R1-controlled text updates and no unrecovered disconnect.

### Phase 1 — vertical slice (1–2 weeks)

- Even Hub plugin, session backend, one streaming ASR, OpenAI adapter, English + one foreign language.
- Two modes, rolling 20-word translation, 1–3/1–2 reply limits, phonetics, manual “last turn was mine.”
- No transcript retention; on-device/simulator telemetry contains timings but not conversation text.

Exit: ten scripted conversations meet layout and functional tests; median suggestion latency is measured, not guessed.

### Phase 2 — reliability and providers (2–3 weeks)

- Add xAI and Gemini adapters with contract tests.
- Add speaker enrollment/attribution, cancellation/debounce, reconnect buffering, offline states, safety filters, cost controls.
- Optional Android native companion if phase 0 demonstrates a real need.

### Phase 3 — beta and compliance (2–4 weeks)

- Private then beta package, privacy policy/data map, consent onboarding, threat model, accessibility and multilingual native-speaker review.
- Field test on noisy/overlapping speech and at least five accents per launch language.
- Submit only after Even Hub reviewer parity tests pass.

## Acceptance tests

- English final turn renders 1–3 replies; foreign final turn renders 1–2.
- Translation header is derived from no more than the latest 20 words.
- Every foreign reply contains English, native, and phonetic forms; the display never exceeds seven lines.
- User-English turn in foreign mode replaces the card with a target-language translation when speaker confidence passes threshold.
- Unknown speaker never silently triggers the wearer path.
- Press changes the active tone axis; swipes clamp each axis to integer values 0–7; double press stops capture.
- No reply-selection state exists, and the next user utterance threads correctly whether or not it resembles a suggestion.
- Late provider responses cannot overwrite a newer turn (monotonic `turnId`).
- Invalid/oversized JSON is rejected and replaced with a safe status card.
- Provider keys never appear in plugin assets, logs, or network responses.
- Ending a session closes audio/network streams and clears ephemeral text/audio buffers.

## Prototype scope and next build step

The included browser prototype exercises the interaction core without dependencies or credentials. Run `npm test` and `npm run demo`. After the hardware spike, replace `mockSuggestionEngine` with the WebSocket client, implement `GlassesPort` using the pinned Even Hub SDK, and package it as an `.ehpk`. The state machine and backend contract stay unchanged.
