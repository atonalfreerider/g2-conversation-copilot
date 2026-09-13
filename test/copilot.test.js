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
  assert.match(c.displayLines().join("\n"), /YOU SAID/);
  assert.match(c.displayLines().join("\n"), /mock phonetic translation/);
});

test("ring steers 0-7 axes and status prefixes the first branch", () => {
  const c = new ConversationCopilot(); c.start();
  c.ingest({ speaker: "other", language: "English", text: "I changed careers last year" }, mockSuggestionEngine);
  c.ring("swipe_up"); c.ring("press"); c.ring("swipe_down"); c.ring("swipe_down");
  assert.equal(c.state.tone.stance, 2); assert.equal(c.state.tone.risk, 5);
  assert.match(c.displayLines()[1], /^EN 2\/5 •/);
  c.ring("double_press"); assert.equal(c.state.listening, false);
});

test("language is automatic and foreign branches display phonetics only", () => {
  const c = new ConversationCopilot(); c.start();
  c.ingest({ speaker: "other", language: "unknown", text: "Привет, как дела сегодня?" }, mockSuggestionEngine);
  assert.equal(c.state.detectedLanguage, "RU");
  const display = c.displayLines().join("\n");
  assert.match(display, /THEY SAID: Hello/); assert.match(display, /mock: tell me more/);
  assert.doesNotMatch(display, /Tell me more\./); assert.match(c.displayLines()[1], /^RU 3\/3 •/);
});
