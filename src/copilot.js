export const Mode = Object.freeze({ PLAYFUL: "playful", STRATEGIC: "strategic" });

export function lastWords(text, count = 20) {
  return text.trim().split(/\s+/u).filter(Boolean).slice(-count).join(" ");
}

export class ConversationCopilot {
  constructor({ contextWordCount = 20, mode = Mode.PLAYFUL } = {}) {
    this.state = {
      detectedLanguage: "EN", contextWordCount, mode, listening: false,
      tone: { focus: 0, stance: "inquisitive", risk: 4 }, transcript: [], viewport: 0,
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
      tone: { persona: this.state.mode, stance: this.state.tone.stance, risk: this.state.tone.risk },
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
    if (action === "swipe_down" || action === "swipe_up") {
      const max = 2 + Math.min(6, this.state.card.replies.length);
      tone.focus = Math.max(0, Math.min(max, tone.focus + (action === "swipe_down" ? 1 : -1)));
      if (tone.focus >= 3) this.state.viewport = Math.max(0, Math.min(4, tone.focus - 3));
    }
    if (action === "press") {
      if (tone.focus === 0) this.toggleMode();
      if (tone.focus === 1) tone.stance = tone.stance === "inquisitive" ? "declarative" : "inquisitive";
      if (tone.focus === 2) tone.risk = tone.risk === 7 ? 1 : tone.risk + 1;
      if (tone.focus >= 3) this.state.spokenBranch = tone.focus - 3;
    }
    if (action === "double_press") this.stop();
    return this.snapshot();
  }

  displayLines() {
    const s = this.state;
    const foreign = s.detectedLanguage !== "EN";
    const tokens = {
      persona: s.mode === Mode.PLAYFUL ? "P" : "S",
      stance: s.tone.stance === "inquisitive" ? "I" : "D",
      risk: String(s.tone.risk),
    };
    const axis = ["persona", "stance", "risk"][s.tone.focus]; if (axis) tokens[axis] = `[${tokens[axis]}]`;
    const status = `${s.detectedLanguage} ${tokens.persona} ${tokens.stance} ${tokens.risk}`;
    const lines = [s.card.englishContext];
    s.card.replies.slice(s.viewport, s.viewport + 2).forEach((r, localIndex) => {
      const index = s.viewport + localIndex;
      const branch = `${s.tone.focus === index + 3 ? ">" : "•"} ${r.english}`;
      lines.push(index === 0 ? `${status} ${branch}` : branch);
      if (foreign && r.phonetic) lines.push(`  ${r.phonetic}`);
    });
    const visible = lines;
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
    const lead = req.tone.stance === "inquisitive" ? inquisitive : declarative;
    const edge = req.tone.risk >= 5 ? "Here’s the bold take." : req.tone.risk <= 2 ? "We can take this slowly." : "I’m with you.";
    const replies = [lead, edge, req.tone.stance === "inquisitive" ? "And then what happened?" : "I see where you’re coming from.","Tell me the part you remember most.","That changes how I see it.","What would you do differently now?"];
    return { detectedLanguage, englishContext: lastWords(req.latestText, req.contextWordCount), replies: replies.map(english => ({ english })) };
  }
  if (req.userWentOffScript) return {
    detectedLanguage, englishContext: lastWords(req.latestText, req.contextWordCount),
    replies: Array.from({length:6},(_,i)=>({ english: i ? `Option ${i+1}` : req.latestText, phonetic: i ? `mock option ${i+1}` : "mock phonetic translation" })),
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
