export const Mode = Object.freeze({ PLAYFUL: "playful", STRATEGIC: "strategic" });

export function lastWords(text, count = 20) {
  return text.trim().split(/\s+/u).filter(Boolean).slice(-count).join(" ");
}

export class ConversationCopilot {
  constructor({ contextWordCount = 20, mode = Mode.PLAYFUL } = {}) {
    this.state = {
      detectedLanguage: "EN", contextWordCount, mode, listening: false,
      selection: 0, transcript: [], viewport: 0,
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
    const incoming = suggestionEngine({
      mode: this.state.mode,
      detectedLanguage: this.state.detectedLanguage, foreign,
      latestSpeaker: speaker, contextWordCount: this.state.contextWordCount,
      rollingText: lastWords(this.state.transcript.map(x => x.text).join(" "), this.state.contextWordCount),
      latestText: text,
      userWentOffScript: foreign && speaker === "user" && language === "English",
    });
    this.state.detectedLanguage = incoming.detectedLanguage;
    if (incoming.pinned || (foreign && speaker === "user" && language === "English")) {
      this.state.card = {...incoming, replies: [incoming.replies[0], ...this.state.card.replies.filter(r => r.english !== incoming.replies[0].english)].slice(0, 8)};
      this.state.selection = 0; this.state.viewport = 0;
    } else if (!this.state.card.replies.length) this.state.card = incoming;
    else {
      const selected = this.state.selection;
      const replies = incoming.replies.slice(0, 8).map((r,i) => Math.abs(i-selected) <= 1 ? this.state.card.replies[i] ?? r : r);
      this.state.card = {...incoming, replies};
    }
    return this.snapshot();
  }

  ring(action) {
    if (action === "swipe_down" || action === "swipe_up") {
      const max = Math.min(8, this.state.card.replies.length) - 1;
      this.state.selection = Math.max(0, Math.min(max, this.state.selection + (action === "swipe_down" ? 1 : -1)));
      if (this.state.selection < this.state.viewport) this.state.viewport = this.state.selection;
      if (this.state.selection > this.state.viewport + 1) this.state.viewport = this.state.selection - 1;
    }
    if (action === "press") this.state.spokenBranch = this.state.selection;
    if (action === "double_press") this.stop();
    return this.snapshot();
  }

  displayLines() {
    const s = this.state;
    const foreign = s.detectedLanguage !== "EN";
    const lines = [s.card.englishContext];
    s.card.replies.slice(s.viewport, s.viewport + 2).forEach((r, localIndex) => {
      const index = s.viewport + localIndex;
      const branch = `${s.selection === index ? ">" : "•"} ${r.english}`;
      lines.push(branch);
      if (foreign && r.phonetic) lines.push(`  ${r.phonetic}`);
    });
    const visible = lines;
    return visible;
  }
  snapshot() { return structuredClone(this.state); }
}

export function mockSuggestionEngine(req) {
  const detectedLanguage = detectLanguage(req.latestText, req.latestSpeaker === "user" ? req.detectedLanguage : "EN");
  const foreign = detectedLanguage !== "EN";
  if (!foreign) {
    const replies = ["What happened next?","That makes sense.","What mattered most?","I see the tension there.","Would you do it again?","I think you wanted the change.","What are you afraid to admit?","I think you already know the answer."];
    return { detectedLanguage, englishContext: lastWords(req.latestText, req.contextWordCount), replies: replies.map(english => ({ english })) };
  }
  if (req.userWentOffScript) return {
    detectedLanguage, englishContext: lastWords(req.latestText, req.contextWordCount),
    pinned:true,replies: Array.from({length:8},(_,i)=>({ english: i ? `Option ${i+1}` : req.latestText, phonetic: i ? `mock option ${i+1}` : "mock phonetic translation" })),
  };
  return {
    detectedLanguage, englishContext: mockEnglishTranslation(req.latestText, detectedLanguage),
    replies: [
      { english: "Tell me more.", phonetic: "mock: tell me more" },
      { english: "That sounds wonderful.", phonetic: "mock: sounds wonderful" },
      { english: "What is your name?", phonetic: "mock: what is your name" },
      { english: "Where are you from?", phonetic: "mock: where are you from" },
      { english: "Please say that again.", phonetic: "mock: say it again" },
      { english: "It is nice to meet you.", phonetic: "mock: nice to meet you" },
      { english: "What brought you here?", phonetic: "mock: what brought you here" },
      { english: "You have an unforgettable smile.", phonetic: "mock: unforgettable smile" },
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
