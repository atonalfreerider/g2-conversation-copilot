import test from "node:test";
import assert from "node:assert/strict";
import { ConversationCopilot, lastWords, mockSuggestionEngine } from "../src/copilot.js";

test("rolling transcript is capped to configured N words", () => {
  const text = Array.from({length: 25}, (_, i) => `w${i + 1}`).join(" ");
  assert.equal(lastWords(text).split(" ").length, 20);
  assert.equal(lastWords(text).startsWith("w6 "), true);
});

test("foreign off-script English becomes a phonetic branch", () => {
  const c = new ConversationCopilot(); c.start();
  c.ingest({ speaker: "other", language: "unknown", text: "Cześć, miło cię poznać" }, mockSuggestionEngine);
  c.ingest({ speaker: "user", language: "English", text: "I would love to visit Warsaw" }, mockSuggestionEngine);
  assert.doesNotMatch(c.displayLines().join("\n"), /YOU SAID/);
  assert.match(c.displayLines().join("\n"), /mock phonetic translation/);
});

test("ring scroll selects and press mutates or activates", () => {
  const c = new ConversationCopilot(); c.start();
  c.ingest({ speaker: "other", language: "English", text: "I changed careers last year" }, mockSuggestionEngine);
  c.ring("press");
  assert.equal(c.state.mode, "strategic");
  c.ring("swipe_down"); c.ring("press");
  assert.equal(c.state.tone.stance, "declarative");
  c.ring("swipe_down"); c.state.tone.risk=7; c.ring("press");
  assert.equal(c.state.tone.risk, 1);
  assert.match(c.displayLines()[1], /^EN S D \[1\] •/);
  c.ring("swipe_down"); c.ring("press"); assert.equal(c.state.spokenBranch,0);
  c.ring("double_press"); assert.equal(c.state.listening, false);
});

test("foreign branches display English above phonetics", () => {
  const c = new ConversationCopilot(); c.start();
  c.ingest({ speaker: "other", language: "unknown", text: "Привет, как дела сегодня?" }, mockSuggestionEngine);
  assert.equal(c.state.detectedLanguage, "RU");
  const display = c.displayLines().join("\n");
  assert.match(display, /^Hello/m); assert.match(display, /Tell me more\.\n  mock: tell me more/);
  assert.doesNotMatch(display, /THEY SAID/); assert.match(c.displayLines()[1], /^RU \[P\] I 4 •/);
});
