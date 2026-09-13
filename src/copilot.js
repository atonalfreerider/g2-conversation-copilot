export const Mode = Object.freeze({ PLAYFUL: "playful", STRATEGIC: "strategic" });

export function lastWords(text, count = 20) {
  return text.trim().split(/\s+/u).filter(Boolean).slice(-count).join(" ");
}

export class ConversationCopilot {
  constructor({ contextWordCount = 20, mode = Mode.PLAYFUL } = {}) {
    this.state = {
      detectedLanguage: "EN", contextWordCount, mode, listening: false,
      tone: { focus: "stance", stance: 3, risk: 3 }, transcript: [],
      card: { detectedLanguage: "EN", englishContext: "Ready", replies: [] },
    };
  }
  start() { this.state.listening = true; return this.snapshot(); }
  stop() { this.state.listening = false; return this.snapshot(); }
  toggleMode() { this.state.mode = this.state.mode === Mode.PLAYFUL ? Mode.STRATEGIC : Mode.PLAYFUL; return this.snapshot(); }

  ingest({ speaker, language, text }, suggestionEngine) {
    if (!this.state.listening) return this.snapshot();
    this.state.transcript.push({ speaker, language, text });
    this.state.transcript = this.state.transcript.slice(-12);
    const foreign = this.state.detectedLanguage !== "EN";
    this.state.card = suggestionEngine({
      mode: this.state.mode,
      tone: { stance: this.state.tone.stance, risk: this.state.tone.risk },
      detectedLanguage: this.state.detectedLanguage, foreign,
      latestSpeaker: speaker, contextWordCount: this.state.contextWordCount,
      rollingText: lastWords(this.state.transcript.map(x => x.text).join(" "), this.state.contextWordCount),
      latestText: text,
      userWentOffScript: foreign && speaker === "user" && language === "English",
    });
    this.state.detectedLanguage = this.state.card.detectedLanguage;
    return this.snapshot();
  }

  ring(action) {
    const tone = this.state.tone;
    if (action === "press") tone.focus = tone.focus === "stance" ? "risk" : "stance";
    if (action === "swipe_down") tone[tone.focus] = Math.min(7, tone[tone.focus] + 1);
    if (action === "swipe_up") tone[tone.focus] = Math.max(0, tone[tone.focus] - 1);
    if (action === "double_press") this.stop();
    return this.snapshot();
  }

  displayLines() {
    const s = this.state;
    const foreign = s.detectedLanguage !== "EN";
    const status = `${s.detectedLanguage} ${s.tone.stance}/${s.tone.risk}`;
    const lines = [s.card.englishContext];
    s.card.replies.forEach((r, index) => {
      const branch = `• ${foreign ? r.phonetic : r.english}`;
      lines.push(index === 0 ? `${status} ${branch}` : branch);
    });
    const visible = lines.slice(0, 7);
    if (s.card.replies.length === 0) visible[0] = `${status} ${visible[0]}`;
    return visible;
  }
  snapshot() { return structuredClone(this.state); }
}

export function mockSuggestionEngine(req) {
  const detectedLanguage = detectLanguage(req.latestText, req.latestSpeaker === "user" ? req.detectedLanguage : "EN");
  const foreign = detectedLanguage !== "EN";
  if (!foreign) {
    const inquisitive = req.mode === Mode.PLAYFUL ? "What’s the fun version?" : "What matters most to you?";
    const declarative = req.mode === Mode.PLAYFUL ? "Okay, that has a story." : "That sounds important to you.";
    const lead = req.tone.stance <= 2 ? inquisitive : req.tone.stance >= 5 ? declarative : "Tell me more about that.";
    const edge = req.tone.risk >= 5 ? "Here’s the bold take." : req.tone.risk <= 2 ? "We can take this slowly." : "I’m with you.";
    const replies = [lead, edge, req.tone.stance <= 2 ? "And then what happened?" : "I see where you’re coming from."];
    return { detectedLanguage, englishContext: lastWords(req.latestText, req.contextWordCount), replies: replies.map(english => ({ english })) };
  }
  if (req.userWentOffScript) return {
    detectedLanguage, englishContext: `YOU SAID: ${lastWords(req.latestText, req.contextWordCount)}`,
    replies: [{ english: req.latestText, phonetic: "mock phonetic translation" }],
  };
  return {
    detectedLanguage, englishContext: `THEY SAID: ${mockEnglishTranslation(req.latestText, detectedLanguage)}`,
    replies: [
      { english: "Tell me more.", phonetic: "mock: tell me more" },
      { english: "That sounds wonderful.", phonetic: "mock: sounds wonderful" },
    ],
  };
}

export function detectLanguage(text, sessionLanguage = "EN") {
  if (/\p{Script=Cyrillic}/u.test(text)) return "RU";
  if (/\p{Script=Han}/u.test(text)) return "ZH";
  if (/[ąćęłńóśźż]/iu.test(text)) return "PL";
  return sessionLanguage;
}

function mockEnglishTranslation(text, language) {
  if (language === "PL") return "Hello, I am glad to meet you.";
  if (language === "RU") return "Hello, how are you today?";
  if (language === "ZH") return "Hello, it is nice to meet you.";
  return lastWords(text);
}
